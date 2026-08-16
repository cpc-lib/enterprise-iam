package com.enterprise.iam.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JdbcOutboxWriterTest {

    @Test
    void rejectsAppendOutsideExistingBusinessTransaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcOutboxWriter writer = new JdbcOutboxWriter(jdbc);

        assertThatThrownBy(() -> writer.append(new OutboxEventToAppend(
                1, 1, 2, "LOGIN_SESSION", 3, 4,
                "iam.auth.session-projection", 1, "{}", Instant.now())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already-active business transaction");
        verifyNoInteractions(jdbc);
    }
}
