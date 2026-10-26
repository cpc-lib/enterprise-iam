package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationArtifactMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.ShareAclProjectionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationMapperXmlTest {

    @Test
    void allMapperXmlFilesParseAndRegisterCriticalStatements() throws Exception {
        Configuration configuration = new Configuration();
        parse(configuration, "mapper/AuthorizationArtifactMapper.xml");
        parse(configuration, "mapper/AuthorizationPolicyMapper.xml");
        parse(configuration, "mapper/ShareAclProjectionMapper.xml");

        assertAllMethodsMapped(configuration, AuthorizationArtifactMapper.class);
        assertAllMethodsMapped(configuration, AuthorizationPolicyMapper.class);
        assertAllMethodsMapped(configuration, ShareAclProjectionMapper.class);
    }

    private static void parse(Configuration configuration, String resource) throws Exception {
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static void assertAllMethodsMapped(Configuration configuration, Class<?> mapperType) {
        assertThat(mapperType.getDeclaredMethods())
                .extracting(Method::getName)
                .allSatisfy(method -> assertThat(
                        configuration.hasStatement(mapperType.getName() + "." + method)).isTrue());
    }
}
