package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPersistenceMapperXmlTest {

    private static final String RESOURCE = "mapper/AuthPersistenceMapper.xml";
    @Test
    void definesXmlStatementForEveryMapperMethod() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }

        String namespace = AuthPersistenceMapper.class.getName();
        assertThat(AuthPersistenceMapper.class.getDeclaredMethods())
                .extracting(Method::getName)
                .allSatisfy(method -> assertThat(configuration.hasStatement(namespace + "." + method)).isTrue());
    }
}
