package com.enterprise.iam.auth.infrastructure.config;

import com.enterprise.iam.auth.application.port.out.AuthenticationAttemptSink;
import com.enterprise.iam.auth.application.port.out.CredentialRepository;
import com.enterprise.iam.auth.application.port.out.LoginIdentityDirectory;
import com.enterprise.iam.auth.application.port.out.LoginSessionIssuer;
import com.enterprise.iam.auth.application.port.out.PasswordVerifier;
import com.enterprise.iam.auth.application.port.out.PasswordHasher;
import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.application.port.out.LoginAuditOutboxAppender;
import com.enterprise.iam.auth.application.port.in.ChangePasswordUseCase;
import com.enterprise.iam.auth.application.port.out.SessionRevoker;
import com.enterprise.iam.auth.application.port.out.WebAuthnRepository;
import com.enterprise.iam.auth.application.service.AuthenticateLoginUseCase;
import com.enterprise.iam.auth.application.service.WebAuthnPasskeyUseCase;
import com.enterprise.iam.auth.infrastructure.persistence.JdbcCredentialRepository;
import com.enterprise.iam.auth.infrastructure.persistence.JdbcLoginIdentityDirectory;
import com.enterprise.iam.auth.infrastructure.persistence.JdbcSessionRevoker;
import com.enterprise.iam.auth.infrastructure.persistence.JdbcWebAuthnRepository;
import com.enterprise.iam.auth.infrastructure.persistence.Slf4jAuthenticationAttemptSink;
import com.enterprise.iam.auth.infrastructure.persistence.TimeOrderedPositiveIdGenerator;
import com.enterprise.iam.auth.infrastructure.persistence.TransactionalJdbcPasswordChanger;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.auth.infrastructure.security.Argon2idPasswordVerifier;
import com.enterprise.iam.auth.infrastructure.security.HmacSha256RefreshTokenHasher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.auth.http", name = "enabled", havingValue = "true")
public class AuthHttpConfiguration {

    @Bean
    public Clock authHttpClock() {
        return Clock.systemUTC();
    }

    @Bean
    public LoginIdentityDirectory loginIdentityDirectory(AuthPersistenceMapper mapper) {
        return new JdbcLoginIdentityDirectory(mapper);
    }

    @Bean
    public CredentialRepository credentialRepository(AuthPersistenceMapper mapper) {
        return new JdbcCredentialRepository(mapper);
    }

    @Bean
    public PasswordHasher passwordVerifier() {
        return new Argon2idPasswordVerifier();
    }

    @Bean
    public AuthenticationAttemptSink authenticationAttemptSink() {
        return new Slf4jAuthenticationAttemptSink();
    }

    @Bean
    public AuthenticateLoginUseCase authenticateLoginUseCase(
            LoginIdentityDirectory identityDirectory,
            CredentialRepository credentialRepository,
            PasswordVerifier passwordVerifier,
            LoginSessionIssuer sessionIssuer,
            AuthenticationAttemptSink attemptSink,
            Clock authHttpClock) {
        return new AuthenticateLoginUseCase(
                identityDirectory, credentialRepository, passwordVerifier, sessionIssuer, attemptSink, authHttpClock);
    }

    @Bean
    public SessionRevoker sessionRevoker(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            Clock authHttpClock) {
        return new JdbcSessionRevoker(mapper, transactionManager, authHttpClock);
    }

    @Bean
    public ChangePasswordUseCase changePasswordUseCase(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            PasswordHasher passwordHasher,
            HmacSha256RefreshTokenHasher refreshTokenHasher,
            PositiveIdGenerator idGenerator,
            SessionProjectionOutboxAppender projectionAppender,
            LoginAuditOutboxAppender auditAppender,
            Clock authHttpClock,
            SessionIssuanceProperties properties) {
        return new TransactionalJdbcPasswordChanger(
                mapper,
                transactionManager,
                passwordHasher,
                refreshTokenHasher,
                idGenerator,
                projectionAppender,
                auditAppender,
                authHttpClock,
                properties);
    }

    @Bean
    @ConditionalOnMissingBean(PositiveIdGenerator.class)
    public PositiveIdGenerator authHttpPositiveIdGenerator(Clock authHttpClock) {
        return new TimeOrderedPositiveIdGenerator(authHttpClock, 1);
    }

    @Bean
    public WebAuthnRepository webAuthnRepository(AuthPersistenceMapper mapper) {
        return new JdbcWebAuthnRepository(mapper);
    }

    @Bean
    public WebAuthnPasskeyUseCase webAuthnPasskeyUseCase(
            WebAuthnRepository webAuthnRepository,
            PositiveIdGenerator idGenerator,
            Clock authHttpClock) {
        return new WebAuthnPasskeyUseCase(webAuthnRepository, idGenerator, authHttpClock);
    }

    @Bean
    public com.enterprise.iam.auth.application.port.out.SessionQueryRepository sessionQueryRepository(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            Clock authHttpClock) {
        return new com.enterprise.iam.auth.infrastructure.persistence.JdbcSessionQueryRepository(
                mapper, transactionManager, authHttpClock);
    }

    @Bean
    public com.enterprise.iam.auth.application.service.SessionQueryUseCase sessionQueryUseCase(
            com.enterprise.iam.auth.application.port.out.SessionQueryRepository sessionQueryRepository) {
        return new com.enterprise.iam.auth.application.service.SessionQueryUseCase(sessionQueryRepository);
    }

    @Bean
    public com.enterprise.iam.auth.interfaces.http.AdminSessionController adminSessionController(
            com.enterprise.iam.auth.application.service.SessionQueryUseCase sessionQueryUseCase) {
        return new com.enterprise.iam.auth.interfaces.http.AdminSessionController(sessionQueryUseCase);
    }
}
