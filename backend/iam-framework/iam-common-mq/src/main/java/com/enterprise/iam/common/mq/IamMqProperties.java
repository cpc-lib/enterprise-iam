package com.enterprise.iam.common.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Broker settings for IAM domain events. Disabled by default so services
 * remain broker-free until {@code iam.mq.enabled=true} is explicitly set.
 */
@ConfigurationProperties(prefix = "iam.mq")
public final class IamMqProperties {

    /** Master fail-safe switch; when false no MQ beans or listeners are created. */
    private boolean enabled;
    private String host = "rabbitmq";
    private int port = 5672;
    private String virtualHost = "/";
    private String username = "guest";
    private String password = "guest";
    private String exchangeName = "iam.domain.events";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }
}
