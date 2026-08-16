package com.enterprise.iam.organization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = OrganizationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.web-application-type=none",
                "management.endpoints.access.default=none"
        })
class OrganizationApplicationTest {

    @Test
    void contextLoads() {
    }
}
