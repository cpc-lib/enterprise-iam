package com.enterprise.iam.identity.interfaces.http;

import com.enterprise.iam.identity.application.service.CreateMenuUseCase;
import com.enterprise.iam.identity.application.service.DeleteMenuUseCase;
import com.enterprise.iam.identity.application.service.DisableMenuUseCase;
import com.enterprise.iam.identity.application.service.EnableMenuUseCase;
import com.enterprise.iam.identity.application.service.GetMenuDetailUseCase;
import com.enterprise.iam.identity.application.service.ListMenusUseCase;
import com.enterprise.iam.identity.application.service.PublishMenuUseCase;
import com.enterprise.iam.identity.application.service.ReorderMenusUseCase;
import com.enterprise.iam.identity.application.service.UpdateMenuUseCase;
import com.enterprise.iam.identity.domain.model.Menu;
import com.enterprise.iam.identity.domain.model.MenuNode;
import com.enterprise.iam.identity.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.identity.interfaces.http.dto.CreateMenuRequest;
import com.enterprise.iam.identity.interfaces.http.dto.MenuDto;
import com.enterprise.iam.identity.interfaces.http.dto.PageResponse;
import com.enterprise.iam.identity.interfaces.http.dto.UpdateMenuRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/menus")
@ConditionalOnProperty(prefix = "iam.identity", name = "enabled", havingValue = "true")
public class MenuController {

    private final CreateMenuUseCase createMenuUseCase;
    private final UpdateMenuUseCase updateMenuUseCase;
    private final EnableMenuUseCase enableMenuUseCase;
    private final DisableMenuUseCase disableMenuUseCase;
    private final PublishMenuUseCase publishMenuUseCase;
    private final DeleteMenuUseCase deleteMenuUseCase;
    private final ListMenusUseCase listMenusUseCase;
    private final GetMenuDetailUseCase getMenuDetailUseCase;
    private final ReorderMenusUseCase reorderMenusUseCase;

    public MenuController(
            CreateMenuUseCase createMenuUseCase,
            UpdateMenuUseCase updateMenuUseCase,
            EnableMenuUseCase enableMenuUseCase,
            DisableMenuUseCase disableMenuUseCase,
            PublishMenuUseCase publishMenuUseCase,
            DeleteMenuUseCase deleteMenuUseCase,
            ListMenusUseCase listMenusUseCase,
            GetMenuDetailUseCase getMenuDetailUseCase,
            ReorderMenusUseCase reorderMenusUseCase) {
        this.createMenuUseCase = Objects.requireNonNull(createMenuUseCase);
        this.updateMenuUseCase = Objects.requireNonNull(updateMenuUseCase);
        this.enableMenuUseCase = Objects.requireNonNull(enableMenuUseCase);
        this.disableMenuUseCase = Objects.requireNonNull(disableMenuUseCase);
        this.publishMenuUseCase = Objects.requireNonNull(publishMenuUseCase);
        this.deleteMenuUseCase = Objects.requireNonNull(deleteMenuUseCase);
        this.listMenusUseCase = Objects.requireNonNull(listMenusUseCase);
        this.getMenuDetailUseCase = Objects.requireNonNull(getMenuDetailUseCase);
        this.reorderMenusUseCase = Objects.requireNonNull(reorderMenusUseCase);
    }

    private long resolveTenantId(Long headerTenantId, Long queryTenantId) {
        if (headerTenantId != null && headerTenantId > 0) {
            return headerTenantId;
        }
        if (queryTenantId != null && queryTenantId > 0) {
            return queryTenantId;
        }
        return 10001L;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MenuDto>> create(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @Valid @RequestBody CreateMenuRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        Menu menu = createMenuUseCase.create(
                tenantId,
                request.parentId(),
                request.effectiveMenuKey(),
                request.title(),
                request.path(),
                request.icon(),
                request.effectiveSortOrder(),
                request.effectivePermissionCode(),
                request.component(),
                request.effectiveVisible(),
                request.effectivePublishImmediately());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(toDto(menu), traceId));
    }

    @GetMapping({"", "/tree"})
    public ResponseEntity<ApiResponse<List<MenuDto>>> listTree(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "menuType", required = false) String menuType,
            @RequestParam(value = "status", required = false) String status) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        List<MenuNode> tree = listMenusUseCase.listMenuTree(tenantId);
        List<MenuDto> dtos = tree.stream().map(this::toDto).toList();
        if ((title != null && !title.isBlank())
                || (menuType != null && !menuType.isBlank())
                || (status != null && !status.isBlank())) {
            dtos = filterTree(dtos, title, menuType, status);
        }
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponse<MenuDto>>> listPage(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "menuType", required = false) String menuType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        List<Menu> all = listMenusUseCase.listAll(tenantId);
        List<MenuDto> flatList = all.stream().map(this::toDto).toList();

        if (title != null && !title.isBlank()) {
            String kw = title.trim().toLowerCase();
            flatList = flatList.stream()
                    .filter(m -> (m.title() != null && m.title().toLowerCase().contains(kw))
                            || (m.code() != null && m.code().toLowerCase().contains(kw)))
                    .toList();
        }
        if (menuType != null && !menuType.isBlank()) {
            String t = menuType.trim().toUpperCase();
            flatList = flatList.stream()
                    .filter(m -> m.menuType() != null && m.menuType().equalsIgnoreCase(t))
                    .toList();
        }
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase();
            flatList = flatList.stream()
                    .filter(m -> m.status() != null && m.status().equalsIgnoreCase(s))
                    .toList();
        }

        long total = flatList.size();
        int p = Math.max(1, page);
        int ps = Math.max(1, pageSize);
        int fromIndex = Math.min((p - 1) * ps, flatList.size());
        int toIndex = Math.min(fromIndex + ps, flatList.size());
        List<MenuDto> pagedItems = flatList.subList(fromIndex, toIndex);

        return ResponseEntity.ok(ApiResponse.ok(new PageResponse<>(pagedItems, total, p, ps), traceId));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<MenuDto>> getDetail(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long id) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        return getMenuDetailUseCase.getDetail(tenantId, id)
                .map(m -> ResponseEntity.ok(ApiResponse.ok(toDto(m), traceId)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<MenuDto>> update(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long id,
            @Valid @RequestBody UpdateMenuRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        long version = request.version() != null ? request.version()
                : getMenuDetailUseCase.getDetail(tenantId, id).map(Menu::version).orElse(1L);
        String traceId = UUID.randomUUID().toString();
        Menu updated = updateMenuUseCase.update(
                tenantId,
                id,
                request.parentId(),
                request.effectiveMenuKey(),
                request.title(),
                request.path(),
                request.icon(),
                request.effectiveSortOrder(),
                request.effectivePermissionCode(),
                request.component(),
                request.effectiveVisible(),
                version);
        return ResponseEntity.ok(ApiResponse.ok(toDto(updated), traceId));
    }

    @PostMapping("/{id:\\d+}/enable")
    public ResponseEntity<ApiResponse<MenuDto>> enable(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long id,
            @RequestParam(value = "version", required = false) Long version) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        long v = version != null ? version
                : getMenuDetailUseCase.getDetail(tenantId, id).map(Menu::version).orElse(1L);
        String traceId = UUID.randomUUID().toString();
        Menu enabled = enableMenuUseCase.enable(tenantId, id, v);
        return ResponseEntity.ok(ApiResponse.ok(toDto(enabled), traceId));
    }

    @PostMapping("/{id:\\d+}/disable")
    public ResponseEntity<ApiResponse<MenuDto>> disable(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long id,
            @RequestParam(value = "version", required = false) Long version) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        long v = version != null ? version
                : getMenuDetailUseCase.getDetail(tenantId, id).map(Menu::version).orElse(1L);
        String traceId = UUID.randomUUID().toString();
        Menu disabled = disableMenuUseCase.disable(tenantId, id, v);
        return ResponseEntity.ok(ApiResponse.ok(toDto(disabled), traceId));
    }

    @PostMapping("/{id:\\d+}/publish")
    public ResponseEntity<ApiResponse<MenuDto>> publish(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long id,
            @RequestParam(value = "version", required = false) Long version) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        long v = version != null ? version
                : getMenuDetailUseCase.getDetail(tenantId, id).map(Menu::version).orElse(1L);
        String traceId = UUID.randomUUID().toString();
        Menu published = publishMenuUseCase.publish(tenantId, id, v);
        return ResponseEntity.ok(ApiResponse.ok(toDto(published), traceId));
    }

    @PostMapping({"/publish-all", "/publish"})
    public ResponseEntity<ApiResponse<Integer>> publishAll(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        String traceId = UUID.randomUUID().toString();
        int count = publishMenuUseCase.publishAll(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(count, traceId));
    }

    @PostMapping("/reorder")
    public ResponseEntity<ApiResponse<Void>> reorder(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @RequestBody Object body) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        Map<Long, Integer> orderMap = new HashMap<>();
        if (body instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object idObj = m.get("id");
                    Object orderObj = m.get("sortOrder");
                    if (idObj != null && orderObj != null) {
                        try {
                            orderMap.put(Long.parseLong(idObj.toString()), ((Number) orderObj).intValue());
                        } catch (Exception ignored) {}
                    }
                }
            }
        } else if (body instanceof Map<?, ?> map) {
            Object inner = map.get("menuOrderMap");
            Map<?, ?> target = (inner instanceof Map<?, ?> innerMap) ? innerMap : map;
            for (var e : target.entrySet()) {
                try {
                    orderMap.put(Long.parseLong(e.getKey().toString()), Integer.parseInt(e.getValue().toString()));
                } catch (Exception ignored) {}
            }
        }
        reorderMenusUseCase.reorder(tenantId, orderMap);
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @PathVariable long id,
            @RequestParam(value = "version", required = false) Long version) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        long v = version != null ? version
                : getMenuDetailUseCase.getDetail(tenantId, id).map(Menu::version).orElse(1L);
        String traceId = UUID.randomUUID().toString();
        deleteMenuUseCase.delete(tenantId, id, v);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    private String resolveMenuType(String component, String path) {
        if (component != null && !component.isBlank()) {
            return "MENU";
        }
        if (path != null && !path.isBlank()) {
            return "DIRECTORY";
        }
        return "BUTTON";
    }

    private MenuDto toDto(Menu m) {
        String menuType = resolveMenuType(m.component(), m.path());
        return new MenuDto(
                m.id(),
                m.tenantId(),
                m.parentId(),
                m.menuKey(),
                m.menuKey(),
                m.title(),
                m.path(),
                m.icon(),
                m.sortOrder(),
                m.status().name(),
                m.publishStatus().name(),
                m.permissionCode(),
                m.permissionCode(),
                m.component(),
                menuType,
                m.visible(),
                m.version(),
                m.createdAt(),
                m.updatedAt(),
                null);
    }

    private MenuDto toDto(MenuNode node) {
        List<MenuDto> childDtos = node.children() == null ? List.of()
                : node.children().stream().map(this::toDto).toList();
        String menuType = resolveMenuType(node.component(), node.path());
        return new MenuDto(
                node.id(),
                node.tenantId(),
                node.parentId(),
                node.menuKey(),
                node.menuKey(),
                node.title(),
                node.path(),
                node.icon(),
                node.sortOrder(),
                node.status().name(),
                node.publishStatus().name(),
                node.permissionCode(),
                node.permissionCode(),
                node.component(),
                menuType,
                node.visible(),
                node.version(),
                node.createdAt(),
                node.updatedAt(),
                childDtos);
    }

    private List<MenuDto> filterTree(List<MenuDto> nodes, String title, String menuType, String status) {
        List<MenuDto> result = new ArrayList<>();
        String kw = (title != null && !title.isBlank()) ? title.trim().toLowerCase() : null;
        String t = (menuType != null && !menuType.isBlank()) ? menuType.trim().toUpperCase() : null;
        String s = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        for (MenuDto node : nodes) {
            List<MenuDto> filteredChildren = (node.children() != null && !node.children().isEmpty())
                    ? filterTree(node.children(), title, menuType, status)
                    : List.of();

            boolean matchesTitle = (kw == null)
                    || (node.title() != null && node.title().toLowerCase().contains(kw))
                    || (node.code() != null && node.code().toLowerCase().contains(kw));

            boolean matchesType = (t == null)
                    || (node.menuType() != null && node.menuType().equalsIgnoreCase(t));

            boolean matchesStatus = (s == null)
                    || (node.status() != null && node.status().equalsIgnoreCase(s));

            boolean selfMatches = matchesTitle && matchesType && matchesStatus;

            if (selfMatches || !filteredChildren.isEmpty()) {
                result.add(new MenuDto(
                        node.id(),
                        node.tenantId(),
                        node.parentId(),
                        node.menuKey(),
                        node.code(),
                        node.title(),
                        node.path(),
                        node.icon(),
                        node.sortOrder(),
                        node.status(),
                        node.publishStatus(),
                        node.permissionCode(),
                        node.permission(),
                        node.component(),
                        node.menuType(),
                        node.visible(),
                        node.version(),
                        node.createdAt(),
                        node.updatedAt(),
                        filteredChildren));
            }
        }
        return result;
    }
}
