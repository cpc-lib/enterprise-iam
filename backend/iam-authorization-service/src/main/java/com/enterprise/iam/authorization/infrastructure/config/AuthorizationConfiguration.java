package com.enterprise.iam.authorization.infrastructure.config;

import com.enterprise.iam.authorization.application.port.out.AbacPolicyRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.application.port.out.DataPredicateArtifactRepository;
import com.enterprise.iam.authorization.application.port.out.DataScopeRepository;
import com.enterprise.iam.authorization.application.port.out.FieldPolicyRepository;
import com.enterprise.iam.authorization.application.port.out.FieldReadPlanArtifactRepository;
import com.enterprise.iam.authorization.application.port.out.FieldWritePlanArtifactRepository;
import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.authorization.application.port.out.ResolvedDataScopeRepository;
import com.enterprise.iam.authorization.application.port.out.ResolvedFieldPolicyRepository;
import com.enterprise.iam.authorization.application.port.out.ResolvedMaskStrategyRepository;
import com.enterprise.iam.authorization.application.port.out.ResourceFieldRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionDataScopeRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionFieldPolicyRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionRepository;
import com.enterprise.iam.authorization.application.port.out.ShareAclProjectionRepository;
import com.enterprise.iam.authorization.application.port.out.TeamRolePermissionRepository;
import com.enterprise.iam.authorization.application.service.AbacPolicyUseCase;
import com.enterprise.iam.authorization.application.service.AuthorizationCheckUseCase;
import com.enterprise.iam.authorization.application.service.CreateDataScopeUseCase;
import com.enterprise.iam.authorization.application.service.CreateFieldPolicyUseCase;
import com.enterprise.iam.authorization.application.service.ExplainDecisionUseCase;
import com.enterprise.iam.authorization.application.service.ReplaceResourceFieldsUseCase;
import com.enterprise.iam.authorization.application.service.ReplaceRolePermissionDataScopesUseCase;
import com.enterprise.iam.authorization.application.service.ReplaceRolePermissionFieldPoliciesUseCase;
import com.enterprise.iam.authorization.application.service.ReplaceRolePermissionsUseCase;
import com.enterprise.iam.authorization.application.service.ReplaceTeamRolePermissionsUseCase;
import com.enterprise.iam.authorization.application.service.ResolveEffectivePoliciesUseCase;
import com.enterprise.iam.authorization.domain.service.DefaultAuthorizationEngine;
import com.enterprise.iam.authorization.infrastructure.persistence.InMemoryAuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcAbacPolicyRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcAuthorizationFactRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcDataPredicateArtifactRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcDataScopeRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcFieldPolicyRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcFieldReadPlanArtifactRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcFieldWritePlanArtifactRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcPermissionVersionRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcResolvedDataScopeRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcResolvedFieldPolicyRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcResolvedMaskStrategyRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcResourceFieldRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcRolePermissionDataScopeRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcRolePermissionFieldPolicyRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcRolePermissionRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcTeamRolePermissionRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.TimeOrderedPositiveIdGenerator;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationArtifactMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPersistenceMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import com.enterprise.iam.authorization.interfaces.http.AbacPolicyController;
import com.enterprise.iam.authorization.interfaces.http.AuthorizationCheckController;
import com.enterprise.iam.authorization.interfaces.http.AuthorizationPolicyController;
import com.enterprise.iam.authorization.interfaces.http.DataScopeController;
import com.enterprise.iam.authorization.interfaces.http.FieldPolicyController;
import com.enterprise.iam.authorization.interfaces.http.ResourceFieldController;
import com.enterprise.iam.authorization.interfaces.http.RolePermissionBindingController;
import com.enterprise.iam.authorization.interfaces.http.RolePermissionController;
import com.enterprise.iam.authorization.interfaces.http.TeamRolePermissionController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
@MapperScan(basePackageClasses = AuthorizationPolicyMapper.class)
public class AuthorizationConfiguration {

    @Bean
    public Clock authorizationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PositiveIdGenerator positiveIdGenerator(
            Clock authorizationClock,
            @Value("${iam.authorization.node-id:2}") int nodeId) {
        return new TimeOrderedPositiveIdGenerator(authorizationClock, nodeId);
    }

    @Bean
    public DefaultAuthorizationEngine defaultAuthorizationEngine(
            Clock authorizationClock,
            PositiveIdGenerator idGenerator) {
        return new DefaultAuthorizationEngine(authorizationClock, idGenerator::nextId);
    }

    @Bean
    public AuthorizationFactRepository authorizationFactRepository(AuthorizationPolicyMapper mapper) {
        return new JdbcAuthorizationFactRepository(mapper);
    }

    @Bean
    public AuthorizationDecisionRecordRepository authorizationDecisionRecordRepository() {
        return new InMemoryAuthorizationDecisionRecordRepository();
    }

    @Bean
    public RolePermissionRepository rolePermissionRepository(
            AuthorizationPolicyMapper mapper, PlatformTransactionManager transactionManager) {
        return new JdbcRolePermissionRepository(mapper, transactionManager);
    }

    @Bean
    public TeamRolePermissionRepository teamRolePermissionRepository(
            AuthorizationPolicyMapper mapper, PlatformTransactionManager transactionManager,
            PositiveIdGenerator idGenerator) {
        return new JdbcTeamRolePermissionRepository(mapper, transactionManager, idGenerator);
    }

    @Bean
    public DataScopeRepository dataScopeRepository(AuthorizationPolicyMapper mapper, PositiveIdGenerator idGenerator) {
        return new JdbcDataScopeRepository(mapper, idGenerator);
    }

    @Bean
    public RolePermissionDataScopeRepository rolePermissionDataScopeRepository(
            AuthorizationPolicyMapper mapper, PlatformTransactionManager transactionManager,
            PositiveIdGenerator idGenerator) {
        return new JdbcRolePermissionDataScopeRepository(mapper, transactionManager, idGenerator);
    }

    @Bean
    public ResourceFieldRepository resourceFieldRepository(
            AuthorizationPolicyMapper mapper, PlatformTransactionManager transactionManager,
            PositiveIdGenerator idGenerator) {
        return new JdbcResourceFieldRepository(mapper, transactionManager, idGenerator);
    }

    @Bean
    public FieldPolicyRepository fieldPolicyRepository(AuthorizationPolicyMapper mapper, PositiveIdGenerator idGenerator) {
        return new JdbcFieldPolicyRepository(mapper, idGenerator);
    }

    @Bean
    public RolePermissionFieldPolicyRepository rolePermissionFieldPolicyRepository(
            AuthorizationPolicyMapper mapper, PlatformTransactionManager transactionManager,
            PositiveIdGenerator idGenerator) {
        return new JdbcRolePermissionFieldPolicyRepository(mapper, transactionManager, idGenerator);
    }

    @Bean
    public PermissionVersionRepository permissionVersionRepository(AuthorizationPolicyMapper mapper) {
        return new JdbcPermissionVersionRepository(mapper);
    }

    @Bean
    public AbacPolicyRepository abacPolicyRepository(
            AuthorizationPersistenceMapper mapper, Clock authorizationClock) {
        return new JdbcAbacPolicyRepository(mapper, authorizationClock);
    }

    @Bean
    public AbacPolicyUseCase abacPolicyUseCase(
            AbacPolicyRepository abacPolicyRepository,
            PositiveIdGenerator idGenerator,
            Clock authorizationClock,
            PermissionVersionRepository permissionVersionRepository,
            ObjectProvider<AuthorizationPlanCache> authorizationPlanCache) {
        return new AbacPolicyUseCase(
                abacPolicyRepository,
                idGenerator,
                authorizationClock,
                permissionVersionRepository,
                authorizationPlanCache.getIfAvailable());
    }

    @Bean
    public AuthorizationCheckUseCase authorizationCheckUseCase(
            AuthorizationFactRepository factRepository,
            DefaultAuthorizationEngine engine,
            AuthorizationDecisionRecordRepository decisionRecordRepository,
            Clock authorizationClock,
            ObjectProvider<ShareAclProjectionRepository> shareAclProjectionRepository,
            ObjectProvider<AuthorizationPlanCache> authorizationPlanCache,
            ObjectProvider<ResolvedFieldPolicyRepository> resolvedFieldPolicyRepository,
            ObjectProvider<ResolvedDataScopeRepository> resolvedDataScopeRepository,
            ObjectProvider<ResolvedMaskStrategyRepository> resolvedMaskStrategyRepository,
            ObjectProvider<AbacPolicyRepository> abacPolicyRepository) {
        return new AuthorizationCheckUseCase(
                factRepository,
                engine,
                decisionRecordRepository,
                shareAclProjectionRepository.getIfAvailable(),
                authorizationClock,
                authorizationPlanCache.getIfAvailable(),
                resolvedFieldPolicyRepository.getIfAvailable(),
                resolvedDataScopeRepository.getIfAvailable(),
                resolvedMaskStrategyRepository.getIfAvailable(),
                abacPolicyRepository.getIfAvailable());
    }

    @Bean
    public ResolvedFieldPolicyRepository resolvedFieldPolicyRepository(AuthorizationPolicyMapper mapper) {
        return new JdbcResolvedFieldPolicyRepository(mapper);
    }

    @Bean
    public ResolvedMaskStrategyRepository resolvedMaskStrategyRepository(AuthorizationPolicyMapper mapper) {
        return new JdbcResolvedMaskStrategyRepository(mapper, new ObjectMapper());
    }

    @Bean
    public ResolvedDataScopeRepository resolvedDataScopeRepository(AuthorizationPolicyMapper mapper) {
        return new JdbcResolvedDataScopeRepository(mapper);
    }

    @Bean
    public ExplainDecisionUseCase explainDecisionUseCase(
            AuthorizationDecisionRecordRepository decisionRecordRepository) {
        return new ExplainDecisionUseCase(decisionRecordRepository);
    }

    @Bean
    public ReplaceRolePermissionsUseCase replaceRolePermissionsUseCase(
            RolePermissionRepository rolePermissionRepository,
            PermissionVersionRepository permissionVersionRepository,
            ObjectProvider<AuthorizationPlanCache> authorizationPlanCache) {
        return new ReplaceRolePermissionsUseCase(
                rolePermissionRepository, permissionVersionRepository,
                authorizationPlanCache.getIfAvailable());
    }

    @Bean
    public ReplaceTeamRolePermissionsUseCase replaceTeamRolePermissionsUseCase(
            TeamRolePermissionRepository teamRolePermissionRepository,
            PermissionVersionRepository permissionVersionRepository,
            ObjectProvider<AuthorizationPlanCache> authorizationPlanCache) {
        return new ReplaceTeamRolePermissionsUseCase(
                teamRolePermissionRepository, permissionVersionRepository,
                authorizationPlanCache.getIfAvailable());
    }

    @Bean
    public CreateDataScopeUseCase createDataScopeUseCase(DataScopeRepository dataScopeRepository) {
        return new CreateDataScopeUseCase(dataScopeRepository);
    }

    @Bean
    public ReplaceRolePermissionDataScopesUseCase replaceRolePermissionDataScopesUseCase(
            RolePermissionDataScopeRepository rolePermissionDataScopeRepository,
            PermissionVersionRepository permissionVersionRepository,
            ObjectProvider<AuthorizationPlanCache> authorizationPlanCache) {
        return new ReplaceRolePermissionDataScopesUseCase(
                rolePermissionDataScopeRepository, permissionVersionRepository,
                authorizationPlanCache.getIfAvailable());
    }

    @Bean
    public ReplaceResourceFieldsUseCase replaceResourceFieldsUseCase(
            ResourceFieldRepository resourceFieldRepository) {
        return new ReplaceResourceFieldsUseCase(resourceFieldRepository);
    }

    @Bean
    public CreateFieldPolicyUseCase createFieldPolicyUseCase(FieldPolicyRepository fieldPolicyRepository) {
        return new CreateFieldPolicyUseCase(fieldPolicyRepository);
    }

    @Bean
    public ReplaceRolePermissionFieldPoliciesUseCase replaceRolePermissionFieldPoliciesUseCase(
            RolePermissionFieldPolicyRepository rolePermissionFieldPolicyRepository,
            PermissionVersionRepository permissionVersionRepository,
            ObjectProvider<AuthorizationPlanCache> authorizationPlanCache) {
        return new ReplaceRolePermissionFieldPoliciesUseCase(
                rolePermissionFieldPolicyRepository, permissionVersionRepository,
                authorizationPlanCache.getIfAvailable());
    }

    @Bean
    public ResolveEffectivePoliciesUseCase resolveEffectivePoliciesUseCase(
            AuthorizationCheckUseCase authorizationCheckUseCase,
            ObjectProvider<DataPredicateArtifactRepository> dataPredicateArtifactRepository,
            ObjectProvider<FieldReadPlanArtifactRepository> fieldReadPlanArtifactRepository,
            ObjectProvider<FieldWritePlanArtifactRepository> fieldWritePlanArtifactRepository,
            Clock authorizationClock) {
        return new ResolveEffectivePoliciesUseCase(
                authorizationCheckUseCase,
                dataPredicateArtifactRepository.getIfAvailable(),
                fieldReadPlanArtifactRepository.getIfAvailable(),
                fieldWritePlanArtifactRepository.getIfAvailable(),
                authorizationClock);
    }

    @Bean
    public DataPredicateArtifactRepository dataPredicateArtifactRepository(AuthorizationArtifactMapper mapper) {
        return new JdbcDataPredicateArtifactRepository(mapper, new ObjectMapper());
    }

    @Bean
    public FieldReadPlanArtifactRepository fieldReadPlanArtifactRepository(AuthorizationArtifactMapper mapper) {
        return new JdbcFieldReadPlanArtifactRepository(mapper, new ObjectMapper());
    }

    @Bean
    public FieldWritePlanArtifactRepository fieldWritePlanArtifactRepository(AuthorizationArtifactMapper mapper) {
        return new JdbcFieldWritePlanArtifactRepository(mapper, new ObjectMapper());
    }

    @Bean
    public AuthorizationCheckController authorizationCheckController(
            AuthorizationCheckUseCase authorizationCheckUseCase,
            ExplainDecisionUseCase explainDecisionUseCase) {
        return new AuthorizationCheckController(authorizationCheckUseCase, explainDecisionUseCase);
    }

    @Bean
    public AbacPolicyController abacPolicyController(AbacPolicyUseCase abacPolicyUseCase) {
        return new AbacPolicyController(abacPolicyUseCase);
    }

    @Bean
    public RolePermissionController rolePermissionController(
            ReplaceRolePermissionsUseCase replaceRolePermissionsUseCase) {
        return new RolePermissionController(replaceRolePermissionsUseCase);
    }

    @Bean
    public TeamRolePermissionController teamRolePermissionController(
            ReplaceTeamRolePermissionsUseCase replaceTeamRolePermissionsUseCase) {
        return new TeamRolePermissionController(replaceTeamRolePermissionsUseCase);
    }

    @Bean
    public DataScopeController dataScopeController(CreateDataScopeUseCase createDataScopeUseCase) {
        return new DataScopeController(createDataScopeUseCase);
    }

    @Bean
    public RolePermissionBindingController rolePermissionBindingController(
            ReplaceRolePermissionDataScopesUseCase replaceRolePermissionDataScopesUseCase,
            ReplaceRolePermissionFieldPoliciesUseCase replaceRolePermissionFieldPoliciesUseCase) {
        return new RolePermissionBindingController(
                replaceRolePermissionDataScopesUseCase, replaceRolePermissionFieldPoliciesUseCase);
    }

    @Bean
    public ResourceFieldController resourceFieldController(
            ReplaceResourceFieldsUseCase replaceResourceFieldsUseCase) {
        return new ResourceFieldController(replaceResourceFieldsUseCase);
    }

    @Bean
    public FieldPolicyController fieldPolicyController(CreateFieldPolicyUseCase createFieldPolicyUseCase) {
        return new FieldPolicyController(createFieldPolicyUseCase);
    }

    @Bean
    public AuthorizationPolicyController authorizationPolicyController(
            ResolveEffectivePoliciesUseCase resolveEffectivePoliciesUseCase) {
        return new AuthorizationPolicyController(resolveEffectivePoliciesUseCase);
    }
}
