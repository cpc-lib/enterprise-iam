package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.application.port.out.LoginDeliveryConfirmer;
import com.enterprise.iam.auth.infrastructure.config.SessionIssuanceProperties;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredLoginDeliveryRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredRefreshTokenRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredSessionRow;
import com.enterprise.iam.auth.infrastructure.security.HmacSha256RefreshTokenHasher;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Confirms a login delivery only when the response session and refresh cookie still match. */
public final class TransactionalJdbcLoginDeliveryConfirmer implements LoginDeliveryConfirmer {

    private final AuthPersistenceMapper mapper;
    private final HmacSha256RefreshTokenHasher refreshTokenHasher;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public TransactionalJdbcLoginDeliveryConfirmer(
            AuthPersistenceMapper mapper,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            PlatformTransactionManager transactionManager,
            Clock clock,
            SessionIssuanceProperties properties) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.refreshTokenHasher = Objects.requireNonNull(
                refreshTokenHasher, "refreshTokenHasher must not be null");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(properties, "properties must not be null").validateEnabled();
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(Math.toIntExact(properties.getTransactionTimeout().toSeconds()));
    }

    @Override
    public boolean confirm(long sessionId, SensitiveRefreshToken presentedRefreshToken) {
        if (sessionId <= 0) {
            throw new IllegalArgumentException("sessionId must be positive");
        }
        Objects.requireNonNull(presentedRefreshToken, "presentedRefreshToken must not be null");
        byte[] tokenHash = refreshTokenHasher.hash(presentedRefreshToken);
        try {
            StoredRefreshTokenRow located = locateActiveToken(tokenHash, sessionId);
            if (located == null) {
                return false;
            }
            try {
                return confirmInTransaction(located, sessionId, tokenHash);
            } catch (TransientDataAccessException firstFailure) {
                return confirmInTransaction(located, sessionId, tokenHash);
            }
        } finally {
            Arrays.fill(tokenHash, (byte) 0);
        }
    }

    private boolean confirmInTransaction(
            StoredRefreshTokenRow located,
            long sessionId,
            byte[] tokenHash) {
        Boolean confirmed = transaction.execute(status -> {
            StoredLoginDeliveryRow delivery = uniqueDelivery(
                    mapper.findLoginDeliveryForUpdate(located.tenantId(), sessionId));
            if (delivery == null
                    || delivery.userId() != located.userId()
                    || "RECONCILED".equals(delivery.deliveryStatus())) {
                return false;
            }

            StoredSessionRow session = uniqueSession(
                    mapper.findSessionForUpdate(located.tenantId(), sessionId));
            if (session == null
                    || session.userId() != located.userId()
                    || !"ACTIVE".equals(session.status())) {
                return false;
            }

            StoredRefreshTokenRow locked = uniqueToken(
                    mapper.findRefreshTokenByHashForUpdate(tokenHash));
            Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());
            if (!matchesActiveToken(locked, located, sessionId, now)) {
                return false;
            }
            if ("CONFIRMED".equals(delivery.deliveryStatus())) {
                return true;
            }
            return "PENDING".equals(delivery.deliveryStatus())
                    && delivery.confirmBefore().isAfter(now)
                    && mapper.confirmLoginDelivery(located.tenantId(), sessionId, now) == 1;
        });
        return Boolean.TRUE.equals(confirmed);
    }

    private StoredRefreshTokenRow locateActiveToken(byte[] tokenHash, long sessionId) {
        StoredRefreshTokenRow token = uniqueToken(mapper.findRefreshTokenByHash(tokenHash));
        Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        return token != null
                && token.sessionId() == sessionId
                && "ACTIVE".equals(token.status())
                && token.expireAt().isAfter(now)
                ? token
                : null;
    }

    private static boolean matchesActiveToken(
            StoredRefreshTokenRow locked,
            StoredRefreshTokenRow located,
            long sessionId,
            Instant now) {
        return locked != null
                && locked.id() == located.id()
                && locked.tenantId() == located.tenantId()
                && locked.userId() == located.userId()
                && locked.sessionId() == sessionId
                && "ACTIVE".equals(locked.status())
                && locked.expireAt().isAfter(now);
    }

    private static StoredRefreshTokenRow uniqueToken(List<StoredRefreshTokenRow> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("refresh token hash is not unique");
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static StoredLoginDeliveryRow uniqueDelivery(List<StoredLoginDeliveryRow> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("login delivery is not unique");
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static StoredSessionRow uniqueSession(List<StoredSessionRow> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("login session is not unique");
        }
        return rows.isEmpty() ? null : rows.get(0);
    }
}
