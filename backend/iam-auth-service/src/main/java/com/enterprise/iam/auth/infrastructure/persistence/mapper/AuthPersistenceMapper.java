package com.enterprise.iam.auth.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AuthPersistenceMapper {

    CredentialRow findCredential(@Param("tenantId") long tenantId,
                                 @Param("userId") long userId);

    Integer performEnumerationResistantDummyLookup();

    LoginIdentityRow findLoginIdentity(@Param("tenantCode") String tenantCode,
                                       @Param("identityType") String identityType,
                                       @Param("identityKey") String identityKey);

    int revokeSession(@Param("tenantId") long tenantId,
                      @Param("sessionId") long sessionId,
                      @Param("now") Instant now);

    int revokeSessionRefreshTokens(@Param("tenantId") long tenantId,
                                   @Param("sessionId") long sessionId,
                                   @Param("now") Instant now);

    List<Long> lockTokenVersion(@Param("tenantId") long tenantId,
                                @Param("userId") long userId);

    List<LockedSecurityStateRow> findSecurityStateForUpdate(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId);

    List<LockedCredentialRow> findCredentialForUpdate(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId);

    List<PasswordChangeSessionRow> findActiveSessionsForPasswordChange(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId);

    Long countActiveSessions(@Param("tenantId") long tenantId,
                             @Param("userId") long userId,
                             @Param("now") Instant now);

    int insertLoginSession(@Param("id") long id,
                           @Param("tenantId") long tenantId,
                           @Param("sessionId") long sessionId,
                           @Param("userId") long userId,
                           @Param("now") Instant now,
                           @Param("idleExpireAt") Instant idleExpireAt,
                           @Param("absoluteExpireAt") Instant absoluteExpireAt);

    int insertRefreshToken(@Param("id") long id,
                           @Param("tenantId") long tenantId,
                           @Param("userId") long userId,
                           @Param("sessionId") long sessionId,
                           @Param("tokenFamilyId") long tokenFamilyId,
                           @Param("tokenHash") byte[] tokenHash,
                           @Param("now") Instant now,
                           @Param("expireAt") Instant expireAt);

    int insertLoginDelivery(@Param("tenantId") long tenantId,
                            @Param("userId") long userId,
                            @Param("sessionId") long sessionId,
                            @Param("requestId") String requestId,
                            @Param("confirmBefore") Instant confirmBefore,
                            @Param("now") Instant now);

    List<StoredRefreshTokenRow> findRefreshTokenByHash(@Param("tokenHash") byte[] tokenHash);

    List<StoredRefreshTokenRow> findRefreshTokenByHashForUpdate(
            @Param("tokenHash") byte[] tokenHash);

    List<StoredLoginDeliveryRow> findLoginDeliveryForUpdate(
            @Param("tenantId") long tenantId,
            @Param("sessionId") long sessionId);

    int confirmLoginDelivery(@Param("tenantId") long tenantId,
                             @Param("sessionId") long sessionId,
                             @Param("now") Instant now);

    List<StoredLoginDeliveryRow> findNextExpiredLoginDeliveryForUpdate(
            @Param("now") Instant now);

    int revokeOrphanSession(@Param("tenantId") long tenantId,
                            @Param("sessionId") long sessionId,
                            @Param("now") Instant now);

    int revokeOrphanSessionRefreshTokens(@Param("tenantId") long tenantId,
                                         @Param("sessionId") long sessionId,
                                         @Param("now") Instant now);

    int markLoginDeliveryReconciled(@Param("tenantId") long tenantId,
                                    @Param("sessionId") long sessionId,
                                    @Param("now") Instant now);

    List<StoredSessionRow> findSessionForUpdate(@Param("tenantId") long tenantId,
                                                @Param("sessionId") long sessionId);

    int rotateRefreshToken(@Param("successorId") long successorId,
                           @Param("now") Instant now,
                           @Param("tokenId") long tokenId,
                           @Param("tenantId") long tenantId);

    int insertSuccessorRefreshToken(@Param("id") long id,
                                    @Param("tenantId") long tenantId,
                                    @Param("userId") long userId,
                                    @Param("sessionId") long sessionId,
                                    @Param("tokenFamilyId") long tokenFamilyId,
                                    @Param("tokenHash") byte[] tokenHash,
                                    @Param("parentTokenId") long parentTokenId,
                                    @Param("now") Instant now,
                                    @Param("expireAt") Instant expireAt);

    int slideSession(@Param("tenantId") long tenantId,
                     @Param("sessionId") long sessionId,
                     @Param("now") Instant now,
                     @Param("idleExpireAt") Instant idleExpireAt);

    int markPresentedTokenReused(@Param("tokenId") long tokenId,
                                 @Param("tenantId") long tenantId,
                                 @Param("now") Instant now);

    int revokeRefreshTokenFamily(@Param("tenantId") long tenantId,
                                 @Param("tokenFamilyId") long tokenFamilyId,
                                 @Param("now") Instant now);

    int revokeSessionForTokenReuse(@Param("tenantId") long tenantId,
                                   @Param("sessionId") long sessionId,
                                   @Param("now") Instant now);

    int updatePasswordCredential(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("passwordPhc") String passwordPhc,
            @Param("expectedPasswordVersion") long expectedPasswordVersion,
            @Param("now") Instant now);

    int advanceSecurityStateForPasswordChange(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("expectedTokenVersion") long expectedTokenVersion,
            @Param("expectedPasswordVersion") long expectedPasswordVersion,
            @Param("now") Instant now);

    int revokeSessionsForPasswordChange(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("sessionIds") List<Long> sessionIds,
            @Param("now") Instant now);

    int revokeSessionRefreshTokensForPasswordChange(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("sessionIds") List<Long> sessionIds,
            @Param("now") Instant now);

    int insertWebAuthnChallenge(
            @Param("challengeId") String challengeId,
            @Param("tenantId") long tenantId,
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("ceremonyType") String ceremonyType,
            @Param("challengeB64") String challengeB64,
            @Param("rpId") String rpId,
            @Param("status") String status,
            @Param("expiresAt") Instant expiresAt,
            @Param("createdAt") Instant createdAt);

    WebAuthnChallengeRow findPendingWebAuthnChallenge(
            @Param("tenantId") long tenantId,
            @Param("challengeId") String challengeId);

    int markWebAuthnChallengeConsumed(
            @Param("tenantId") long tenantId,
            @Param("challengeId") String challengeId,
            @Param("now") Instant now);

    int insertWebAuthnCredential(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("credentialId") String credentialId,
            @Param("publicKeyCose") String publicKeyCose,
            @Param("signCount") long signCount,
            @Param("aaguid") String aaguid,
            @Param("transports") String transports,
            @Param("deviceName") String deviceName,
            @Param("status") String status,
            @Param("createdAt") Instant createdAt,
            @Param("lastUsedAt") Instant lastUsedAt);

    WebAuthnCredentialRow findWebAuthnCredentialByTenantAndCredentialId(
            @Param("tenantId") long tenantId,
            @Param("credentialId") String credentialId);

    List<WebAuthnCredentialRow> findWebAuthnCredentialsByUser(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId);

    int updateWebAuthnCredentialSignCount(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("signCount") long signCount,
            @Param("lastUsedAt") Instant lastUsedAt);

    int revokeWebAuthnCredential(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("id") long id);

    List<ActiveSessionRow> selectSessionsPage(
            @Param("tenantId") long tenantId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countSessions(
            @Param("tenantId") long tenantId,
            @Param("userId") Long userId,
            @Param("status") String status);

    int revokeSessionWithReason(
            @Param("tenantId") long tenantId,
            @Param("sessionId") long sessionId,
            @Param("reason") String reason,
            @Param("now") Instant now);

    int revokeSessionRefreshTokensWithReason(
            @Param("tenantId") long tenantId,
            @Param("sessionId") long sessionId,
            @Param("reason") String reason,
            @Param("now") Instant now);

    record ActiveSessionRow(
            long id,
            long tenantId,
            long sessionId,
            long userId,
            String status,
            String deviceType,
            Instant lastAccessAt,
            Instant idleExpireAt,
            Instant absoluteExpireAt,
            Instant lastStrongAuthAt,
            Instant revokedAt,
            String revokeReason,
            long sessionVersion,
            Instant createdAt,
            Instant updatedAt) {
    }

    record CredentialRow(
            long tenantId,
            long userId,
            String passwordPhc,
            String status,
            Instant lockUntil) {
    }

    record LoginIdentityRow(long tenantId, long userId, String userStatus) {
    }

    record StoredRefreshTokenRow(
            long id,
            long tenantId,
            long userId,
            long sessionId,
            long tokenFamilyId,
            String status,
            Instant expireAt) {
    }

    record StoredSessionRow(
            long userId,
            String status,
            Instant idleExpireAt,
            Instant absoluteExpireAt,
            long sessionVersion) {
    }

    record StoredLoginDeliveryRow(
            long tenantId,
            long userId,
            long sessionId,
            String requestId,
            String deliveryStatus,
            Instant confirmBefore) {
    }

    record LockedSecurityStateRow(long tokenVersion, long passwordVersion) {
    }

    record LockedCredentialRow(
            String passwordPhc,
            long passwordVersion,
            String status) {
    }

    record PasswordChangeSessionRow(
            long sessionId,
            Instant idleExpireAt,
            Instant absoluteExpireAt,
            long sessionVersion) {
    }

    record WebAuthnChallengeRow(
            String challengeId,
            long tenantId,
            Long userId,
            String username,
            String ceremonyType,
            String challengeB64,
            String rpId,
            String status,
            Instant expiresAt,
            Instant createdAt) {
    }

    record WebAuthnCredentialRow(
            long id,
            long tenantId,
            long userId,
            String credentialId,
            String publicKeyCose,
            long signCount,
            String aaguid,
            String transports,
            String deviceName,
            String status,
            Instant createdAt,
            Instant lastUsedAt) {
    }
}
