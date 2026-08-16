package com.enterprise.iam.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "management.endpoints.access.default=none"
        })
class GatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}
