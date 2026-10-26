package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.AuthorizationResult;

import java.util.Optional;

public interface AuthorizationDecisionRecordRepository {

    Optional<AuthorizationResult> findById(long decisionId);

    void record(AuthorizationResult result);
}
