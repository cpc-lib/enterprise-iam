package com.enterprise.iam.file.domain.model;

import java.time.Instant;
import java.util.Objects;

public record FileWatermarkSpec(
        long fileId,
        long tenantId,
        long userId,
        String watermarkText,
        String subText,
        double opacity,
        int rotateAngle,
        int fontSize,
        String color,
        String signature,
        Instant issuedAt) {

    public FileWatermarkSpec {
        Objects.requireNonNull(watermarkText, "watermarkText must not be null");
        Objects.requireNonNull(subText, "subText must not be null");
        Objects.requireNonNull(color, "color must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    }
}
