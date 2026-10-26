package com.enterprise.iam.common.core.masking;

import java.util.Map;

/**
 * Executes typed mask strategies against plain values. All implementations
 * are static, deterministic string transforms — no expression evaluation of
 * any kind. Every failure mode (null type, blank value, invalid config)
 * fails closed to a fully masked placeholder rather than leaking the raw
 * value.
 */
public final class TypedMasker {

    /** Conservative placeholder used whenever masking cannot be performed safely. */
    public static final String FALLBACK_MASK = "***";

    private TypedMasker() {
    }

    public static String mask(MaskStrategyType type, Map<String, Object> config, String value) {
        if (type == null || value == null) {
            return FALLBACK_MASK;
        }
        if (value.isEmpty()) {
            return FALLBACK_MASK;
        }
        Map<String, Object> safeConfig = config == null ? Map.of() : config;
        return switch (type) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case ID_CARD -> keep(value, 4, 4);
            case BANK_CARD -> keep(value, 0, 4);
            case NAME -> keep(value, 1, 0);
            case ADDRESS -> keep(value, 6, 0);
            case GENERIC_PARTIAL -> maskGeneric(safeConfig, value);
        };
    }

    /** Keep first 3 and last 4 digits: 138****8000. Too short to do so safely masks fully. */
    private static String maskPhone(String value) {
        if (value.length() < 7) {
            return FALLBACK_MASK;
        }
        return keep(value, 3, 4);
    }

    /** Keep first local-part char and the full domain: a***@example.com. */
    private static String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            return FALLBACK_MASK;
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    private static String maskGeneric(Map<String, Object> config, String value) {
        int keepStart = intConfig(config, "keepStart", 1);
        int keepEnd = intConfig(config, "keepEnd", 1);
        if (keepStart < 0 || keepEnd < 0) {
            return FALLBACK_MASK;
        }
        return keep(value, keepStart, keepEnd);
    }

    /**
     * Keeps {@code keepStart} leading and {@code keepEnd} trailing characters,
     * replacing everything between with a fixed run of asterisks. When the
     * value is too short to keep the requested edges without revealing all of
     * it, masks fully instead (fail closed).
     */
    private static String keep(String value, int keepStart, int keepEnd) {
        if (keepStart + keepEnd >= value.length()) {
            return FALLBACK_MASK;
        }
        return value.substring(0, keepStart) + "***" + value.substring(value.length() - keepEnd);
    }

    private static int intConfig(Map<String, Object> config, String key, int defaultValue) {
        Object raw = config.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
