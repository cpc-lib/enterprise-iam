package com.enterprise.iam.file.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Typed configuration for the in-process file workers under {@code iam.file}.
 * Every worker defaults to disabled; the scanner adapter defaults to the local
 * deterministic policy.
 */
@ConfigurationProperties(prefix = "iam.file")
public final class FileWorkerProperties {

    private Scan scan = new Scan();
    private Reaper reaper = new Reaper();
    private QuotaReconcile quotaReconcile = new QuotaReconcile();
    private Purge purge = new Purge();

    public Scan getScan() {
        return scan;
    }

    public void setScan(Scan scan) {
        this.scan = scan;
    }

    public Reaper getReaper() {
        return reaper;
    }

    public void setReaper(Reaper reaper) {
        this.reaper = reaper;
    }

    public QuotaReconcile getQuotaReconcile() {
        return quotaReconcile;
    }

    public void setQuotaReconcile(QuotaReconcile quotaReconcile) {
        this.quotaReconcile = quotaReconcile;
    }

    public Purge getPurge() {
        return purge;
    }

    public void setPurge(Purge purge) {
        this.purge = purge;
    }

    public static final class Scan {

        /** Enabled flag kept for validation; the poller bean is toggled separately. */
        private boolean enabled;
        private String adapter = "local";
        private Duration pollInterval = Duration.ofSeconds(5);
        private int claimBatchSize = 20;
        private int maxAttempts = 3;
        private Duration backoffBase = Duration.ofSeconds(30);
        private Duration backoffCap = Duration.ofMinutes(10);
        private boolean scanRequired = true;
        private List<String> blockedExtensions = List.of(
                "exe", "bat", "cmd", "scr", "msi", "ps1", "vbs", "jar", "sh");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAdapter() {
            return adapter;
        }

        public void setAdapter(String adapter) {
            this.adapter = adapter;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getClaimBatchSize() {
            return claimBatchSize;
        }

        public void setClaimBatchSize(int claimBatchSize) {
            this.claimBatchSize = claimBatchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoffBase() {
            return backoffBase;
        }

        public void setBackoffBase(Duration backoffBase) {
            this.backoffBase = backoffBase;
        }

        public Duration getBackoffCap() {
            return backoffCap;
        }

        public void setBackoffCap(Duration backoffCap) {
            this.backoffCap = backoffCap;
        }

        public boolean isScanRequired() {
            return scanRequired;
        }

        public void setScanRequired(boolean scanRequired) {
            this.scanRequired = scanRequired;
        }

        public List<String> getBlockedExtensions() {
            return blockedExtensions;
        }

        public void setBlockedExtensions(List<String> blockedExtensions) {
            this.blockedExtensions = blockedExtensions;
        }
    }

    public static final class Reaper {

        private boolean enabled;
        private Duration pollInterval = Duration.ofSeconds(60);
        private int batchSize = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static final class QuotaReconcile {

        private boolean enabled;
        private Duration pollInterval = Duration.ofMinutes(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }
    }

    public static final class Purge {

        private boolean enabled;
        private Duration pollInterval = Duration.ofMinutes(10);
        private int batchSize = 100;
        private long defaultRetentionDays = 30;
        private String storage = "noop";
        private Minio minio = new Minio();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getDefaultRetentionDays() {
            return defaultRetentionDays;
        }

        public void setDefaultRetentionDays(long defaultRetentionDays) {
            this.defaultRetentionDays = defaultRetentionDays;
        }

        public String getStorage() {
            return storage;
        }

        public void setStorage(String storage) {
            this.storage = storage;
        }

        public Minio getMinio() {
            return minio;
        }

        public void setMinio(Minio minio) {
            this.minio = minio;
        }
    }

    /** Connection material for {@code iam.file.purge.storage=minio} (D-18). */
    public static final class Minio {

        private String endpoint = "";
        private String accessKey = "";
        private String secretKey = "";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
}
