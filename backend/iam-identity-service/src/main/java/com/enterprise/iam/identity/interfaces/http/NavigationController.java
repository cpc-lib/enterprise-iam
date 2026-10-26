package com.enterprise.iam.identity.interfaces.http;

import com.enterprise.iam.identity.application.service.GetFieldSchemaUseCase;
import com.enterprise.iam.identity.application.service.GetNavigationSchemaUseCase;
import com.enterprise.iam.identity.application.service.GetPageSchemaUseCase;
import com.enterprise.iam.identity.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.identity.interfaces.http.dto.FieldDefinitionDto;
import com.enterprise.iam.identity.interfaces.http.dto.FieldSchemaDto;
import com.enterprise.iam.identity.interfaces.http.dto.NavItemDto;
import com.enterprise.iam.identity.interfaces.http.dto.NavSchemaNodeDto;
import com.enterprise.iam.identity.interfaces.http.dto.PageColumnDto;
import com.enterprise.iam.identity.interfaces.http.dto.PageOperationDto;
import com.enterprise.iam.identity.interfaces.http.dto.PageSchemaDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/navigation")
@ConditionalOnProperty(prefix = "iam.identity", name = "enabled", havingValue = "true")
public class NavigationController {

    private final GetNavigationSchemaUseCase getNavigationSchemaUseCase;
    private final GetPageSchemaUseCase getPageSchemaUseCase;
    private final GetFieldSchemaUseCase getFieldSchemaUseCase;

    public NavigationController(
            GetNavigationSchemaUseCase getNavigationSchemaUseCase,
            GetPageSchemaUseCase getPageSchemaUseCase,
            GetFieldSchemaUseCase getFieldSchemaUseCase) {
        this.getNavigationSchemaUseCase = getNavigationSchemaUseCase;
        this.getPageSchemaUseCase = getPageSchemaUseCase;
        this.getFieldSchemaUseCase = getFieldSchemaUseCase;
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

    @GetMapping("/schema")
    public ResponseEntity<ApiResponse<List<NavSchemaNodeDto>>> getSchema(
            @RequestHeader(value = "X-Tenant-Id", required = false) Long headerTenantId,
            @RequestParam(value = "tenantId", required = false) Long queryTenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long queryUserId) {
        long tenantId = resolveTenantId(headerTenantId, queryTenantId);
        Long userId = headerUserId != null ? headerUserId : queryUserId;
        String traceId = UUID.randomUUID().toString();
        var nodes = getNavigationSchemaUseCase.getSchema(tenantId, userId);
        var dtos = nodes.stream()
                .map(node -> new NavSchemaNodeDto(
                        node.key(),
                        node.title(),
                        node.path(),
                        node.icon(),
                        node.order(),
                        node.items() == null ? null : node.items().stream()
                                .map(item -> new NavItemDto(
                                        item.key(),
                                        item.path(),
                                        item.title(),
                                        item.icon(),
                                        item.order()))
                                .toList()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @GetMapping("/page-schemas/{pageKey}")
    public ResponseEntity<ApiResponse<PageSchemaDto>> getPageSchema(@PathVariable String pageKey) {
        String traceId = UUID.randomUUID().toString();
        return getPageSchemaUseCase.getPageSchema(pageKey)
                .map(schema -> {
                    var ops = schema.operations().stream()
                            .map(o -> new PageOperationDto(o.controlId(), o.operationId(), o.title(), o.actionType()))
                            .toList();
                    var cols = schema.columns().stream()
                            .map(c -> new PageColumnDto(c.fieldKey(), c.label(), c.dataType(), c.sortable(), c.filterable()))
                            .toList();
                    var dto = new PageSchemaDto(schema.pageKey(), schema.title(), schema.resourceType(), ops, cols);
                    return ResponseEntity.ok(ApiResponse.ok(dto, traceId));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/field-schemas/{resourceType}")
    public ResponseEntity<ApiResponse<FieldSchemaDto>> getFieldSchema(@PathVariable String resourceType) {
        String traceId = UUID.randomUUID().toString();
        return getFieldSchemaUseCase.getFieldSchema(resourceType)
                .map(schema -> {
                    var fields = schema.fields().stream()
                            .map(f -> new FieldDefinitionDto(
                                    f.fieldKey(),
                                    f.fieldName(),
                                    f.dataType(),
                                    f.sensitive(),
                                    f.maskable(),
                                    f.description()))
                            .toList();
                    var dto = new FieldSchemaDto(schema.resourceType(), fields);
                    return ResponseEntity.ok(ApiResponse.ok(dto, traceId));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
