package com.enterprise.iam.auth.application.port.in;

import com.enterprise.iam.auth.application.command.ChangePasswordCommand;

@FunctionalInterface
public interface ChangePasswordUseCase {

    void changePassword(ChangePasswordCommand command);
}
