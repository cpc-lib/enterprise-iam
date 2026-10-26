package com.enterprise.iam.sharing.infrastructure.config;

import com.enterprise.iam.common.transaction.SeataTransactionProperties;
import com.enterprise.iam.sharing.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.sharing.application.port.out.ShareAclEventAppender;
import com.enterprise.iam.sharing.application.port.out.ShareAclRepository;
import com.enterprise.iam.sharing.application.port.out.ShareProjectionEpochStore;
import com.enterprise.iam.sharing.application.port.out.ShareRepository;
import com.enterprise.iam.sharing.application.port.out.ShareRevocationSecurityCoordinator;
import com.enterprise.iam.sharing.application.service.CreateShareUseCase;
import com.enterprise.iam.sharing.application.service.GetShareUseCase;
import com.enterprise.iam.sharing.application.service.ReshareUseCase;
import com.enterprise.iam.sharing.application.service.RevokeShareUseCase;
import com.enterprise.iam.sharing.infrastructure.persistence.JdbcShareAclRepository;
import com.enterprise.iam.sharing.infrastructure.persistence.JdbcShareRepository;
import com.enterprise.iam.sharing.infrastructure.persistence.TimeOrderedPositiveIdGenerator;
import com.enterprise.iam.sharing.infrastructure.persistence.mapper.SharingPersistenceMapper;
import com.enterprise.iam.sharing.infrastructure.seata.AuthorizationSecurityEpochSynchronizer;
import com.enterprise.iam.sharing.infrastructure.seata.JdbcAuthorizationSecurityEpochSynchronizer;
import com.enterprise.iam.sharing.infrastructure.seata.NoopShareRevocationSecurityCoordinator;
import com.enterprise.iam.sharing.infrastructure.seata.SeataShareRevocationSecurityCoordinator;
import com.enterprise.iam.sharing.interfaces.rest.ShareController;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.sharing", name = "enabled", havingValue = "true")
@MapperScan(basePackageClasses = SharingPersistenceMapper.class)
public class SharingConfiguration {

    @Bean
    public Clock sharingClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PositiveIdGenerator positiveIdGenerator(
            Clock sharingClock,
            @Value("${iam.sharing.node-id:1}") int nodeId) {
        return new TimeOrderedPositiveIdGenerator(sharingClock, nodeId);
    }

    @Bean
    public ShareRepository shareRepository(SharingPersistenceMapper mapper) {
        return new JdbcShareRepository(mapper);
    }

    @Bean
    public ShareAclRepository shareAclRepository(
            SharingPersistenceMapper mapper, PositiveIdGenerator positiveIdGenerator) {
        return new JdbcShareAclRepository(mapper, positiveIdGenerator);
    }

    @Bean
    public CreateShareUseCase createShareUseCase(
            ShareRepository shareRepository,
            ShareAclRepository shareAclRepository,
            PositiveIdGenerator idGenerator,
            Clock sharingClock,
            ObjectProvider<ShareAclEventAppender> eventAppender,
            ObjectProvider<ShareProjectionEpochStore> epochStore) {
        return new CreateShareUseCase(
                shareRepository,
                shareAclRepository,
                idGenerator,
                sharingClock,
                eventAppender.getIfAvailable(),
                epochStore.getIfAvailable());
    }

    @Bean
    public SeataTransactionProperties seataTransactionProperties(
            @Value("${iam.sharing.seata.enabled:false}") boolean enabled,
            @Value("${iam.sharing.seata.mode:AT}") String mode,
            @Value("${iam.sharing.seata.require-global-tx:false}") boolean requireGlobalTx,
            @Value("${spring.application.name:iam-sharing-service}") String applicationId,
            @Value("${iam.sharing.seata.tx-service-group:default_tx_group}") String txServiceGroup) {
        return new SeataTransactionProperties(enabled, mode, requireGlobalTx, applicationId, txServiceGroup);
    }

    @Bean
    @ConditionalOnProperty(prefix = "iam.sharing.seata", name = "enabled", havingValue = "true")
    public AuthorizationSecurityEpochSynchronizer authorizationSecurityEpochSynchronizer(
            SharingPersistenceMapper mapper) {
        return new JdbcAuthorizationSecurityEpochSynchronizer(mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "iam.sharing.seata", name = "enabled", havingValue = "true")
    public ShareRevocationSecurityCoordinator seataShareRevocationSecurityCoordinator(
            AuthorizationSecurityEpochSynchronizer epochSynchronizer,
            SeataTransactionProperties seataProperties,
            Clock sharingClock) {
        return new SeataShareRevocationSecurityCoordinator(epochSynchronizer, seataProperties, sharingClock);
    }

    @Bean
    @ConditionalOnMissingBean(ShareRevocationSecurityCoordinator.class)
    public ShareRevocationSecurityCoordinator noopShareRevocationSecurityCoordinator() {
        return new NoopShareRevocationSecurityCoordinator();
    }

    @Bean
    public RevokeShareUseCase revokeShareUseCase(
            ShareRepository shareRepository,
            ShareAclRepository shareAclRepository,
            ObjectProvider<ShareProjectionEpochStore> epochStore,
            ObjectProvider<ShareAclEventAppender> eventAppender,
            ShareRevocationSecurityCoordinator revocationSecurityCoordinator,
            Clock sharingClock) {
        return new RevokeShareUseCase(
                shareRepository,
                shareAclRepository,
                epochStore.getIfAvailable(),
                eventAppender.getIfAvailable(),
                revocationSecurityCoordinator,
                sharingClock);
    }

    @Bean
    public ReshareUseCase reshareUseCase(
            ShareRepository shareRepository,
            ShareAclRepository shareAclRepository,
            CreateShareUseCase createShareUseCase,
            Clock sharingClock) {
        return new ReshareUseCase(shareRepository, shareAclRepository, createShareUseCase, sharingClock);
    }

    @Bean
    public GetShareUseCase getShareUseCase(
            ShareRepository shareRepository,
            ShareAclRepository shareAclRepository) {
        return new GetShareUseCase(shareRepository, shareAclRepository);
    }

    @Bean
    public ShareController shareController(
            CreateShareUseCase createShareUseCase,
            RevokeShareUseCase revokeShareUseCase,
            ReshareUseCase reshareUseCase,
            GetShareUseCase getShareUseCase) {
        return new ShareController(createShareUseCase, revokeShareUseCase, reshareUseCase, getShareUseCase);
    }
}
