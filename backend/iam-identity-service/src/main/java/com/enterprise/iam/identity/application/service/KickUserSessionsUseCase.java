package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.application.port.out.UserSessionRevocationPort;
import com.enterprise.iam.identity.domain.model.User;

import java.util.Objects;

public class KickUserSessionsUseCase {

    private final UserRepository userRepository;
    private final UserSessionRevocationPort sessionRevocationPort;

    public KickUserSessionsUseCase(
            UserRepository userRepository,
            UserSessionRevocationPort sessionRevocationPort) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.sessionRevocationPort = sessionRevocationPort;
    }

    public void kick(long tenantId, long userId) {
        userRepository.findById(tenantId, userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));

        if (sessionRevocationPort != null) {
            sessionRevocationPort.kickUserSessions(tenantId, userId);
        }
    }
}
