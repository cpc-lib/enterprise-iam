package com.enterprise.iam.authorization.domain.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationRequestTest {

    @Test
    void defensivelyCopiesPolicyContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("network", "CORPORATE");
        AuthorizationRequest request = request(context);

        context.put("network", "PUBLIC");

        assertThat(request.context()).containsEntry("network", "CORPORATE");
        assertThatThrownBy(() -> request.context().put("new", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNonScalarContextValues() {
        assertThatThrownBy(() -> request(Map.of("nested", Map.of("unsafe", true))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scalar");
    }

    @Test
    void acceptsJsonNullContextValueWithoutLosingImmutability() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("optionalRisk", null);

        AuthorizationRequest request = request(context);

        assertThat(request.context()).containsEntry("optionalRisk", null);
        assertThatThrownBy(() -> request.context().remove("optionalRisk"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMoreThanFrozenContextPropertyLimit() {
        Map<String, Object> context = new LinkedHashMap<>();
        for (int index = 0; index < 33; index++) {
            context.put("key" + index, index);
        }

        assertThatThrownBy(() -> request(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void normalizesTeamIdsListFromContext() {
        AuthorizationRequest request = request(
                Map.of("teamIds", java.util.List.of(7, "8", 7, "9")));

        assertThat(TeamIds.from(request.context())).containsExactly(7L, 8L, 9L);
    }

    @Test
    void rejectsNonPositiveTeamIds() {
        assertThatThrownBy(() -> request(Map.of("teamIds", java.util.List.of(1, -2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamIds");
    }

    @Test
    void absentTeamIdsNormalizeToEmpty() {
        assertThat(TeamIds.from(request(Map.of("network", "CORPORATE")).context())).isEmpty();
    }

    private AuthorizationRequest request(Map<String, Object> context) {
        return new AuthorizationRequest(1, 2, 3, 4, 5, null, 1, "request-1", context);
    }
}
