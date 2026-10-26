package com.enterprise.iam.auth.application.port.out;

import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;

/** Confirms that a login response body and its HttpOnly refresh cookie arrived together. */
public interface LoginDeliveryConfirmer {

    boolean confirm(long sessionId, SensitiveRefreshToken presentedRefreshToken);
}
