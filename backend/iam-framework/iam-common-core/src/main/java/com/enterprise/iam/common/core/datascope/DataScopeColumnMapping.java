package com.enterprise.iam.common.core.datascope;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Maps the logical data-scope fields to concrete table columns of the
 * consuming service. Column names are interpolated into generated WHERE
 * fragments, so they are restricted to plain SQL identifiers (no quoting,
 * no expressions) as an injection guard.
 */
public record DataScopeColumnMapping(
        String ownerUserColumn,
        String ownerTeamColumn,
        String instanceKeyColumn) {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");

    public DataScopeColumnMapping {
        ownerUserColumn = requireIdentifier(ownerUserColumn, "ownerUserColumn");
        ownerTeamColumn = requireIdentifier(ownerTeamColumn, "ownerTeamColumn");
        instanceKeyColumn = requireIdentifier(instanceKeyColumn, "instanceKeyColumn");
    }

    private static String requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a plain SQL identifier: " + value);
        }
        return value;
    }
}
