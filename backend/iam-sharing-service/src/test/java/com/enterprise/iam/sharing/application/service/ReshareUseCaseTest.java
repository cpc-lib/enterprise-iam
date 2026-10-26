package com.enterprise.iam.sharing.application.service;

import com.enterprise.iam.sharing.application.command.CreateShareCommand;
import com.enterprise.iam.sharing.application.command.ReshareCommand;
import com.enterprise.iam.sharing.application.model.CreateShareResult;
import com.enterprise.iam.sharing.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.sharing.application.port.out.ShareAclRepository;
import com.enterprise.iam.sharing.application.port.out.ShareRepository;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareAcl;
import com.enterprise.iam.sharing.domain.model.ShareAclStatus;
import com.enterprise.iam.sharing.domain.model.ShareStatus;
import com.enterprise.iam.sharing.domain.model.ShareTargetType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReshareUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final StubShareRepository shareRepository = new StubShareRepository();
    private final StubShareAclRepository shareAclRepository = new StubShareAclRepository();
    private final StubIdGenerator idGenerator = new StubIdGenerator(1000L);
    private final CreateShareUseCase createShareUseCase =
            new CreateShareUseCase(shareRepository, shareAclRepository, idGenerator, CLOCK);
    private final ReshareUseCase useCase =
            new ReshareUseCase(shareRepository, shareAclRepository, createShareUseCase, CLOCK);

    @Test
    void createsBoundedChildFromParentRightsAndKeepsBoundedFields() {
        Share parent = new Share(
                100L, 1L, 1L, "inst-1", ShareTargetType.USER, 5L, 9L,
                null, 100L, 0, ShareStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                true, 1, null, null, null, 5L, NOW, NOW);
        shareRepository.save(parent);
        shareAclRepository.saveAll(List.of(
                acl(200L, 100L, 1L),
                acl(201L, 100L, 2L)));

        CreateShareCommand createCommand = new CreateShareCommand(
                1L, 1L, "inst-1", ShareTargetType.TEAM, 7L, 9L,
                100L, false,
                Instant.parse("2026-12-01T00:00:00Z"),
                List.of(2L, 3L),
                List.of(
                        new CreateShareCommand.FieldGrant(2L, 77L, true, false, false, null),
                        new CreateShareCommand.FieldGrant(3L, 88L, false, false, true, null)),
                "trace-1");
        CreateShareResult result = useCase.reshare(new ReshareCommand(createCommand, 5L));

        assertThat(result.status()).isEqualTo(ShareStatus.ACTIVE);
        assertThat(result.version()).isZero();
        Share child = shareRepository.findById(1L, result.shareId()).orElseThrow();
        assertThat(child.parentShareId()).isEqualTo(100L);
        assertThat(child.targetType()).isEqualTo(ShareTargetType.TEAM);
        List<ShareAcl> childAcls = shareAclRepository.findByShareId(1L, result.shareId());
        assertThat(childAcls).hasSize(1);
        ShareAcl boundedAcl = childAcls.get(0);
        assertThat(boundedAcl.operationId()).isEqualTo(2L);
        assertThat(boundedAcl.fieldPermissions())
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.fieldId()).isEqualTo(77L);
                    assertThat(field.readable()).isTrue();
                });
    }

    private static ShareAcl acl(long id, long shareId, long operationId) {
        return new ShareAcl(id, 1L, shareId, operationId, ShareAclStatus.ACTIVE, 0L, NOW, List.of());
    }

    static final class StubShareRepository implements ShareRepository {
        private final Map<Long, Share> byId = new HashMap<>();

        @Override
        public Share save(Share share) {
            byId.put(share.id(), share);
            return share;
        }

        @Override
        public Optional<Share> findById(long tenantId, long shareId) {
            return Optional.ofNullable(byId.get(shareId));
        }

        @Override
        public void revoke(long tenantId, long shareId, ShareStatus status, String reason, long expectedVersion) {
            throw new UnsupportedOperationException();
        }
    }

    static final class StubShareAclRepository implements ShareAclRepository {
        private final Map<Long, List<ShareAcl>> byShare = new HashMap<>();

        @Override
        public List<ShareAcl> saveAll(List<ShareAcl> acls) {
            for (ShareAcl acl : acls) {
                byShare.computeIfAbsent(acl.shareId(), k -> new ArrayList<>()).add(acl);
            }
            return acls;
        }

        @Override
        public List<ShareAcl> findByShareId(long tenantId, long shareId) {
            return byShare.getOrDefault(shareId, List.of());
        }
    }

    static final class StubIdGenerator implements PositiveIdGenerator {
        private long next;

        StubIdGenerator(long start) {
            this.next = start;
        }

        @Override
        public long nextId() {
            return next++;
        }
    }
}
