package com.enterprise.iam.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 幂等过滤器配置。默认关闭（fail-safe），由各服务在 application.yml 显式开启并声明受保护路径。
 */
@ConfigurationProperties(prefix = "iam.idempotency")
public class IdempotencyProperties {

    private boolean enabled = false;
    /** Replay store backend: memory (single-node default), jdbc or redis (multi-instance). */
    private String store = "memory";
    private String headerName = "Idempotency-Key";
    /** 旧链路可信租户头回退；已验证的 TrustedRequestContext 始终优先。 */
    private String tenantHeader = "X-Tenant-Id";
    private List<String> paths = new ArrayList<>();
    private List<String> methods = List.of("POST", "PUT", "DELETE", "PATCH");
    private int keyMinLength = 20;
    private int keyMaxLength = 128;
    private Duration ttl = Duration.ofHours(24);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getTenantHeader() {
        return tenantHeader;
    }

    public void setTenantHeader(String tenantHeader) {
        this.tenantHeader = tenantHeader;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public int getKeyMinLength() {
        return keyMinLength;
    }

    public void setKeyMinLength(int keyMinLength) {
        this.keyMinLength = keyMinLength;
    }

    public int getKeyMaxLength() {
        return keyMaxLength;
    }

    public void setKeyMaxLength(int keyMaxLength) {
        this.keyMaxLength = keyMaxLength;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }
}
