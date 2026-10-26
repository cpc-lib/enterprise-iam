package com.enterprise.iam.common.core.fieldpolicy;

/**
 * Frozen field-level access policy. READ means full visibility. MASK means
 * the value is returned but partially obfuscated. HIDE means the field is
 * omitted from the response entirely. WRITE guards mutations.
 */
public enum FieldPolicy {

    READ,
    MASK,
    HIDE,
    WRITE_DENIED;

    public boolean isVisible() {
        return this == READ || this == MASK;
    }
}
