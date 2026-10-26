package com.enterprise.iam.file.infrastructure.scan;

import com.enterprise.iam.file.application.model.FileScanTarget;
import com.enterprise.iam.file.application.port.out.FileScanPort;
import com.enterprise.iam.file.domain.model.ScanVerdict;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Real, dependency-free deterministic scanner for V1 end-to-end security gating without an
 * external antivirus service. Policy:
 * <ul>
 *   <li>blocked executable/script extensions (configurable) -&gt; INFECTED LOCAL_BLOCKED_EXT,
 *       including double-extension spoofs such as {@code invoice.exe.pdf};</li>
 *   <li>missing/empty/unreadable metadata -&gt; FAILED LOCAL_INVALID_METADATA so the file
 *       stays unavailable and goes through bounded retry instead of being released;</li>
 *   <li>everything else -&gt; CLEAN.</li>
 * </ul>
 * A real cloud-scanner adapter replaces this behind the same port later (SPEC 35 §10);
 * ClamAV is deliberately excluded by project decision.
 */
public final class LocalDeterministicScanAdapter implements FileScanPort {

    public static final String SCANNER_NAME = "local";
    static final String SIGNATURE_VERSION = "local-deterministic-1";
    static final String THREAT_BLOCKED_EXTENSION = "LOCAL_BLOCKED_EXT";
    static final String ERROR_INVALID_METADATA = "LOCAL_INVALID_METADATA";

    static final List<String> DEFAULT_BLOCKED_EXTENSIONS = List.of(
            "exe", "bat", "cmd", "scr", "msi", "ps1", "vbs", "jar", "sh");

    private final Set<String> blockedExtensions;

    public LocalDeterministicScanAdapter(List<String> blockedExtensions) {
        if (blockedExtensions == null || blockedExtensions.isEmpty()) {
            this.blockedExtensions = Set.copyOf(DEFAULT_BLOCKED_EXTENSIONS);
        } else {
            this.blockedExtensions = blockedExtensions.stream()
                    .filter(ext -> ext != null && !ext.isBlank())
                    .map(ext -> ext.strip().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    @Override
    public ScanVerdict scan(FileScanTarget target) {
        if (target == null
                || isBlank(target.fileName())
                || isBlank(target.declaredContentType())
                || target.fileSize() <= 0
                || !isValidSha256(target.sha256Hex())) {
            return ScanVerdict.failed(ERROR_INVALID_METADATA);
        }

        List<String> parts = Arrays.stream(target.fileName().toLowerCase(Locale.ROOT).split("\\."))
                .filter(part -> !part.isBlank())
                .toList();
        if (!parts.isEmpty()) {
            String finalExtension = parts.get(parts.size() - 1);
            if (blockedExtensions.contains(finalExtension)) {
                return ScanVerdict.infected(THREAT_BLOCKED_EXTENSION, SIGNATURE_VERSION);
            }
            // Double-extension spoof: an executable/script extension hidden in front of an
            // innocuous final extension, e.g. "invoice.exe.pdf".
            if (parts.size() >= 2 && blockedExtensions.contains(parts.get(parts.size() - 2))) {
                return ScanVerdict.infected(THREAT_BLOCKED_EXTENSION, SIGNATURE_VERSION);
            }
        }
        return ScanVerdict.clean(SIGNATURE_VERSION);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isValidSha256(String sha256Hex) {
        if (sha256Hex == null || sha256Hex.length() != 64) {
            return false;
        }
        for (int i = 0; i < sha256Hex.length(); i++) {
            if (Character.digit(sha256Hex.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
