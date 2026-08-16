package com.enterprise.iam.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = JobApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.web-application-type=none",
                "management.endpoints.access.default=none"
        })
class JobApplicationTest {

    @Test
    void contextLoads() {
    }
}
