package com.enterprise.iam.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

@ConfigurationProperties("iam.outbox.relay")
public final class OutboxRelayProperties {

    private static final Pattern INSTANCE = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private boolean enabled;
    private int batchSize = 50;
    private int maxAttempts = 10;
    private Duration pollDelay = Duration.ofSeconds(1);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration initialBackoff = Duration.ofSeconds(1);
    private Duration maximumBackoff = Duration.ofMinutes(5);
    private String instanceId;

    public void validateEnabled() {
        if (!enabled) {
            return;
        }
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalStateException("iam.outbox.relay.batch-size must be 1..100");
        }
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalStateException("iam.outbox.relay.max-attempts must be 1..20");
        }
        requireDuration(pollDelay, Duration.ofMillis(100), Duration.ofMinutes(1), "poll-delay");
        requireDuration(leaseDuration, Duration.ofSeconds(5), Duration.ofMinutes(5), "lease-duration");
        requireDuration(initialBackoff, Duration.ofMillis(100), Duration.ofMinutes(1), "initial-backoff");
        requireDuration(maximumBackoff, initialBackoff, Duration.ofHours(1), "maximum-backoff");
        if (instanceId != null && !INSTANCE.matcher(instanceId).matches()) {
            throw new IllegalStateException("iam.outbox.relay.instance-id is invalid");
        }
    }

    public String resolveInstanceId(String applicationName) {
        if (instanceId != null && !instanceId.isBlank()) {
            return instanceId;
        }
        String prefix = applicationName == null ? "iam-service" : applicationName;
        prefix = prefix.replaceAll("[^A-Za-z0-9._-]", "-");
        if (prefix.isBlank() || prefix.length() > 64) {
            prefix = "iam-service";
        }
        return prefix + ":" + UUID.randomUUID();
    }

    private static void requireDuration(
            Duration value,
            Duration minimum,
            Duration maximum,
            String name) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(
                    "iam.outbox.relay." + name + " must be between " + minimum + " and " + maximum);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getPollDelay() {
        return pollDelay;
    }

    public void setPollDelay(Duration pollDelay) {
        this.pollDelay = pollDelay;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public Duration getMaximumBackoff() {
        return maximumBackoff;
    }

    public void setMaximumBackoff(Duration maximumBackoff) {
        this.maximumBackoff = maximumBackoff;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}
