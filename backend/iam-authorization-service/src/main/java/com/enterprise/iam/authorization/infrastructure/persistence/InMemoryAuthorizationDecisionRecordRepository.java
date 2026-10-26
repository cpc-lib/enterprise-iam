package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAuthorizationDecisionRecordRepository
        implements AuthorizationDecisionRecordRepository {

    private final ConcurrentHashMap<Long, AuthorizationResult> store = new ConcurrentHashMap<>();

    @Override
    public Optional<AuthorizationResult> findById(long decisionId) {
        return Optional.ofNullable(store.get(decisionId));
    }

    @Override
    public void record(AuthorizationResult result) {
        store.put(result.decisionId(), result);
    }
}
