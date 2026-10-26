package com.enterprise.iam.auth.interfaces.http;

import com.enterprise.iam.auth.infrastructure.config.AccessTokenPublicJwk;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessTokenJwksControllerTest {

    @Test
    void publishesRawJwksJsonWithKidHint() throws Exception {
        Map<String, Object> jwk = Map.of(
                "kty", "EC",
                "crv", "P-256",
                "x", "MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4",
                "y", "4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM",
                "kid", "auth-key-1",
                "alg", "ES256",
                "use", "sig");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new AccessTokenJwksController(new AccessTokenPublicJwk(jwk))).build();

        mockMvc.perform(get(AccessTokenJwksController.JWKS_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].kid").value("auth-key-1"))
                .andExpect(jsonPath("$.keys[0].kty").value("EC"))
                .andExpect(jsonPath("$.keys[0].crv").value("P-256"))
                .andExpect(jsonPath("$.keys[0].alg").value("ES256"));
    }
}
