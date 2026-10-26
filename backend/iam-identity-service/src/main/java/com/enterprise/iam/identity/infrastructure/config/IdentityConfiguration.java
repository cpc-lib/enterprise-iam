package com.enterprise.iam.identity.infrastructure.config;

import com.enterprise.iam.identity.application.port.out.DictDataRepository;
import com.enterprise.iam.identity.application.port.out.DictTypeRepository;
import com.enterprise.iam.identity.application.port.out.MenuRepository;
import com.enterprise.iam.identity.application.port.out.PermissionApprovalRepository;
import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.ResourceShareExecutionPort;
import com.enterprise.iam.identity.application.port.out.RoleRepository;
import com.enterprise.iam.identity.application.port.out.SodPolicyRepository;
import com.enterprise.iam.identity.application.port.out.TeamRoleAssignmentPort;
import com.enterprise.iam.identity.application.port.out.TenantRepository;
import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.application.port.out.UserRoleRepository;
import com.enterprise.iam.identity.application.port.out.UserSessionRevocationPort;
import com.enterprise.iam.identity.application.service.BindUserRoleUseCase;
import com.enterprise.iam.identity.application.service.CreateMenuUseCase;
import com.enterprise.iam.identity.application.service.CreateRoleUseCase;
import com.enterprise.iam.identity.application.service.CreateUserUseCase;
import com.enterprise.iam.identity.application.service.DeleteMenuUseCase;
import com.enterprise.iam.identity.application.service.DeleteRoleUseCase;
import com.enterprise.iam.identity.application.service.DictDataUseCase;
import com.enterprise.iam.identity.application.service.DictTypeUseCase;
import com.enterprise.iam.identity.application.service.DisableMenuUseCase;
import com.enterprise.iam.identity.application.service.DisableRoleUseCase;
import com.enterprise.iam.identity.application.service.DisableTenantUseCase;
import com.enterprise.iam.identity.application.service.DisableUserUseCase;
import com.enterprise.iam.identity.application.service.EnableMenuUseCase;
import com.enterprise.iam.identity.application.service.EnableRoleUseCase;
import com.enterprise.iam.identity.application.service.EnableTenantUseCase;
import com.enterprise.iam.identity.application.service.EnableUserUseCase;
import com.enterprise.iam.identity.application.service.GetFieldSchemaUseCase;
import com.enterprise.iam.identity.application.service.GetMenuDetailUseCase;
import com.enterprise.iam.identity.application.service.GetNavigationSchemaUseCase;
import com.enterprise.iam.identity.application.service.GetPageSchemaUseCase;
import com.enterprise.iam.identity.application.service.GetRoleMenusUseCase;
import com.enterprise.iam.identity.application.service.GetRoleUsersUseCase;
import com.enterprise.iam.identity.application.service.GetUserDetailUseCase;
import com.enterprise.iam.identity.application.service.KickUserSessionsUseCase;
import com.enterprise.iam.identity.application.service.ListMenusUseCase;
import com.enterprise.iam.identity.application.service.ListRolesUseCase;
import com.enterprise.iam.identity.application.service.ListTenantsUseCase;
import com.enterprise.iam.identity.application.service.ListUsersUseCase;
import com.enterprise.iam.identity.application.service.PermissionApprovalUseCase;
import com.enterprise.iam.identity.application.service.PublishMenuUseCase;
import com.enterprise.iam.identity.application.service.ReorderMenusUseCase;
import com.enterprise.iam.identity.application.service.ResetUserPasswordUseCase;
import com.enterprise.iam.identity.application.service.SodPolicyUseCase;
import com.enterprise.iam.identity.application.service.TenantBootstrapUseCase;
import com.enterprise.iam.identity.application.service.UnbindUserRoleUseCase;
import com.enterprise.iam.identity.application.service.UpdateMenuUseCase;
import com.enterprise.iam.identity.application.service.UpdateRoleMenusUseCase;
import com.enterprise.iam.identity.application.service.UpdateTenantUseCase;
import com.enterprise.iam.identity.application.service.UpdateUserUseCase;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcDictDataRepository;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcDictTypeRepository;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcMenuRepository;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcPermissionApprovalRepository;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcResourceShareExecutionAdapter;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcRoleRepository;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcSodPolicyRepository;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcTeamRoleAssignmentAdapter;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcTenantRepository;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcUserRepository;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcUserRoleRepository;
import com.enterprise.iam.identity.infrastructure.persistence.JdbcUserSessionRevocationAdapter;
import com.enterprise.iam.identity.infrastructure.persistence.TimeOrderedPositiveIdGenerator;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import com.enterprise.iam.identity.interfaces.http.DictDataController;
import com.enterprise.iam.identity.interfaces.http.DictTypeController;
import com.enterprise.iam.identity.interfaces.http.MenuController;
import com.enterprise.iam.identity.interfaces.http.NavigationController;
import com.enterprise.iam.identity.interfaces.http.PermissionApprovalController;
import com.enterprise.iam.identity.interfaces.http.RoleController;
import com.enterprise.iam.identity.interfaces.http.SodPolicyController;
import com.enterprise.iam.identity.interfaces.http.TenantController;
import com.enterprise.iam.identity.interfaces.http.UserController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.identity", name = "enabled", havingValue = "true")
public class IdentityConfiguration {

    @Bean
    public Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PositiveIdGenerator positiveIdGenerator(
            Clock identityClock,
            @Value("${iam.identity.node-id:1}") int nodeId) {
        return new TimeOrderedPositiveIdGenerator(identityClock, nodeId);
    }

    @Bean
    public TenantRepository tenantRepository(IdentityPersistenceMapper mapper) {
        return new JdbcTenantRepository(mapper);
    }

    @Bean
    public UserRepository userRepository(IdentityPersistenceMapper mapper, PositiveIdGenerator positiveIdGenerator) {
        return new JdbcUserRepository(mapper, positiveIdGenerator);
    }

    @Bean
    public RoleRepository roleRepository(IdentityPersistenceMapper mapper, PositiveIdGenerator positiveIdGenerator) {
        return new JdbcRoleRepository(mapper, positiveIdGenerator);
    }

    @Bean
    public UserRoleRepository userRoleRepository(IdentityPersistenceMapper mapper) {
        return new JdbcUserRoleRepository(mapper);
    }

    @Bean
    public SodPolicyRepository sodPolicyRepository(IdentityPersistenceMapper mapper) {
        return new JdbcSodPolicyRepository(mapper);
    }

    @Bean
    public PermissionApprovalRepository permissionApprovalRepository(IdentityPersistenceMapper mapper) {
        return new JdbcPermissionApprovalRepository(mapper);
    }

    @Bean
    public MenuRepository menuRepository(IdentityPersistenceMapper mapper) {
        return new JdbcMenuRepository(mapper);
    }

    @Bean
    public UserSessionRevocationPort userSessionRevocationPort(IdentityPersistenceMapper mapper) {
        return new JdbcUserSessionRevocationAdapter(mapper);
    }

    @Bean
    public TeamRoleAssignmentPort teamRoleAssignmentPort(
            IdentityPersistenceMapper mapper,
            PositiveIdGenerator idGenerator,
            Clock identityClock) {
        return new JdbcTeamRoleAssignmentAdapter(mapper, idGenerator, identityClock);
    }

    @Bean
    public ResourceShareExecutionPort resourceShareExecutionPort(
            IdentityPersistenceMapper mapper,
            PositiveIdGenerator idGenerator,
            Clock identityClock) {
        return new JdbcResourceShareExecutionAdapter(mapper, idGenerator, identityClock);
    }

    @Bean
    public SodPolicyUseCase sodPolicyUseCase(
            SodPolicyRepository sodPolicyRepository,
            PositiveIdGenerator idGenerator,
            Clock identityClock) {
        return new SodPolicyUseCase(sodPolicyRepository, idGenerator, identityClock);
    }

    @Bean
    public PermissionApprovalUseCase permissionApprovalUseCase(
            PermissionApprovalRepository permissionApprovalRepository,
            UserRoleRepository userRoleRepository,
            SodPolicyUseCase sodPolicyUseCase,
            PositiveIdGenerator idGenerator,
            Clock identityClock,
            TeamRoleAssignmentPort teamRoleAssignmentPort,
            ResourceShareExecutionPort resourceShareExecutionPort) {
        return new PermissionApprovalUseCase(
                permissionApprovalRepository,
                userRoleRepository,
                sodPolicyUseCase,
                idGenerator,
                identityClock,
                teamRoleAssignmentPort,
                resourceShareExecutionPort);
    }

    @Bean
    public TenantBootstrapUseCase tenantBootstrapUseCase(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            MenuRepository menuRepository,
            PositiveIdGenerator idGenerator,
            Clock identityClock) {
        return new TenantBootstrapUseCase(
                tenantRepository, userRepository, roleRepository, userRoleRepository, menuRepository, idGenerator, identityClock);
    }

    @Bean
    public CreateUserUseCase createUserUseCase(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            PositiveIdGenerator idGenerator,
            Clock identityClock) {
        return new CreateUserUseCase(userRepository, tenantRepository, idGenerator, identityClock);
    }

    @Bean
    public UpdateUserUseCase updateUserUseCase(UserRepository userRepository) {
        return new UpdateUserUseCase(userRepository);
    }

    @Bean
    public EnableUserUseCase enableUserUseCase(UserRepository userRepository) {
        return new EnableUserUseCase(userRepository);
    }

    @Bean
    public DisableUserUseCase disableUserUseCase(UserRepository userRepository) {
        return new DisableUserUseCase(userRepository);
    }

    @Bean
    public ListUsersUseCase listUsersUseCase(UserRepository userRepository) {
        return new ListUsersUseCase(userRepository);
    }

    @Bean
    public GetUserDetailUseCase getUserDetailUseCase(UserRepository userRepository) {
        return new GetUserDetailUseCase(userRepository);
    }

    @Bean
    public KickUserSessionsUseCase kickUserSessionsUseCase(
            UserRepository userRepository,
            UserSessionRevocationPort userSessionRevocationPort) {
        return new KickUserSessionsUseCase(userRepository, userSessionRevocationPort);
    }

    @Bean
    public CreateRoleUseCase createRoleUseCase(
            RoleRepository roleRepository,
            TenantRepository tenantRepository,
            PositiveIdGenerator idGenerator,
            Clock identityClock) {
        return new CreateRoleUseCase(roleRepository, tenantRepository, idGenerator, identityClock);
    }

    @Bean
    public BindUserRoleUseCase bindUserRoleUseCase(
            UserRoleRepository userRoleRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PositiveIdGenerator idGenerator,
            Clock identityClock,
            SodPolicyUseCase sodPolicyUseCase) {
        return new BindUserRoleUseCase(
                userRoleRepository, userRepository, roleRepository, idGenerator, identityClock, sodPolicyUseCase);
    }

    @Bean
    public CreateMenuUseCase createMenuUseCase(
            MenuRepository menuRepository,
            PositiveIdGenerator idGenerator,
            Clock identityClock) {
        return new CreateMenuUseCase(menuRepository, idGenerator, identityClock);
    }

    @Bean
    public UpdateMenuUseCase updateMenuUseCase(MenuRepository menuRepository) {
        return new UpdateMenuUseCase(menuRepository);
    }

    @Bean
    public EnableMenuUseCase enableMenuUseCase(MenuRepository menuRepository) {
        return new EnableMenuUseCase(menuRepository);
    }

    @Bean
    public DisableMenuUseCase disableMenuUseCase(MenuRepository menuRepository) {
        return new DisableMenuUseCase(menuRepository);
    }

    @Bean
    public PublishMenuUseCase publishMenuUseCase(MenuRepository menuRepository) {
        return new PublishMenuUseCase(menuRepository);
    }

    @Bean
    public DeleteMenuUseCase deleteMenuUseCase(MenuRepository menuRepository) {
        return new DeleteMenuUseCase(menuRepository);
    }

    @Bean
    public ListMenusUseCase listMenusUseCase(MenuRepository menuRepository) {
        return new ListMenusUseCase(menuRepository);
    }

    @Bean
    public GetMenuDetailUseCase getMenuDetailUseCase(MenuRepository menuRepository) {
        return new GetMenuDetailUseCase(menuRepository);
    }

    @Bean
    public ReorderMenusUseCase reorderMenusUseCase(MenuRepository menuRepository) {
        return new ReorderMenusUseCase(menuRepository);
    }

    @Bean
    public ListTenantsUseCase listTenantsUseCase(TenantRepository tenantRepository) {
        return new ListTenantsUseCase(tenantRepository);
    }

    @Bean
    public EnableTenantUseCase enableTenantUseCase(TenantRepository tenantRepository) {
        return new EnableTenantUseCase(tenantRepository);
    }

    @Bean
    public DisableTenantUseCase disableTenantUseCase(TenantRepository tenantRepository) {
        return new DisableTenantUseCase(tenantRepository);
    }

    @Bean
    public UpdateTenantUseCase updateTenantUseCase(TenantRepository tenantRepository) {
        return new UpdateTenantUseCase(tenantRepository);
    }

    @Bean
    public TenantController tenantController(
            TenantBootstrapUseCase tenantBootstrapUseCase,
            ListTenantsUseCase listTenantsUseCase,
            EnableTenantUseCase enableTenantUseCase,
            DisableTenantUseCase disableTenantUseCase,
            UpdateTenantUseCase updateTenantUseCase,
            TenantRepository tenantRepository) {
        return new TenantController(
                tenantBootstrapUseCase,
                listTenantsUseCase,
                enableTenantUseCase,
                disableTenantUseCase,
                updateTenantUseCase,
                tenantRepository);
    }

    @Bean
    public ResetUserPasswordUseCase resetUserPasswordUseCase(UserRepository userRepository) {
        return new ResetUserPasswordUseCase(userRepository);
    }

    @Bean
    public UserController userController(
            CreateUserUseCase createUserUseCase,
            UpdateUserUseCase updateUserUseCase,
            DisableUserUseCase disableUserUseCase,
            EnableUserUseCase enableUserUseCase,
            ListUsersUseCase listUsersUseCase,
            GetUserDetailUseCase getUserDetailUseCase,
            KickUserSessionsUseCase kickUserSessionsUseCase,
            ResetUserPasswordUseCase resetUserPasswordUseCase,
            UserRepository userRepository) {
        return new UserController(
            createUserUseCase,
            updateUserUseCase,
            disableUserUseCase,
            enableUserUseCase,
            listUsersUseCase,
            getUserDetailUseCase,
            kickUserSessionsUseCase,
            resetUserPasswordUseCase,
            userRepository);
    }

    @Bean
    public ListRolesUseCase listRolesUseCase(RoleRepository roleRepository) {
        return new ListRolesUseCase(roleRepository);
    }

    @Bean
    public GetRoleMenusUseCase getRoleMenusUseCase(RoleRepository roleRepository) {
        return new GetRoleMenusUseCase(roleRepository);
    }

    @Bean
    public UpdateRoleMenusUseCase updateRoleMenusUseCase(RoleRepository roleRepository) {
        return new UpdateRoleMenusUseCase(roleRepository);
    }

    @Bean
    public EnableRoleUseCase enableRoleUseCase(RoleRepository roleRepository) {
        return new EnableRoleUseCase(roleRepository);
    }

    @Bean
    public DisableRoleUseCase disableRoleUseCase(RoleRepository roleRepository) {
        return new DisableRoleUseCase(roleRepository);
    }

    @Bean
    public DeleteRoleUseCase deleteRoleUseCase(RoleRepository roleRepository) {
        return new DeleteRoleUseCase(roleRepository);
    }

    @Bean
    public GetRoleUsersUseCase getRoleUsersUseCase(UserRepository userRepository) {
        return new GetRoleUsersUseCase(userRepository);
    }

    @Bean
    public UnbindUserRoleUseCase unbindUserRoleUseCase(
            UserRoleRepository userRoleRepository,
            UserRepository userRepository,
            RoleRepository roleRepository) {
        return new UnbindUserRoleUseCase(userRoleRepository, userRepository, roleRepository);
    }

    @Bean
    public RoleController roleController(
            CreateRoleUseCase createRoleUseCase,
            BindUserRoleUseCase bindUserRoleUseCase,
            ListRolesUseCase listRolesUseCase,
            GetRoleMenusUseCase getRoleMenusUseCase,
            UpdateRoleMenusUseCase updateRoleMenusUseCase,
            EnableRoleUseCase enableRoleUseCase,
            DisableRoleUseCase disableRoleUseCase,
            DeleteRoleUseCase deleteRoleUseCase,
            GetRoleUsersUseCase getRoleUsersUseCase,
            UnbindUserRoleUseCase unbindUserRoleUseCase) {
        return new RoleController(
                createRoleUseCase,
                bindUserRoleUseCase,
                listRolesUseCase,
                getRoleMenusUseCase,
                updateRoleMenusUseCase,
                enableRoleUseCase,
                disableRoleUseCase,
                deleteRoleUseCase,
                getRoleUsersUseCase,
                unbindUserRoleUseCase);
    }

    @Bean
    public SodPolicyController sodPolicyController(SodPolicyUseCase sodPolicyUseCase) {
        return new SodPolicyController(sodPolicyUseCase);
    }

    @Bean
    public PermissionApprovalController permissionApprovalController(PermissionApprovalUseCase permissionApprovalUseCase) {
        return new PermissionApprovalController(permissionApprovalUseCase);
    }

    @Bean
    public MenuController menuController(
            CreateMenuUseCase createMenuUseCase,
            UpdateMenuUseCase updateMenuUseCase,
            EnableMenuUseCase enableMenuUseCase,
            DisableMenuUseCase disableMenuUseCase,
            PublishMenuUseCase publishMenuUseCase,
            DeleteMenuUseCase deleteMenuUseCase,
            ListMenusUseCase listMenusUseCase,
            GetMenuDetailUseCase getMenuDetailUseCase,
            ReorderMenusUseCase reorderMenusUseCase) {
        return new MenuController(
                createMenuUseCase,
                updateMenuUseCase,
                enableMenuUseCase,
                disableMenuUseCase,
                publishMenuUseCase,
                deleteMenuUseCase,
                listMenusUseCase,
                getMenuDetailUseCase,
                reorderMenusUseCase);
    }

    @Bean
    public GetNavigationSchemaUseCase getNavigationSchemaUseCase(
            MenuRepository menuRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository) {
        return new GetNavigationSchemaUseCase(menuRepository, userRoleRepository, roleRepository);
    }

    @Bean
    public GetPageSchemaUseCase getPageSchemaUseCase() {
        return new GetPageSchemaUseCase();
    }

    @Bean
    public GetFieldSchemaUseCase getFieldSchemaUseCase() {
        return new GetFieldSchemaUseCase();
    }

    @Bean
    public NavigationController navigationController(
            GetNavigationSchemaUseCase getNavigationSchemaUseCase,
            GetPageSchemaUseCase getPageSchemaUseCase,
            GetFieldSchemaUseCase getFieldSchemaUseCase) {
        return new NavigationController(getNavigationSchemaUseCase, getPageSchemaUseCase, getFieldSchemaUseCase);
    }

    @Bean
    public DictTypeRepository dictTypeRepository(IdentityPersistenceMapper mapper) {
        return new JdbcDictTypeRepository(mapper);
    }

    @Bean
    public DictDataRepository dictDataRepository(IdentityPersistenceMapper mapper) {
        return new JdbcDictDataRepository(mapper);
    }

    @Bean
    public DictTypeUseCase dictTypeUseCase(
            DictTypeRepository dictTypeRepository,
            PositiveIdGenerator positiveIdGenerator,
            Clock identityClock) {
        return new DictTypeUseCase(dictTypeRepository, positiveIdGenerator, identityClock);
    }

    @Bean
    public DictDataUseCase dictDataUseCase(
            DictDataRepository dictDataRepository,
            DictTypeRepository dictTypeRepository,
            PositiveIdGenerator positiveIdGenerator,
            Clock identityClock) {
        return new DictDataUseCase(dictDataRepository, dictTypeRepository, positiveIdGenerator, identityClock);
    }

    @Bean
    public DictTypeController dictTypeController(DictTypeUseCase dictTypeUseCase) {
        return new DictTypeController(dictTypeUseCase);
    }

    @Bean
    public DictDataController dictDataController(DictDataUseCase dictDataUseCase) {
        return new DictDataController(dictDataUseCase);
    }
}
