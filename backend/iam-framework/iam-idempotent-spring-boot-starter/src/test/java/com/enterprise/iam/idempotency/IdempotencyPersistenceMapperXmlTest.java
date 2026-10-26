package com.enterprise.iam.idempotency;

import com.enterprise.iam.idempotency.mapper.IdempotencyPersistenceMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyPersistenceMapperXmlTest {

    private static final String RESOURCE = "mapper/IdempotencyPersistenceMapper.xml";
    @Test
    void definesXmlStatementForEveryMapperMethod() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }

        String namespace = IdempotencyPersistenceMapper.class.getName();
        assertThat(IdempotencyPersistenceMapper.class.getDeclaredMethods())
                .extracting(Method::getName)
                .allSatisfy(method -> assertThat(configuration.hasStatement(namespace + "." + method)).isTrue());
    }
}
