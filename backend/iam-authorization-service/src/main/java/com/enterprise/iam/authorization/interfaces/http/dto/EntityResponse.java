package com.enterprise.iam.authorization.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

/**
 * 通用命令响应（OpenAPI phase-02 EntityResponse）：
 * id 为十进制字符串（D-1 全局 long-as-string），version 为数值量（{@link NumericLong}）。
 */
public record EntityResponse(
        long id,
        @NumericLong long version,
        String status) {
}
