package com.enterprise.iam.audit.infrastructure.persistence;

import com.enterprise.iam.audit.infrastructure.persistence.mapper.AuditLogPersistenceMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogPersistenceMapperXmlTest {

    @Test
    void definesXmlStatementForEveryMapperMethod() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/AuditLogPersistenceMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = AuditLogPersistenceMapper.class.getName();
        assertThat(AuditLogPersistenceMapper.class.getDeclaredMethods())
                .extracting(Method::getName)
                .allSatisfy(method -> assertThat(configuration.hasStatement(namespace + "." + method)).isTrue());
    }
}
