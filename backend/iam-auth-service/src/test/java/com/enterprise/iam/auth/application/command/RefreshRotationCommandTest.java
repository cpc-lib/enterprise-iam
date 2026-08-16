package com.enterprise.iam.auth.application.command;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshRotationCommandTest {

    private static final String REQUEST_ID = "request-0001";

    private static char[] tokenOfLength(int length) {
        char[] value = new char[length];
        Arrays.fill(value, 't');
        return value;
    }

    @Test
    void copiesPresentedBufferSoCallerRetainsNoAlias() {
        char[] presented = tokenOfLength(48);
        RefreshRotationCommand command = new RefreshRotationCommand(presented, REQUEST_ID);

        Arrays.fill(presented, '\0');
        char[] copy = command.presentedTokenCopy();

        assertThat(copy).doesNotContain('\0');
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        Arrays.fill(copy, '\0');
    }

    @Test
    void lengthBoundsMatchSensitiveRefreshTokenContract() {
        assertThatThrownBy(() -> new RefreshRotationCommand(tokenOfLength(31), REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 32 and 256");
        assertThatThrownBy(() -> new RefreshRotationCommand(tokenOfLength(257), REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class);
        new RefreshRotationCommand(tokenOfLength(32), REQUEST_ID).destroy();
        new RefreshRotationCommand(tokenOfLength(256), REQUEST_ID).destroy();
    }

    @Test
    void requestIdLengthIsFrozenBetweenEightAndOneHundredTwentyEight() {
        assertThatThrownBy(() -> new RefreshRotationCommand(
                tokenOfLength(48), "req-7"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshRotationCommand(
                tokenOfLength(48), "r".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
        new RefreshRotationCommand(tokenOfLength(48), "request-0").destroy();
        new RefreshRotationCommand(tokenOfLength(48), "r".repeat(128)).destroy();
    }

    @Test
    void destroyZeroesBufferAndBlocksFurtherReads() {
        RefreshRotationCommand command = new RefreshRotationCommand(tokenOfLength(48), REQUEST_ID);

        command.destroy();

        assertThat(command.isDestroyed()).isTrue();
        assertThatThrownBy(command::presentedTokenCopy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been consumed");
        command.destroy();
        assertThat(command.isDestroyed()).isTrue();
    }
}
