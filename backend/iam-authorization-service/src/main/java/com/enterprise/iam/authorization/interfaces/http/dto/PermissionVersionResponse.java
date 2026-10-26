package com.enterprise.iam.authorization.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

/**
 * 策略绑定替换响应（OpenAPI phase-02 PermissionVersionResponse）：
 * bindingVersion/permissionVersion 均为单调递增的数值版本号。
 */
public record PermissionVersionResponse(
        @NumericLong long bindingVersion,
        @NumericLong long permissionVersion) {
}
