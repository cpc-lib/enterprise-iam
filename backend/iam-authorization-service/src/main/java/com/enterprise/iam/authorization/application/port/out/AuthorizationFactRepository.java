package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;

public interface AuthorizationFactRepository {

    AuthorizationFacts loadFacts(AuthorizationRequest request);
}
