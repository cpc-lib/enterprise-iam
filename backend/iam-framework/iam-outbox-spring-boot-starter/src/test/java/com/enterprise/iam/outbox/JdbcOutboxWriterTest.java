package com.enterprise.iam.outbox;

import com.enterprise.iam.outbox.mapper.OutboxPersistenceMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JdbcOutboxWriterTest {

    @Test
    void rejectsAppendOutsideExistingBusinessTransaction() {
        OutboxPersistenceMapper mapper = mock(OutboxPersistenceMapper.class);
        JdbcOutboxWriter writer = new JdbcOutboxWriter(mapper);

        assertThatThrownBy(() -> writer.append(new OutboxEventToAppend(
                1, 1, 2, "LOGIN_SESSION", 3, 4,
                "iam.auth.session-projection", 1, "{}", Instant.now())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already-active business transaction");
        verifyNoInteractions(mapper);
    }
}
