package com.enterprise.iam.file.application.model;

/**
 * A file row atomically claimed for scanning by the background poller.
 *
 * @param attempt               the scan_attempt to write (1 for a fresh file, last failed attempt + 1 on retry)
 * @param version               the iam_file.version after the claim, used for the optimistic terminal transition
 */
public record ClaimedScan(
        long tenantId,
        long fileId,
        int attempt,
        String fileName,
        String declaredContentType,
        long fileSize,
        String sha256Hex,
        long version) {

    public FileScanTarget toTarget() {
        return new FileScanTarget(fileId, tenantId, fileName, declaredContentType, fileSize, sha256Hex);
    }
}
