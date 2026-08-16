package com.enterprise.iam.gateway.delegation;

@FunctionalInterface
public interface GatewayDelegationTokenIssuer {

    String issue(String audience, AuthenticatedGatewayPrincipal principal);
}
