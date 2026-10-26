package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.DataPredicateArtifact;

import java.time.Instant;
import java.util.Optional;

/**
 * Server-side store for typed data-predicate artifacts (SPEC 38 §8). Lookups
 * are fail-closed: an unknown, malformed or expired reference yields empty.
 */
public interface DataPredicateArtifactRepository {

    void save(DataPredicateArtifact artifact);

    Optional<DataPredicateArtifact> findActiveByRef(String ref, Instant now);
}
