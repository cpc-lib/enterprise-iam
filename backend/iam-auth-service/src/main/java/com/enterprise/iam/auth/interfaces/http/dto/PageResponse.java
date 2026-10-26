package com.enterprise.iam.auth.interfaces.http.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int pageSize
) {}
