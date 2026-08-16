package com.enterprise.iam.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("iam.auth.session-issuance")
public final class SessionIssuanceProperties {

    private boolean enabled;
    private Integer nodeId;
    private Duration idleTtl = Duration.ofDays(1);
    private Duration absoluteTtl = Duration.ofDays(30);
    private Duration refreshTokenTtl = Duration.ofDays(14);
    private int maximumConcurrentSessions = 10;
    private Duration transactionTimeout = Duration.ofSeconds(5);

    public void validateEnabled() {
        if (!enabled) {
            return;
        }
        if (nodeId == null || nodeId < 0 || nodeId > 1_023) {
            throw new IllegalStateException(
                    "iam.auth.session-issuance.node-id must be explicitly set to 0..1023");
        }
        requireWholeSecondDuration(idleTtl, Duration.ofMinutes(1),
                Duration.ofDays(30), "idle-ttl");
        requireWholeSecondDuration(absoluteTtl, idleTtl,
                Duration.ofDays(90), "absolute-ttl");
        requireWholeSecondDuration(refreshTokenTtl, Duration.ofMinutes(1),
                absoluteTtl, "refresh-token-ttl");
        requireWholeSecondDuration(transactionTimeout, Duration.ofSeconds(1),
                Duration.ofSeconds(30), "transaction-timeout");
        if (maximumConcurrentSessions < 1 || maximumConcurrentSessions > 100) {
            throw new IllegalStateException(
                    "iam.auth.session-issuance.maximum-concurrent-sessions must be 1..100");
        }
    }

    private static void requireWholeSecondDuration(
            Duration value,
            Duration minimum,
            Duration maximum,
            String name) {
        if (value == null || value.compareTo(minimum) < 0
                || value.compareTo(maximum) > 0 || value.toNanosPart() != 0) {
            throw new IllegalStateException(
                    "iam.auth.session-issuance." + name
                            + " is outside its whole-second range");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getNodeId() {
        return nodeId;
    }

    public void setNodeId(Integer nodeId) {
        this.nodeId = nodeId;
    }

    public Duration getIdleTtl() {
        return idleTtl;
    }

    public void setIdleTtl(Duration idleTtl) {
        this.idleTtl = idleTtl;
    }

    public Duration getAbsoluteTtl() {
        return absoluteTtl;
    }

    public void setAbsoluteTtl(Duration absoluteTtl) {
        this.absoluteTtl = absoluteTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public int getMaximumConcurrentSessions() {
        return maximumConcurrentSessions;
    }

    public void setMaximumConcurrentSessions(int maximumConcurrentSessions) {
        this.maximumConcurrentSessions = maximumConcurrentSessions;
    }

    public Duration getTransactionTimeout() {
        return transactionTimeout;
    }

    public void setTransactionTimeout(Duration transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }
}
