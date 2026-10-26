package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.FieldWritePlanArtifact;

import java.time.Instant;
import java.util.Optional;

/**
 * Server-side store for typed field-write plan artifacts (SPEC 38 §8).
 * Unknown or expired references resolve empty — consumers fail closed.
 */
public interface FieldWritePlanArtifactRepository {

    void save(FieldWritePlanArtifact artifact);

    Optional<FieldWritePlanArtifact> findActiveByRef(String ref, Instant now);
}
