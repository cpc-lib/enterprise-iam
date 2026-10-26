package com.enterprise.iam.common.core.datascope;

/**
 * Frozen data-visibility scope ordered from broadest to narrowest.
 * ALL &gt; TENANT &gt; TEAM_AND_DESCENDANTS &gt; TEAM &gt; SELF.
 */
public enum DataScope {

    ALL,
    TENANT,
    TEAM_AND_DESCENDANTS,
    TEAM,
    SELF;

    public boolean isNarrowerThan(DataScope other) {
        return this.ordinal() > other.ordinal();
    }

    public boolean isBroaderThan(DataScope other) {
        return this.ordinal() < other.ordinal();
    }
}
