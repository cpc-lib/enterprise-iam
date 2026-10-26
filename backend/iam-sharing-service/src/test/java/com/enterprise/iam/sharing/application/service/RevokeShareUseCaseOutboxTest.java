package com.enterprise.iam.sharing.application.service;

import com.enterprise.iam.sharing.application.command.RevokeShareCommand;
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

class RevokeShareUseCaseOutboxTest {

    private static final Instant NOW = Instant.parse("2026-09-24T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void revokeBumpsEpochAndAppendsRevokedEventWithOperations() {
        Share share = activeShare();
        StubShareRepository repository = new StubShareRepository(share);
        StubShareAclRepository aclRepository = new StubShareAclRepository(List.of(
                acl(1L, 31L), acl(2L, 32L), acl(3L, 31L)));
        CreateShareUseCaseOutboxTest.RecordingEpochStore epochStore =
                new CreateShareUseCaseOutboxTest.RecordingEpochStore(2L);
        CreateShareUseCaseOutboxTest.RecordingAppender appender =
                new CreateShareUseCaseOutboxTest.RecordingAppender();

        RevokeShareUseCase useCase = new RevokeShareUseCase(
                repository, aclRepository, epochStore, appender, CLOCK);

        CreateShareResult result = useCase.revoke(
                new RevokeShareCommand(7L, 100L, "owner-left", 0L, "request-0003"));

        assertThat(result.status()).isEqualTo(ShareStatus.REVOKED);
        assertThat(repository.revoked).containsExactly(new long[]{7L, 100L});
        assertThat(epochStore.requested).containsExactly(new long[]{7L, 9L});
        assertThat(appender.revoked).hasSize(1);
        var call = appender.revoked.get(0);
        assertThat(call.share().id()).isEqualTo(100L);
        assertThat(call.share().resourceId()).isEqualTo(9L);
        assertThat(call.epoch()).isEqualTo(2L);
        assertThat(call.operationIds()).containsExactly(31L, 32L);
        assertThat(call.occurredAt()).isEqualTo(NOW);
        assertThat(appender.granted).isEmpty();
    }

    private static Share activeShare() {
        return new Share(
                100L, 7L, 9L, "invoice-42", ShareTargetType.USER, 11L, 13L,
                null, 100L, 0, ShareStatus.ACTIVE,
                NOW.minusSeconds(3600), NOW.plusSeconds(3600),
                false, 1, null, null, null, 0L, NOW, NOW);
    }

    private static ShareAcl acl(long id, long operationId) {
        return new ShareAcl(id, 7L, 100L, operationId, ShareAclStatus.ACTIVE, 0L, NOW, List.of());
    }

    static final class StubShareRepository implements ShareRepository {
        final Map<Long, Share> byId = new HashMap<>();
        final List<long[]> revoked = new ArrayList<>();

        StubShareRepository(Share share) {
            byId.put(share.id(), share);
        }

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
        public void revoke(long tenantId, long shareId, ShareStatus status, String reason,
                           long expectedVersion) {
            revoked.add(new long[]{tenantId, shareId});
            Share current = byId.get(shareId);
            if (current != null) {
                byId.put(shareId, new Share(
                        current.id(), current.tenantId(), current.resourceId(),
                        current.resourceInstanceKey(), current.targetType(), current.targetId(),
                        current.creatorUserId(), current.parentShareId(), current.rootShareId(),
                        current.shareDepth(), ShareStatus.REVOKED, current.startTime(),
                        current.expireTime(), current.canReshare(), null, reason, null, NOW,
                        expectedVersion + 1, current.createdAt(), NOW));
            }
        }
    }

    static final class StubShareAclRepository implements ShareAclRepository {
        private final List<ShareAcl> acls;

        StubShareAclRepository(List<ShareAcl> acls) {
            this.acls = acls;
        }

        @Override
        public List<ShareAcl> saveAll(List<ShareAcl> shareAcls) {
            return shareAcls;
        }

        @Override
        public List<ShareAcl> findByShareId(long tenantId, long shareId) {
            return acls.stream().filter(acl -> acl.shareId() == shareId).toList();
        }
    }

    static final class SequenceIdGenerator implements PositiveIdGenerator {
        private long next = 1;

        @Override
        public long nextId() {
            return next++;
        }
    }
}
