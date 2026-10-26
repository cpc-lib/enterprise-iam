package com.enterprise.iam.organization.infrastructure.config;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.application.port.out.PositionRepository;
import com.enterprise.iam.organization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.organization.application.port.out.TeamMemberRepository;
import com.enterprise.iam.organization.application.port.out.TeamMemberRoleRepository;
import com.enterprise.iam.organization.application.port.out.TeamRepository;
import com.enterprise.iam.organization.application.port.out.TeamRoleRepository;
import com.enterprise.iam.organization.application.service.AddTeamMemberUseCase;
import com.enterprise.iam.organization.application.service.AssignTeamRoleUseCase;
import com.enterprise.iam.organization.application.service.CreateOrganizationUseCase;
import com.enterprise.iam.organization.application.service.CreatePositionUseCase;
import com.enterprise.iam.organization.application.service.CreateTeamRoleUseCase;
import com.enterprise.iam.organization.application.service.CreateTeamUseCase;
import com.enterprise.iam.organization.application.service.DeletePositionUseCase;
import com.enterprise.iam.organization.application.service.DisableOrganizationUseCase;
import com.enterprise.iam.organization.application.service.DisablePositionUseCase;
import com.enterprise.iam.organization.application.service.EnableOrganizationUseCase;
import com.enterprise.iam.organization.application.service.EnablePositionUseCase;
import com.enterprise.iam.organization.application.service.GetOrganizationDetailUseCase;
import com.enterprise.iam.organization.application.service.GetOrganizationTreeUseCase;
import com.enterprise.iam.organization.application.service.GetPositionDetailUseCase;
import com.enterprise.iam.organization.application.service.ListOrganizationMembersUseCase;
import com.enterprise.iam.organization.application.service.ListPositionsUseCase;
import com.enterprise.iam.organization.application.service.MoveTeamUseCase;
import com.enterprise.iam.organization.application.service.RemoveTeamMemberUseCase;
import com.enterprise.iam.organization.application.service.UpdateOrganizationUseCase;
import com.enterprise.iam.organization.application.service.UpdatePositionUseCase;
import com.enterprise.iam.organization.infrastructure.persistence.JdbcOrganizationRepository;
import com.enterprise.iam.organization.infrastructure.persistence.JdbcPositionRepository;
import com.enterprise.iam.organization.infrastructure.persistence.JdbcTeamMemberRepository;
import com.enterprise.iam.organization.infrastructure.persistence.JdbcTeamMemberRoleRepository;
import com.enterprise.iam.organization.infrastructure.persistence.JdbcTeamRepository;
import com.enterprise.iam.organization.infrastructure.persistence.JdbcTeamRoleRepository;
import com.enterprise.iam.organization.infrastructure.persistence.TimeOrderedPositiveIdGenerator;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.enterprise.iam.organization.interfaces.rest.DepartmentController;
import com.enterprise.iam.organization.interfaces.rest.OrganizationController;
import com.enterprise.iam.organization.interfaces.rest.PositionController;
import com.enterprise.iam.organization.interfaces.rest.TeamController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.organization", name = "enabled", havingValue = "true")
public class OrganizationConfiguration {

    @Bean
    public Clock organizationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PositiveIdGenerator positiveIdGenerator(
            Clock organizationClock,
            @Value("${iam.organization.node-id:1}") int nodeId) {
        return new TimeOrderedPositiveIdGenerator(organizationClock, nodeId);
    }

    @Bean
    public OrganizationRepository organizationRepository(OrganizationPersistenceMapper mapper) {
        return new JdbcOrganizationRepository(mapper);
    }

    @Bean
    public PositionRepository positionRepository(OrganizationPersistenceMapper mapper) {
        return new JdbcPositionRepository(mapper);
    }

    @Bean
    public TeamRepository teamRepository(OrganizationPersistenceMapper mapper) {
        return new JdbcTeamRepository(mapper);
    }

    @Bean
    public TeamMemberRepository teamMemberRepository(OrganizationPersistenceMapper mapper) {
        return new JdbcTeamMemberRepository(mapper);
    }

    @Bean
    public TeamRoleRepository teamRoleRepository(OrganizationPersistenceMapper mapper) {
        return new JdbcTeamRoleRepository(mapper);
    }

    @Bean
    public TeamMemberRoleRepository teamMemberRoleRepository(OrganizationPersistenceMapper mapper) {
        return new JdbcTeamMemberRoleRepository(mapper);
    }

    @Bean
    public CreateOrganizationUseCase createOrganizationUseCase(
            OrganizationRepository organizationRepository,
            PositiveIdGenerator idGenerator,
            Clock organizationClock) {
        return new CreateOrganizationUseCase(organizationRepository, idGenerator, organizationClock);
    }

    @Bean
    public GetOrganizationTreeUseCase getOrganizationTreeUseCase(OrganizationRepository organizationRepository) {
        return new GetOrganizationTreeUseCase(organizationRepository);
    }

    @Bean
    public GetOrganizationDetailUseCase getOrganizationDetailUseCase(OrganizationRepository organizationRepository) {
        return new GetOrganizationDetailUseCase(organizationRepository);
    }

    @Bean
    public UpdateOrganizationUseCase updateOrganizationUseCase(OrganizationRepository organizationRepository) {
        return new UpdateOrganizationUseCase(organizationRepository);
    }

    @Bean
    public DisableOrganizationUseCase disableOrganizationUseCase(OrganizationRepository organizationRepository) {
        return new DisableOrganizationUseCase(organizationRepository);
    }

    @Bean
    public EnableOrganizationUseCase enableOrganizationUseCase(OrganizationRepository organizationRepository) {
        return new EnableOrganizationUseCase(organizationRepository);
    }

    @Bean
    public ListOrganizationMembersUseCase listOrganizationMembersUseCase(OrganizationRepository organizationRepository) {
        return new ListOrganizationMembersUseCase(organizationRepository);
    }

    @Bean
    public CreatePositionUseCase createPositionUseCase(
            PositionRepository positionRepository,
            OrganizationRepository organizationRepository,
            PositiveIdGenerator idGenerator,
            Clock organizationClock) {
        return new CreatePositionUseCase(positionRepository, organizationRepository, idGenerator, organizationClock);
    }

    @Bean
    public UpdatePositionUseCase updatePositionUseCase(
            PositionRepository positionRepository,
            OrganizationRepository organizationRepository) {
        return new UpdatePositionUseCase(positionRepository, organizationRepository);
    }

    @Bean
    public DisablePositionUseCase disablePositionUseCase(PositionRepository positionRepository) {
        return new DisablePositionUseCase(positionRepository);
    }

    @Bean
    public EnablePositionUseCase enablePositionUseCase(PositionRepository positionRepository) {
        return new EnablePositionUseCase(positionRepository);
    }

    @Bean
    public ListPositionsUseCase listPositionsUseCase(PositionRepository positionRepository) {
        return new ListPositionsUseCase(positionRepository);
    }

    @Bean
    public GetPositionDetailUseCase getPositionDetailUseCase(PositionRepository positionRepository) {
        return new GetPositionDetailUseCase(positionRepository);
    }

    @Bean
    public DeletePositionUseCase deletePositionUseCase(PositionRepository positionRepository) {
        return new DeletePositionUseCase(positionRepository);
    }

    @Bean
    public CreateTeamUseCase createTeamUseCase(
            TeamRepository teamRepository,
            OrganizationRepository organizationRepository,
            PositiveIdGenerator idGenerator,
            Clock organizationClock) {
        return new CreateTeamUseCase(teamRepository, organizationRepository, idGenerator, organizationClock);
    }

    @Bean
    public AddTeamMemberUseCase addTeamMemberUseCase(
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            PositiveIdGenerator idGenerator,
            Clock organizationClock) {
        return new AddTeamMemberUseCase(teamMemberRepository, teamRepository, idGenerator, organizationClock);
    }

    @Bean
    public MoveTeamUseCase moveTeamUseCase(TeamRepository teamRepository) {
        return new MoveTeamUseCase(teamRepository);
    }

    @Bean
    public RemoveTeamMemberUseCase removeTeamMemberUseCase(TeamMemberRepository teamMemberRepository) {
        return new RemoveTeamMemberUseCase(teamMemberRepository);
    }

    @Bean
    public CreateTeamRoleUseCase createTeamRoleUseCase(
            TeamRoleRepository teamRoleRepository,
            TeamRepository teamRepository,
            PositiveIdGenerator idGenerator,
            Clock organizationClock) {
        return new CreateTeamRoleUseCase(teamRoleRepository, teamRepository, idGenerator, organizationClock);
    }

    @Bean
    public AssignTeamRoleUseCase assignTeamRoleUseCase(
            TeamMemberRoleRepository teamMemberRoleRepository,
            TeamMemberRepository teamMemberRepository,
            TeamRoleRepository teamRoleRepository,
            TeamRepository teamRepository,
            PositiveIdGenerator idGenerator,
            Clock organizationClock) {
        return new AssignTeamRoleUseCase(
                teamMemberRoleRepository, teamMemberRepository, teamRoleRepository, teamRepository,
                idGenerator, organizationClock);
    }

    @Bean
    public OrganizationController organizationController(
            CreateOrganizationUseCase createOrganizationUseCase,
            GetOrganizationTreeUseCase getOrganizationTreeUseCase,
            GetOrganizationDetailUseCase getOrganizationDetailUseCase,
            UpdateOrganizationUseCase updateOrganizationUseCase,
            DisableOrganizationUseCase disableOrganizationUseCase,
            EnableOrganizationUseCase enableOrganizationUseCase,
            ListOrganizationMembersUseCase listOrganizationMembersUseCase) {
        return new OrganizationController(
                createOrganizationUseCase,
                getOrganizationTreeUseCase,
                getOrganizationDetailUseCase,
                updateOrganizationUseCase,
                disableOrganizationUseCase,
                enableOrganizationUseCase,
                listOrganizationMembersUseCase);
    }

    @Bean
    public DepartmentController departmentController(
            CreateOrganizationUseCase createOrganizationUseCase,
            GetOrganizationTreeUseCase getOrganizationTreeUseCase,
            GetOrganizationDetailUseCase getOrganizationDetailUseCase,
            UpdateOrganizationUseCase updateOrganizationUseCase,
            DisableOrganizationUseCase disableOrganizationUseCase,
            EnableOrganizationUseCase enableOrganizationUseCase,
            ListOrganizationMembersUseCase listOrganizationMembersUseCase) {
        return new DepartmentController(
                createOrganizationUseCase,
                getOrganizationTreeUseCase,
                getOrganizationDetailUseCase,
                updateOrganizationUseCase,
                disableOrganizationUseCase,
                enableOrganizationUseCase,
                listOrganizationMembersUseCase);
    }

    @Bean
    public PositionController positionController(
            CreatePositionUseCase createPositionUseCase,
            UpdatePositionUseCase updatePositionUseCase,
            DisablePositionUseCase disablePositionUseCase,
            EnablePositionUseCase enablePositionUseCase,
            ListPositionsUseCase listPositionsUseCase,
            GetPositionDetailUseCase getPositionDetailUseCase,
            DeletePositionUseCase deletePositionUseCase) {
        return new PositionController(
                createPositionUseCase,
                updatePositionUseCase,
                disablePositionUseCase,
                enablePositionUseCase,
                listPositionsUseCase,
                getPositionDetailUseCase,
                deletePositionUseCase);
    }

    @Bean
    public TeamController teamController(
            CreateTeamUseCase createTeamUseCase,
            MoveTeamUseCase moveTeamUseCase,
            AddTeamMemberUseCase addTeamMemberUseCase,
            RemoveTeamMemberUseCase removeTeamMemberUseCase,
            CreateTeamRoleUseCase createTeamRoleUseCase,
            AssignTeamRoleUseCase assignTeamRoleUseCase) {
        return new TeamController(
                createTeamUseCase,
                moveTeamUseCase,
                addTeamMemberUseCase,
                removeTeamMemberUseCase,
                createTeamRoleUseCase,
                assignTeamRoleUseCase);
    }
}
