package com.enterprise.iam.common.core.fieldpolicy;

import com.enterprise.iam.common.core.masking.MaskStrategyType;

import java.util.Map;
import java.util.Objects;

/**
 * A resolved typed mask strategy for a single field: the strategy type plus
 * its validated config. Pure data — per SPEC 38 §5 a strategy never contains
 * code, expressions or templates that execute dynamically.
 */
public record MaskStrategySpec(MaskStrategyType type, Map<String, Object> config) {

    public MaskStrategySpec {
        Objects.requireNonNull(type, "type must not be null");
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
