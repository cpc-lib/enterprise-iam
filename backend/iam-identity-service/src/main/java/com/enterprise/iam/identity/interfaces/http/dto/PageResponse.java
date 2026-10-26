package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        @NumericLong long total,
        int page,
        int pageSize
) {}
