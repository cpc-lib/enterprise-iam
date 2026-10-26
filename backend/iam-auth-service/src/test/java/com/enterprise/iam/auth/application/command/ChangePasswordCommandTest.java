package com.enterprise.iam.auth.application.command;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangePasswordCommandTest {

    private static final String REQUEST_ID = "request-0001";

    @Test
    void copiesAllSecretBuffersSoCallersRetainNoAliases() {
        char[] refreshToken = filled(48, 't');
        char[] currentPassword = "current-password".toCharArray();
        char[] newPassword = "replacement-password".toCharArray();
        ChangePasswordCommand command = new ChangePasswordCommand(
                refreshToken, currentPassword, newPassword, 7L, 11L, 101L, REQUEST_ID);

        Arrays.fill(refreshToken, '\0');
        Arrays.fill(currentPassword, '\0');
        Arrays.fill(newPassword, '\0');

        char[] refreshCopy = command.presentedRefreshTokenCopy();
        char[] currentCopy = command.currentPasswordCopy();
        char[] newCopy = command.newPasswordCopy();
        assertThat(refreshCopy).doesNotContain('\0');
        assertThat(currentCopy).containsExactly("current-password".toCharArray());
        assertThat(newCopy).containsExactly("replacement-password".toCharArray());
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        assertThat(command.expectedTenantId()).isEqualTo(7L);
        assertThat(command.expectedUserId()).isEqualTo(11L);
        assertThat(command.expectedSessionId()).isEqualTo(101L);

        Arrays.fill(refreshCopy, '\0');
        Arrays.fill(currentCopy, '\0');
        Arrays.fill(newCopy, '\0');
        command.destroy();
    }

    @Test
    void destroyZeroesEveryInternalBufferAndBlocksFurtherReads() {
        ChangePasswordCommand command = new ChangePasswordCommand(
                filled(48, 't'),
                "current-password".toCharArray(),
                "replacement-password".toCharArray(),
                7L,
                11L,
                101L,
                REQUEST_ID);

        command.destroy();

        assertThat(command.isDestroyed()).isTrue();
        assertThatThrownBy(command::presentedRefreshTokenCopy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been consumed");
        assertThatThrownBy(command::currentPasswordCopy)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(command::newPasswordCopy)
                .isInstanceOf(IllegalStateException.class);
        command.close();
        assertThat(command.isDestroyed()).isTrue();
    }

    private static char[] filled(int length, char character) {
        char[] value = new char[length];
        Arrays.fill(value, character);
        return value;
    }
}
