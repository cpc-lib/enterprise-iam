package com.enterprise.iam.idempotency;

import com.enterprise.iam.common.security.delegation.TrustedRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 命令请求幂等过滤器（SPEC 36 §22 / SPEC 47 §9.1 D-4）。
 *
 * <pre>
 * 缺 Idempotency-Key            → 400 IAM_IDEMPOTENCY_KEY_REQUIRED
 * key 长度不合法                → 400 IAM_IDEMPOTENCY_KEY_INVALID
 * 缺可信租户上下文              → 400 IAM_TENANT_CONTEXT_REQUIRED
 * 同 key 不同 body              → 409 IAM_IDEMPOTENCY_REPLAY_CONFLICT
 * 同 key 同 body 且在途/丢失    → 409 IAM_IDEMPOTENCY_IN_FLIGHT
 * 同 key 同 body 且已成功       → 原样重放，不进入控制器
 * 下游失败（非 2xx）            → 释放占用，允许同 key 重试
 * </pre>
 */
public final class IdempotentRequestFilter extends OncePerRequestFilter {

    private final IdempotencyProperties properties;
    private final ReplayIdempotencyStore store;
    private final Clock clock;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public IdempotentRequestFilter(IdempotencyProperties properties,
                                   ReplayIdempotencyStore store,
                                   Clock clock) {
        this.properties = properties;
        this.store = store;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String method = request.getMethod();
        if (properties.getMethods().stream().noneMatch(m -> m.equalsIgnoreCase(method))) {
            return true;
        }
        String path = request.getRequestURI();
        return properties.getPaths().stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader(properties.getHeaderName());
        if (key == null || key.isBlank()) {
            writeError(response, HttpStatus.BAD_REQUEST, "IAM_IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required for this command.");
            return;
        }
        String normalized = key.trim();
        if (normalized.length() < properties.getKeyMinLength()
                || normalized.length() > properties.getKeyMaxLength()) {
            writeError(response, HttpStatus.BAD_REQUEST, "IAM_IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key length must be between "
                            + properties.getKeyMinLength() + " and " + properties.getKeyMaxLength() + ".");
            return;
        }
        Long tenantId = resolveTenantId(request);
        if (tenantId == null) {
            writeError(response, HttpStatus.BAD_REQUEST, "IAM_TENANT_CONTEXT_REQUIRED",
                    "Trusted tenant context is required for this command.");
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = hash(cachedRequest.getMethod(), cachedRequest.getRequestURI(), cachedRequest.cachedBody());
        Instant now = clock.instant();

        ReplayIdempotencyStore.ClaimOutcome outcome = store.claim(tenantId, normalized, requestHash, now);
        if (outcome instanceof ReplayIdempotencyStore.ClaimOutcome.DifferentBody) {
            writeError(response, HttpStatus.CONFLICT, "IAM_IDEMPOTENCY_REPLAY_CONFLICT",
                    "Idempotency-Key was already used with a different request body.");
            return;
        }
        if (outcome instanceof ReplayIdempotencyStore.ClaimOutcome.InFlight) {
            writeError(response, HttpStatus.CONFLICT, "IAM_IDEMPOTENCY_IN_FLIGHT",
                    "An identical request is still in progress or its response was lost; do not resubmit blindly.");
            return;
        }
        if (outcome instanceof ReplayIdempotencyStore.ClaimOutcome.Replay replay) {
            replayResponse(response, replay.response());
            return;
        }

        CachingHttpServletResponse cachingResponse = new CachingHttpServletResponse(response);
        try {
            filterChain.doFilter(cachedRequest, cachingResponse);
        } catch (ServletException | IOException | RuntimeException e) {
            store.release(tenantId, normalized);
            throw e;
        }
        CachedHttpResponse snapshot = cachingResponse.snapshot();
        if (snapshot.status() >= 200 && snapshot.status() < 300) {
            store.commit(tenantId, normalized, snapshot, clock.instant());
        } else {
            store.release(tenantId, normalized);
        }
    }

    private Long resolveTenantId(HttpServletRequest request) {
        Object trustedContext = request.getAttribute(TrustedRequestContext.class.getName());
        if (trustedContext instanceof TrustedRequestContext context) {
            return context.tenantId();
        }
        String tenantHeader = request.getHeader(properties.getTenantHeader());
        try {
            long tenantId = Long.parseLong(tenantHeader == null ? "" : tenantHeader.trim());
            return tenantId > 0 ? tenantId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String hash(String method, String path, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void replayResponse(HttpServletResponse response, CachedHttpResponse cached) throws IOException {
        response.setStatus(cached.status());
        if (cached.contentType() != null) {
            response.setContentType(cached.contentType());
        }
        response.setContentLength(cached.body().length);
        response.getOutputStream().write(cached.body());
    }

    private static void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String traceId = UUID.randomUUID().toString();
        String json = "{\"success\":false,\"code\":\"" + code + "\",\"message\":\""
                + message.replace("\"", "'") + "\",\"traceId\":\"" + traceId + "\"}";
        response.getWriter().write(json);
    }

    /** 便于服务在受保护方法集需要扩展时读取。 */
    public static List<String> defaultMethods() {
        return List.of("POST", "PUT", "DELETE", "PATCH");
    }
}
