package com.enterprise.iam.common.mq;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Fail-safe MQ wiring: every bean below exists only when
 * {@code iam.mq.enabled=true} is explicitly set. Default services keep no
 * broker connection factories in use and start no listener containers.
 */
@AutoConfiguration
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "iam.mq", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IamMqProperties.class)
public class IamMqAutoConfiguration {

    @Bean("iamMqObjectMapper")
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper iamMqObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean("iamMqMessageConverter")
    @ConditionalOnMissingBean(MessageConverter.class)
    MessageConverter iamMqMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean("iamMqConnectionFactory")
    @ConditionalOnMissingBean(ConnectionFactory.class)
    ConnectionFactory iamMqConnectionFactory(IamMqProperties properties) {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(properties.getHost());
        factory.setPort(properties.getPort());
        factory.setVirtualHost(properties.getVirtualHost());
        factory.setUsername(properties.getUsername());
        factory.setPassword(properties.getPassword());
        return factory;
    }

    @Bean("iamMqRabbitTemplate")
    @ConditionalOnMissingBean(RabbitTemplate.class)
    RabbitTemplate iamMqRabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean("iamDomainEventExchange")
    @ConditionalOnMissingBean(name = "iamDomainEventExchange")
    TopicExchange iamDomainEventExchange(IamMqProperties properties) {
        return new TopicExchange(properties.getExchangeName(), true, false);
    }

    @Bean("amqpDomainEventGateway")
    @ConditionalOnMissingBean(DomainEventGateway.class)
    DomainEventGateway amqpDomainEventGateway(
            RabbitTemplate rabbitTemplate,
            MessageConverter messageConverter) {
        return new AmqpDomainEventGateway(rabbitTemplate, messageConverter);
    }
}
