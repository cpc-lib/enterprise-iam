package com.enterprise.iam.sharing.application.service;

import com.enterprise.iam.sharing.application.command.CreateShareCommand;
import com.enterprise.iam.sharing.application.model.CreateShareResult;
import com.enterprise.iam.sharing.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.sharing.application.port.out.ShareAclEventAppender;
import com.enterprise.iam.sharing.application.port.out.ShareAclRepository;
import com.enterprise.iam.sharing.application.port.out.ShareProjectionEpochStore;
import com.enterprise.iam.sharing.application.port.out.ShareRepository;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareAcl;
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

class CreateShareUseCaseOutboxTest {

    private static final Instant NOW = Instant.parse("2026-09-24T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createAppendsGrantedEventWithShareFacts() {
        RecordingShareRepository shareRepository = new RecordingShareRepository();
        RecordingShareAclRepository aclRepository = new RecordingShareAclRepository();
        RecordingAppender appender = new RecordingAppender();
        RecordingEpochStore epochStore = new RecordingEpochStore(1L);
        CreateShareUseCase useCase = new CreateShareUseCase(
                shareRepository,
                aclRepository,
                new SequenceIdGenerator(5000L),
                CLOCK,
                appender,
                epochStore);

        CreateShareCommand command = new CreateShareCommand(
                7L, 9L, "invoice-42", ShareTargetType.USER, 11L, 13L, null,
                false, NOW.plusSeconds(3600),
                List.of(21L, 22L),
                List.of(new CreateShareCommand.FieldGrant(
                        21L, 301L, true, false, false, 800L)),
                "request-0001");

        CreateShareResult result = useCase.create(command);

        assertThat(shareRepository.saved).hasSize(1);
        Share savedShare = shareRepository.saved.get(result.shareId());
        assertThat(savedShare.startTime()).isEqualTo(NOW);
        assertThat(aclRepository.saved).hasSize(2);
        ShareAcl firstAcl = aclRepository.saved.stream()
                .filter(acl -> acl.operationId() == 21L)
                .findFirst().orElseThrow();
        assertThat(firstAcl.fieldPermissions())
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.fieldId()).isEqualTo(301L);
                    assertThat(field.readable()).isTrue();
                    assertThat(field.maskStrategyId()).isEqualTo(800L);
                });
        assertThat(epochStore.requested).containsExactly(new long[]{7L, 9L});
        assertThat(appender.granted).hasSize(1);
        RecordingAppender.GrantedCall call = appender.granted.get(0);
        assertThat(call.share.id()).isEqualTo(result.shareId());
        assertThat(call.share.tenantId()).isEqualTo(7L);
        assertThat(call.share.resourceId()).isEqualTo(9L);
        assertThat(call.share.resourceInstanceKey()).isEqualTo("invoice-42");
        assertThat(call.share.targetType()).isEqualTo(ShareTargetType.USER);
        assertThat(call.share.targetId()).isEqualTo(11L);
        assertThat(call.epoch).isEqualTo(1L);
        assertThat(call.acls).hasSize(2);
        assertThat(call.acls.stream().map(ShareAcl::operationId).sorted().toList())
                .containsExactly(21L, 22L);
        ShareAcl aclWithField = call.acls.stream()
                .filter(acl -> acl.operationId() == 21L).findFirst().orElseThrow();
        assertThat(aclWithField.fieldPermissions())
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.fieldId()).isEqualTo(301L);
                    assertThat(field.readable()).isTrue();
                    assertThat(field.maskStrategyId()).isEqualTo(800L);
                });
        assertThat(call.occurredAt).isEqualTo(NOW);
        assertThat(appender.revoked).isEmpty();
    }

    @Test
    void createCarriesAdvancingEpochFromEpochStore() {
        RecordingAppender appender = new RecordingAppender();
        RecordingEpochStore epochStore = new RecordingEpochStore(3L);
        CreateShareUseCase useCase = new CreateShareUseCase(
                new RecordingShareRepository(),
                new RecordingShareAclRepository(),
                new SequenceIdGenerator(1L),
                CLOCK,
                appender,
                epochStore);
        CreateShareCommand command = new CreateShareCommand(
                7L, 9L, "k", ShareTargetType.TEAM, 2L, 3L, null,
                true, NOW.plusSeconds(10),
                List.of(1L), List.of(), "request-0004");

        useCase.create(command);

        assertThat(appender.granted).singleElement()
                .satisfies(call -> assertThat(call.epoch).isEqualTo(3L));
    }

    @Test
    void createWithoutAppenderStillSucceeds() {
        CreateShareUseCase useCase = new CreateShareUseCase(
                new RecordingShareRepository(),
                new RecordingShareAclRepository(),
                new SequenceIdGenerator(1L),
                CLOCK);
        CreateShareCommand command = new CreateShareCommand(
                1L, 1L, "k", ShareTargetType.TEAM, 2L, 3L, null,
                true, NOW.plusSeconds(10),
                List.of(1L), List.of(), "request-0002");
        assertThat(useCase.create(command).status()).isEqualTo(ShareStatus.ACTIVE);
    }

    static final class RecordingAppender implements ShareAclEventAppender {
        final List<GrantedCall> granted = new ArrayList<>();
        final List<RevokedCall> revoked = new ArrayList<>();

        @Override
        public void appendGranted(Share share, List<ShareAcl> acls, long epoch, Instant occurredAt) {
            granted.add(new GrantedCall(share, List.copyOf(acls), epoch, occurredAt));
        }

        @Override
        public void appendRevoked(Share share, List<Long> operationIds, long epoch, Instant occurredAt) {
            revoked.add(new RevokedCall(share, List.copyOf(operationIds), epoch, occurredAt));
        }

        record GrantedCall(Share share, List<ShareAcl> acls, long epoch, Instant occurredAt) {
        }

        record RevokedCall(Share share, List<Long> operationIds, long epoch, Instant occurredAt) {
        }
    }

    static final class RecordingEpochStore implements ShareProjectionEpochStore {
        final List<long[]> requested = new ArrayList<>();
        private final long epoch;

        RecordingEpochStore(long epoch) {
            this.epoch = epoch;
        }

        @Override
        public long nextEpoch(long tenantId, long resourceId) {
            requested.add(new long[]{tenantId, resourceId});
            return epoch;
        }
    }

    static final class SequenceIdGenerator implements PositiveIdGenerator {
        private long next;

        SequenceIdGenerator(long start) {
            this.next = start;
        }

        @Override
        public long nextId() {
            return next++;
        }
    }

    static final class RecordingShareRepository implements ShareRepository {
        final Map<Long, Share> saved = new HashMap<>();

        @Override
        public Share save(Share share) {
            saved.put(share.id(), share);
            return share;
        }

        @Override
        public Optional<Share> findById(long tenantId, long shareId) {
            return Optional.ofNullable(saved.get(shareId));
        }

        @Override
        public void revoke(long tenantId, long shareId, com.enterprise.iam.sharing.domain.model.ShareStatus status,
                           String reason, long expectedVersion) {
        }
    }

    static final class RecordingShareAclRepository implements ShareAclRepository {
        final List<ShareAcl> saved = new ArrayList<>();

        @Override
        public List<ShareAcl> saveAll(List<ShareAcl> acls) {
            saved.addAll(acls);
            return acls;
        }

        @Override
        public List<ShareAcl> findByShareId(long tenantId, long shareId) {
            return saved.stream().filter(acl -> acl.shareId() == shareId).toList();
        }
    }
}
