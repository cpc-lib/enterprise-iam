package com.enterprise.iam.sharing.application.command;

import java.util.Objects;

public record ReshareCommand(CreateShareCommand command, long parentVersion) {

    public ReshareCommand {
        Objects.requireNonNull(command, "command must not be null");
        if (parentVersion < 0) {
            throw new IllegalArgumentException("parentVersion must not be negative");
        }
    }
}
