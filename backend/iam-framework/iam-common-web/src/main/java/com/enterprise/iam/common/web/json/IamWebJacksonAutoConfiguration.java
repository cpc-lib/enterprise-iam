package com.enterprise.iam.common.web.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Web 层 Jackson 自动装配。
 *
 * <p>默认开启（ID 十进制字符串是 OpenAPI 冻结契约，不是可选特性）；
 * 如需临时关闭可用 {@code iam.web.id-as-string.enabled=false}。
 */
@AutoConfiguration
@ConditionalOnClass(SimpleModule.class)
@ConditionalOnProperty(prefix = "iam.web.id-as-string", name = "enabled", matchIfMissing = true)
public class IamWebJacksonAutoConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer iamIdAsStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("iam-id-as-string");
            IdAsStringSerializer serializer = new IdAsStringSerializer();
            module.addSerializer(Long.class, serializer);
            module.addSerializer(Long.TYPE, serializer);
            builder.modulesToInstall(module);
        };
    }
}
