package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.infrastructure.config.SessionIssuanceProperties;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredLoginDeliveryRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredRefreshTokenRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredSessionRow;
import com.enterprise.iam.auth.infrastructure.security.HmacSha256RefreshTokenHasher;
import com.enterprise.iam.auth.infrastructure.security.RefreshTokenHashKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionalJdbcLoginDeliveryConfirmerTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:30:00Z");
    private static final String RAW_TOKEN = "rt1.key." + "a".repeat(43);

    private AuthPersistenceMapper mapper;
    private TransactionalJdbcLoginDeliveryConfirmer confirmer;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthPersistenceMapper.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        SessionIssuanceProperties properties = enabledProperties();
        HmacSha256RefreshTokenHasher hasher = new HmacSha256RefreshTokenHasher(
                new RefreshTokenHashKey(
                        "key", new SecretKeySpec(new byte[32], "HmacSHA256")));
        confirmer = new TransactionalJdbcLoginDeliveryConfirmer(
                mapper,
                hasher,
                transactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties);
    }

    @Test
    void confirmsMatchingPendingDeliveryBeforeDeadline() {
        stubMatchingRows("PENDING", NOW.plusSeconds(60));
        when(mapper.confirmLoginDelivery(7L, 101L, NOW)).thenReturn(1);

        boolean confirmed;
        try (SensitiveRefreshToken token = token()) {
            confirmed = confirmer.confirm(101L, token);
        }

        assertThat(confirmed).isTrue();
        verify(mapper).confirmLoginDelivery(7L, 101L, NOW);
    }

    @Test
    void repeatedConfirmationOfConfirmedDeliveryIsIdempotent() {
        stubMatchingRows("CONFIRMED", NOW.minusSeconds(1));

        boolean confirmed;
        try (SensitiveRefreshToken token = token()) {
            confirmed = confirmer.confirm(101L, token);
        }

        assertThat(confirmed).isTrue();
        verify(mapper, never()).confirmLoginDelivery(7L, 101L, NOW);
    }

    @Test
    void rejectsPendingDeliveryAtOrAfterFrozenDeadline() {
        stubMatchingRows("PENDING", NOW);

        boolean confirmed;
        try (SensitiveRefreshToken token = token()) {
            confirmed = confirmer.confirm(101L, token);
        }

        assertThat(confirmed).isFalse();
        verify(mapper, never()).confirmLoginDelivery(7L, 101L, NOW);
    }

    private void stubMatchingRows(String deliveryStatus, Instant confirmBefore) {
        StoredRefreshTokenRow token = new StoredRefreshTokenRow(
                201L, 7L, 11L, 101L, 301L, "ACTIVE", NOW.plusSeconds(600));
        when(mapper.findRefreshTokenByHash(any(byte[].class))).thenReturn(List.of(token));
        when(mapper.findLoginDeliveryForUpdate(7L, 101L)).thenReturn(List.of(
                new StoredLoginDeliveryRow(
                        7L, 11L, 101L, "req-1", deliveryStatus, confirmBefore)));
        when(mapper.findSessionForUpdate(7L, 101L)).thenReturn(List.of(
                new StoredSessionRow(
                        11L, "ACTIVE", NOW.plusSeconds(300), NOW.plusSeconds(3600), 1L)));
        when(mapper.findRefreshTokenByHashForUpdate(any(byte[].class)))
                .thenReturn(List.of(token));
    }

    private static SensitiveRefreshToken token() {
        return new SensitiveRefreshToken(RAW_TOKEN.toCharArray());
    }

    private static SessionIssuanceProperties enabledProperties() {
        SessionIssuanceProperties properties = new SessionIssuanceProperties();
        properties.setEnabled(true);
        properties.setNodeId(1);
        return properties;
    }
}
