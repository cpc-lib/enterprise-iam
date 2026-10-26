package com.enterprise.iam.file.domain.model;

import java.util.Objects;

/**
 * Outcome of a single scanner invocation for one file (SPEC 35 §12).
 *
 * <p>SUSPICIOUS (SPEC 35 §12) is intentionally not modelled: the database CHECK constraints
 * {@code ck_file_scan_result} ({@code SCANNING/CLEAN/INFECTED/FAILED}) and
 * {@code ck_file_scan_status} ({@code PENDING/SCANNING/CLEAN/INFECTED/FAILED/NOT_REQUIRED})
 * have no SUSPICIOUS value. Adapters MUST normalize a SUSPICIOUS scanner result to
 * {@link Result#INFECTED} — the safe default — so the file is quarantined per SPEC 35 §13
 * instead of being allowed near an access endpoint.
 */
public final class ScanVerdict {

    public enum Result {
        CLEAN,
        INFECTED,
        FAILED,
        SKIPPED
    }

    private final Result result;
    private final String threatCode;
    private final String errorCode;
    private final String signatureVersion;

    private ScanVerdict(Result result, String threatCode, String errorCode, String signatureVersion) {
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.threatCode = threatCode;
        this.errorCode = errorCode;
        this.signatureVersion = signatureVersion;
    }

    public static ScanVerdict clean(String signatureVersion) {
        return new ScanVerdict(Result.CLEAN, null, null, signatureVersion);
    }

    public static ScanVerdict infected(String threatCode, String signatureVersion) {
        return new ScanVerdict(Result.INFECTED,
                Objects.requireNonNull(threatCode, "threatCode must not be null"), null, signatureVersion);
    }

    public static ScanVerdict failed(String errorCode) {
        return new ScanVerdict(Result.FAILED, null,
                Objects.requireNonNull(errorCode, "errorCode must not be null"), null);
    }

    public static ScanVerdict skipped() {
        return new ScanVerdict(Result.SKIPPED, null, null, null);
    }

    public Result result() {
        return result;
    }

    public String threatCode() {
        return threatCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public String signatureVersion() {
        return signatureVersion;
    }
}
