package com.enterprise.iam.auth.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;
import jakarta.validation.constraints.Positive;

public record LoginDeliveryConfirmationRequest(
        @Positive @NumericLong long sessionId) {
}
