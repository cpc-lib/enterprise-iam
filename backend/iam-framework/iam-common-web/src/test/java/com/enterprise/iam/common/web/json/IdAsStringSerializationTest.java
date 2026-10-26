package com.enterprise.iam.common.web.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class IdAsStringSerializationTest {

    record Ids(long userId, Long tenantId, @NumericLong long version, @NumericLong Long partSize) {
    }

    private ObjectMapper configuredMapper() {
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new IamWebJacksonAutoConfiguration().iamIdAsStringCustomizer();
        ObjectMapper mapper = new ObjectMapper();
        SimpleModuleHolder.apply(customizer, mapper);
        return mapper;
    }

    @Test
    void ids_serialize_as_decimal_strings_including_snowflake_scale() throws Exception {
        ObjectMapper mapper = configuredMapper();
        long snowflakeId = 812345678901234567L;

        String json = mapper.writeValueAsString(new Ids(snowflakeId, 9L, 7L, 5242880L));

        assertThat(json).contains("\"userId\":\"812345678901234567\"");
        assertThat(json).contains("\"tenantId\":\"9\"");
    }

    @Test
    void numeric_long_annotation_keeps_json_number() throws Exception {
        ObjectMapper mapper = configuredMapper();

        String json = mapper.writeValueAsString(new Ids(1L, 2L, 7L, 5242880L));

        assertThat(json).contains("\"version\":7");
        assertThat(json).contains("\"partSize\":5242880");
    }

    @Test
    void long_values_in_collections_and_maps_also_serialize_as_strings() throws Exception {
        ObjectMapper mapper = configuredMapper();

        String json = mapper.writeValueAsString(Map.of("ids", List.of(1L, 2L)));

        assertThat(json).isEqualTo("{\"ids\":[\"1\",\"2\"]}");
    }

    @Test
    void quoted_ids_deserialize_back_to_long() throws Exception {
        ObjectMapper mapper = configuredMapper();

        Ids value = mapper.readValue("{\"userId\":\"42\",\"tenantId\":\"7\",\"version\":\"1\",\"partSize\":\"10\"}", Ids.class);

        assertThat(value.userId()).isEqualTo(42L);
        assertThat(value.tenantId()).isEqualTo(7L);
        assertThat(value.version()).isEqualTo(1L);
    }

    /** Test helper bridging the Boot customizer onto a bare ObjectMapper. */
    private static final class SimpleModuleHolder {
        static void apply(Jackson2ObjectMapperBuilderCustomizer customizer, ObjectMapper mapper) {
            var builder = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
            customizer.customize(builder);
            builder.configure(mapper);
        }
    }
}
