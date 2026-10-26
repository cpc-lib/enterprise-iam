package com.enterprise.iam.file;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hermetic full-context test for the D-4 idempotency filter on the file
 * surface: no MySQL/Redis, in-memory replay store, real servlet chain through
 * a probe endpoint matching the protected upload path.
 */
@SpringBootTest(
        classes = {FileApplication.class, FileIdempotencyEnforcementTest.ProbeEndpoints.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "iam.idempotency.enabled=true",
                "iam.idempotency.paths=/api/v1/file-uploads",
                "management.endpoints.access.default=none",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
class FileIdempotencyEnforcementTest {

    private static final String KEY = "file-idempotency-key-0001";

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void missingIdempotencyKeyIsRejectedWith400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", "7");

        ResponseEntity<String> response = rest.postForEntity(
                url(), new HttpEntity<>("{\"v\":1}", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("IAM_IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void missingTenantContextIsRejectedWith400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", KEY + "-tenant");

        ResponseEntity<String> response = rest.postForEntity(
                url(), new HttpEntity<>("{\"v\":1}", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("IAM_TENANT_CONTEXT_REQUIRED");
    }

    @Test
    void sameKeyWithDifferentBodyIsRejectedWith409AfterSuccess() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", "7");
        headers.set("Idempotency-Key", KEY + "-conflict");

        ResponseEntity<String> first = rest.postForEntity(
                url(), new HttpEntity<>("{\"v\":1}", headers), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> replayed = rest.postForEntity(
                url(), new HttpEntity<>("{\"v\":2}", headers), String.class);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(replayed.getBody()).contains("IAM_IDEMPOTENCY_REPLAY_CONFLICT");
    }

    private String url() {
        return "http://localhost:" + port + "/api/v1/file-uploads";
    }

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    static class ProbeEndpoints {

        @PostMapping("/api/v1/file-uploads")
        ResponseEntity<String> create() {
            return ResponseEntity.status(HttpStatus.CREATED).body("{\"accepted\":true}");
        }
    }
}
