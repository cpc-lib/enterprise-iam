package com.enterprise.iam.gateway.security;

import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import reactor.core.publisher.Mono;

/** Deployment adapter port backed by an authoritative Redis session projection. */
@FunctionalInterface
public interface ReactiveSessionSnapshotReader {

    Mono<SessionSecurityProjection> find(
            long tenantId,
            long subjectId,
            long sessionId);
}
