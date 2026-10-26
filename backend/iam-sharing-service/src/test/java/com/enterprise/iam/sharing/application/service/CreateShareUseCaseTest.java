package com.enterprise.iam.sharing.application.service;

import com.enterprise.iam.sharing.application.command.CreateShareCommand;
import com.enterprise.iam.sharing.application.exception.ShareDomainInvariantException;
import com.enterprise.iam.sharing.application.model.CreateShareResult;
import com.enterprise.iam.sharing.domain.model.ShareAcl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.enterprise.iam.sharing.application.service.CreateShareUseCaseOutboxTest.RecordingShareAclRepository;
import static com.enterprise.iam.sharing.application.service.CreateShareUseCaseOutboxTest.RecordingShareRepository;
import static com.enterprise.iam.sharing.application.service.CreateShareUseCaseOutboxTest.SequenceIdGenerator;
import static com.enterprise.iam.sharing.domain.model.ShareTargetType.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateShareUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final RecordingShareRepository shareRepository = new RecordingShareRepository();
    private final RecordingShareAclRepository aclRepository = new RecordingShareAclRepository();

    private CreateShareUseCase useCase() {
        return new CreateShareUseCase(
                shareRepository, aclRepository, new SequenceIdGenerator(9000L), CLOCK);
    }

    @Test
    void persistsFieldGrantsGroupedByOperationForUserTarget() {
        CreateShareCommand command = new CreateShareCommand(
                1L, 9L, "invoice-42", USER, 11L, 13L, null,
                false, NOW.plusSeconds(3600),
                List.of(21L, 22L),
                List.of(
                        new CreateShareCommand.FieldGrant(21L, 301L, true, true, false, 800L),
                        new CreateShareCommand.FieldGrant(21L, 302L, false, false, true, null),
                        new CreateShareCommand.FieldGrant(22L, 303L, true, false, false, null)),
                "trace-fields-1");

        CreateShareResult result = useCase().create(command);

        Map<Long, ShareAcl> byOperation = aclRepository.saved.stream()
                .collect(Collectors.toMap(ShareAcl::operationId, Function.identity()));
        assertThat(byOperation).containsOnlyKeys(21L, 22L);
        List<ShareAcl.ShareFieldPermission> op21 = byOperation.get(21L).fieldPermissions();
        assertThat(op21).hasSize(2);
        assertThat(op21.get(0).fieldId()).isEqualTo(301L);
        assertThat(op21.get(0).writable()).isTrue();
        assertThat(op21.get(0).maskStrategyId()).isEqualTo(800L);
        assertThat(op21.get(1).fieldId()).isEqualTo(302L);
        assertThat(op21.get(1).hidden()).isTrue();
        assertThat(byOperation.get(22L).fieldPermissions())
                .singleElement()
                .satisfies(field -> assertThat(field.fieldId()).isEqualTo(303L));
        assertThat(shareRepository.saved).containsKey(result.shareId());
    }

    @Test
    void emptyFieldListIsValidAndCreatesBareAcls() {
        CreateShareCommand command = new CreateShareCommand(
                1L, 9L, "invoice-42", USER, 11L, 13L, null,
                true, NOW.plusSeconds(60),
                List.of(21L), List.of(), "trace-fields-2");

        CreateShareResult result = useCase().create(command);

        assertThat(result.shareId()).isPositive();
        assertThat(aclRepository.saved).hasSize(1);
        assertThat(aclRepository.saved.get(0).fieldPermissions()).isEmpty();
    }

    @Test
    void hiddenAndReadableFieldGrantIsRejectedWith422Invariant() {
        CreateShareCommand command = new CreateShareCommand(
                1L, 9L, "invoice-42", USER, 11L, 13L, null,
                false, NOW.plusSeconds(60),
                List.of(21L),
                List.of(new CreateShareCommand.FieldGrant(21L, 301L, true, false, true, null)),
                "trace-fields-3");

        assertThatThrownBy(() -> useCase().create(command))
                .isInstanceOf(ShareDomainInvariantException.class)
                .hasMessageContaining("hidden");
        assertThat(shareRepository.saved).isEmpty();
        assertThat(aclRepository.saved).isEmpty();
    }

    @Test
    void fieldGrantReferencingUnknownOperationIsRejected() {
        CreateShareCommand command = new CreateShareCommand(
                1L, 9L, "invoice-42", USER, 11L, 13L, null,
                false, NOW.plusSeconds(60),
                List.of(21L),
                List.of(new CreateShareCommand.FieldGrant(99L, 301L, true, false, false, null)),
                "trace-fields-4");

        assertThatThrownBy(() -> useCase().create(command))
                .isInstanceOf(ShareDomainInvariantException.class)
                .hasMessageContaining("operationId");
    }

    @Test
    void duplicateFieldGrantIsRejected() {
        CreateShareCommand command = new CreateShareCommand(
                1L, 9L, "invoice-42", USER, 11L, 13L, null,
                false, NOW.plusSeconds(60),
                List.of(21L),
                List.of(
                        new CreateShareCommand.FieldGrant(21L, 301L, true, false, false, null),
                        new CreateShareCommand.FieldGrant(21L, 301L, false, true, false, null)),
                "trace-fields-5");

        assertThatThrownBy(() -> useCase().create(command))
                .isInstanceOf(ShareDomainInvariantException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void expireAtInThePastIsRejected() {
        CreateShareCommand command = new CreateShareCommand(
                1L, 9L, "invoice-42", USER, 11L, 13L, null,
                false, NOW.minusSeconds(1),
                List.of(21L), List.of(), "trace-fields-6");

        assertThatThrownBy(() -> useCase().create(command))
                .isInstanceOf(ShareDomainInvariantException.class)
                .hasMessageContaining("expireAt");
    }

    @Test
    void duplicateOperationsAreRejected() {
        CreateShareCommand command = new CreateShareCommand(
                1L, 9L, "invoice-42", USER, 11L, 13L, null,
                false, NOW.plusSeconds(60),
                List.of(21L, 21L), List.of(), "trace-fields-7");

        assertThatThrownBy(() -> useCase().create(command))
                .isInstanceOf(ShareDomainInvariantException.class)
                .hasMessageContaining("unique");
    }
}
