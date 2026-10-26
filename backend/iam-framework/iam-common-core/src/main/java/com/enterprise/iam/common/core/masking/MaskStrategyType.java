package com.enterprise.iam.common.core.masking;

/**
 * Predefined mask strategy types per SPEC 38 §5 and the
 * {@code iam_mask_strategy.strategy_type} CHECK constraint. A strategy is a
 * typed implementation with validated config — never code, expressions or
 * templates that execute dynamically.
 */
public enum MaskStrategyType {
    PHONE,
    EMAIL,
    ID_CARD,
    BANK_CARD,
    NAME,
    ADDRESS,
    GENERIC_PARTIAL
}
