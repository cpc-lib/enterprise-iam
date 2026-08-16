package com.enterprise.iam.auth.application.port.out;

import com.enterprise.iam.auth.application.model.AuthenticationAttempt;

public interface AuthenticationAttemptSink {

    void record(AuthenticationAttempt attempt);
}
