package com.enterprise.iam.organization.infrastructure.persistence;

import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationPersistenceMapperXmlTest {

    @Test
    void definesXmlStatementForEveryMapperMethod() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/OrganizationPersistenceMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = OrganizationPersistenceMapper.class.getName();
        assertThat(OrganizationPersistenceMapper.class.getDeclaredMethods())
                .extracting(Method::getName)
                .allSatisfy(method -> assertThat(configuration.hasStatement(namespace + "." + method)).isTrue());
    }
}
