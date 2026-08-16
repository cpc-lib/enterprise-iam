package com.enterprise.iam.gateway.delegation;

import com.enterprise.iam.common.security.delegation.DelegationSigningRequest;
import com.enterprise.iam.common.security.delegation.Es256DelegationTokenSigner;

import java.util.Objects;

public final class Es256GatewayDelegationTokenIssuer implements GatewayDelegationTokenIssuer {

    private final Es256DelegationTokenSigner signer;

    public Es256GatewayDelegationTokenIssuer(Es256DelegationTokenSigner signer) {
        this.signer = Objects.requireNonNull(signer, "signer must not be null");
    }

    @Override
    public String issue(String audience, AuthenticatedGatewayPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        return signer.sign(new DelegationSigningRequest(
                audience,
                principal.tenantId(),
                principal.subjectId(),
                principal.sessionId(),
                principal.requestId()));
    }
}
