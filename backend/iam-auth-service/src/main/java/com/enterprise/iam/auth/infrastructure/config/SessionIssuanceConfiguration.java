package com.enterprise.iam.auth.infrastructure.config;

import com.enterprise.iam.auth.application.port.out.LoginSessionIssuer;
import com.enterprise.iam.auth.application.port.out.LoginAuditOutboxAppender;
import com.enterprise.iam.auth.application.port.out.LoginDeliveryConfirmer;
import com.enterprise.iam.auth.application.port.out.OrphanLoginSessionReconciler;
import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.auth.application.port.out.RefreshTokenRotator;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.infrastructure.persistence.TimeOrderedPositiveIdGenerator;
import com.enterprise.iam.auth.infrastructure.persistence.ScheduledOrphanLoginSessionReconciler;
import com.enterprise.iam.auth.infrastructure.persistence.TransactionalJdbcLoginDeliveryConfirmer;
import com.enterprise.iam.auth.infrastructure.persistence.TransactionalJdbcLoginSessionIssuer;
import com.enterprise.iam.auth.infrastructure.persistence.TransactionalJdbcOrphanLoginSessionReconciler;
import com.enterprise.iam.auth.infrastructure.persistence.TransactionalJdbcRefreshTokenRotator;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.auth.infrastructure.security.HmacSha256RefreshTokenHasher;
import com.enterprise.iam.auth.infrastructure.security.RefreshTokenHashKey;
import com.enterprise.iam.auth.infrastructure.security.SecureOpaqueRefreshTokenGenerator;
import com.enterprise.iam.common.security.access.AccessTokenSigner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SessionIssuanceProperties.class)
@ConditionalOnProperty(
        prefix = "iam.auth.session-issuance",
        name = "enabled",
        havingValue = "true")
public class SessionIssuanceConfiguration {

    @Bean("iamSessionIssuanceClock")
    @ConditionalOnMissingBean(name = "iamSessionIssuanceClock")
    Clock iamSessionIssuanceClock() {
        return Clock.systemUTC();
    }

    @Bean("iamSessionSecureRandom")
    @ConditionalOnMissingBean(name = "iamSessionSecureRandom")
    SecureRandom iamSessionSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    @ConditionalOnMissingBean(PositiveIdGenerator.class)
    PositiveIdGenerator positiveIdGenerator(
            @Qualifier("iamSessionIssuanceClock") Clock clock,
            SessionIssuanceProperties properties) {
        properties.validateEnabled();
        return new TimeOrderedPositiveIdGenerator(clock, properties.getNodeId());
    }

    @Bean
    @ConditionalOnMissingBean
    SecureOpaqueRefreshTokenGenerator secureOpaqueRefreshTokenGenerator(
            @Qualifier("iamSessionSecureRandom") SecureRandom secureRandom,
            RefreshTokenHashKey hashKey) {
        return new SecureOpaqueRefreshTokenGenerator(secureRandom, hashKey);
    }

    @Bean
    @ConditionalOnMissingBean
    HmacSha256RefreshTokenHasher hmacSha256RefreshTokenHasher(
            RefreshTokenHashKey hashKey) {
        return new HmacSha256RefreshTokenHasher(hashKey);
    }

    @Bean
    @ConditionalOnMissingBean(LoginSessionIssuer.class)
    LoginSessionIssuer loginSessionIssuer(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            AccessTokenSigner accessTokenSigner,
            PositiveIdGenerator idGenerator,
            SecureOpaqueRefreshTokenGenerator refreshTokenGenerator,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            SessionProjectionOutboxAppender projectionAppender,
            LoginAuditOutboxAppender auditAppender,
            @Qualifier("iamSessionIssuanceClock") Clock clock,
            SessionIssuanceProperties properties) {
        properties.validateEnabled();
        return new TransactionalJdbcLoginSessionIssuer(
                mapper,
                transactionManager,
                accessTokenSigner,
                idGenerator,
                refreshTokenGenerator,
                refreshTokenHasher,
                projectionAppender,
                auditAppender,
                clock,
                properties);
    }

    @Bean
    @ConditionalOnMissingBean(LoginDeliveryConfirmer.class)
    LoginDeliveryConfirmer loginDeliveryConfirmer(
            AuthPersistenceMapper mapper,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            PlatformTransactionManager transactionManager,
            @Qualifier("iamSessionIssuanceClock") Clock clock,
            SessionIssuanceProperties properties) {
        return new TransactionalJdbcLoginDeliveryConfirmer(
                mapper, refreshTokenHasher, transactionManager, clock, properties);
    }

    @Bean
    @ConditionalOnMissingBean(OrphanLoginSessionReconciler.class)
    OrphanLoginSessionReconciler orphanLoginSessionReconciler(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            PositiveIdGenerator idGenerator,
            SessionProjectionOutboxAppender projectionAppender,
            LoginAuditOutboxAppender auditAppender,
            @Qualifier("iamSessionIssuanceClock") Clock clock,
            SessionIssuanceProperties properties) {
        return new TransactionalJdbcOrphanLoginSessionReconciler(
                mapper,
                transactionManager,
                idGenerator,
                projectionAppender,
                auditAppender,
                clock,
                properties);
    }

    @Bean
    @ConditionalOnMissingBean
    ScheduledOrphanLoginSessionReconciler scheduledOrphanLoginSessionReconciler(
            OrphanLoginSessionReconciler reconciler) {
        return new ScheduledOrphanLoginSessionReconciler(reconciler);
    }

    @Bean
    @ConditionalOnMissingBean(RefreshTokenRotator.class)
    RefreshTokenRotator refreshTokenRotator(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            AccessTokenSigner accessTokenSigner,
            PositiveIdGenerator idGenerator,
            SecureOpaqueRefreshTokenGenerator refreshTokenGenerator,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            SessionProjectionOutboxAppender projectionAppender,
            @Qualifier("iamSessionIssuanceClock") Clock clock,
            SessionIssuanceProperties properties) {
        properties.validateEnabled();
        return new TransactionalJdbcRefreshTokenRotator(
                mapper,
                transactionManager,
                accessTokenSigner,
                idGenerator,
                refreshTokenGenerator,
                refreshTokenHasher,
                projectionAppender,
                clock,
                properties);
    }
}
