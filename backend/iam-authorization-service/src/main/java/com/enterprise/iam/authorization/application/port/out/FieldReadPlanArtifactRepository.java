package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.FieldReadPlanArtifact;

import java.time.Instant;
import java.util.Optional;

/**
 * Server-side store for typed field-read plan artifacts (SPEC 38 §8).
 * Unknown or expired references resolve empty — consumers fail closed.
 */
public interface FieldReadPlanArtifactRepository {

    void save(FieldReadPlanArtifact artifact);

    Optional<FieldReadPlanArtifact> findActiveByRef(String ref, Instant now);
}
