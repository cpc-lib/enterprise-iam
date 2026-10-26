package com.enterprise.iam.fieldpermission;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class FieldPermissionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DynamicMapFieldEnforcer dynamicMapFieldEnforcer() {
        return new DynamicMapFieldEnforcer();
    }
}
