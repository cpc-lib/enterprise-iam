package com.enterprise.iam.sharing.infrastructure.persistence;

import com.enterprise.iam.sharing.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.sharing.application.port.out.ShareAclRepository;
import com.enterprise.iam.sharing.domain.model.ShareAcl;
import com.enterprise.iam.sharing.domain.model.ShareAclStatus;
import com.enterprise.iam.sharing.infrastructure.persistence.mapper.SharingPersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class JdbcShareAclRepository implements ShareAclRepository {

    private final SharingPersistenceMapper mapper;
    private final PositiveIdGenerator idGenerator;

    public JdbcShareAclRepository(SharingPersistenceMapper mapper, PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    @Override
    public List<ShareAcl> saveAll(List<ShareAcl> acls) {
        Objects.requireNonNull(acls, "acls must not be null");
        Instant now = Instant.now();
        for (ShareAcl acl : acls) {
            mapper.insertShareOperation(
                    acl.id(),
                    acl.tenantId(),
                    acl.shareId(),
                    acl.operationId(),
                    acl.version(),
                    acl.createdAt());
            for (ShareAcl.ShareFieldPermission field : acl.fieldPermissions()) {
                mapper.insertShareField(
                        idGenerator.nextId(),
                        acl.tenantId(),
                        acl.shareId(),
                        acl.operationId(),
                        field.fieldId(),
                        field.readable(),
                        field.writable(),
                        field.hidden(),
                        field.maskStrategyId(),
                        now);
            }
        }
        return acls;
    }

    @Override
    public List<ShareAcl> findByShareId(long tenantId, long shareId) {
        List<ShareAcl> acls = mapper.findShareAclOperations(tenantId, shareId).stream().map(row -> {
            long operationId = row.operationId();
            List<ShareAcl.ShareFieldPermission> fields = findFields(tenantId, shareId, operationId);
            return new ShareAcl(
                    row.id(),
                    row.tenantId(),
                    row.shareId(),
                    operationId,
                    ShareAclStatus.ACTIVE,
                    row.version(),
                    row.createdAt(),
                    fields);
        }).toList();
        return acls;
    }

    private List<ShareAcl.ShareFieldPermission> findFields(long tenantId, long shareId, long operationId) {
        return mapper.findShareFields(tenantId, shareId, operationId).stream()
                .map(row -> new ShareAcl.ShareFieldPermission(
                        row.fieldId(),
                        row.readable(),
                        row.writable(),
                        row.hidden(),
                        row.maskStrategyId()))
                .toList();
    }
}
