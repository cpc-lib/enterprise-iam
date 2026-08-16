package com.enterprise.iam.auth.application.port.out;

import com.enterprise.iam.auth.application.model.IssuedLoginSession;
import com.enterprise.iam.auth.domain.model.ResolvedLoginIdentity;

public interface LoginSessionIssuer {

    IssuedLoginSession issue(ResolvedLoginIdentity identity, String requestId);
}
