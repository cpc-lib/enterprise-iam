package com.enterprise.iam.authorization.infrastructure.persistence.row;

public record ShareEpochRow(long expectedEpoch, long appliedEpoch) {
}
