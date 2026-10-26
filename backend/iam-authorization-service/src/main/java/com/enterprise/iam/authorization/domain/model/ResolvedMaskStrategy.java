package com.enterprise.iam.authorization.domain.model;

import java.util.Map;
import java.util.Set;

/**
 * An ACTIVE mask strategy definition resolved from {@code iam_mask_strategy}
 * at decision time. Per SPEC 38 §5 a mask strategy is a predefined typed
 * implementation with validated config — never code, expressions or
 * templates. Strategies that are missing, DISABLED or soft-deleted are
 * never loaded, so consumers fail closed to a generic mask.
 */
public record ResolvedMaskStrategy(
        long strategyId,
        String strategyType,
        Map<String, Object> config) {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "PHONE", "EMAIL", "ID_CARD", "BANK_CARD", "NAME", "ADDRESS", "GENERIC_PARTIAL");

    public ResolvedMaskStrategy {
        if (strategyId <= 0) {
            throw new IllegalArgumentException("strategyId must be positive");
        }
        if (strategyType == null || !ALLOWED_TYPES.contains(strategyType)) {
            throw new IllegalArgumentException("strategyType must be one of " + ALLOWED_TYPES);
        }
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
