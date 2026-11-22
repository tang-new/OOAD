package com.xmu.rbac.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.config.WebConfig;
import com.xmu.rbac.structured.dto.Inputs;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** 请求输入的 JSON 类型和字段长度边界。 */
class JsonBoundaryTest {
    @Test
    void numericUsernameIsNotAStringCompatibilityAlias() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        WebConfig.strictJsonTypes().customize(builder);
        ObjectMapper mapper = builder.build();
        assertThatThrownBy(
            ()
                -> mapper.readValue("{\"username\":123456,\"password\":\"SyntheticPassword123!\","
                        + "\"systems\":[\"rbac-center\"]}",
                    Inputs.Login.class))
            .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"permission", "operationId"})
    void suppliedAuthorizationSelectorHasLengthBetweenOneAnd128(String field) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validateValue(Inputs.Check.class, field, "")).hasSize(1);
            assertThat(validator.validateValue(Inputs.Check.class, field, "x".repeat(129)))
                .hasSize(1);
            assertThat(validator.validateValue(Inputs.Check.class, field, "x")).isEmpty();
            assertThat(validator.validateValue(Inputs.Check.class, field, "x".repeat(128)))
                .isEmpty();
            assertThat(validator.validateValue(Inputs.Check.class, field, null)).isEmpty();
        }
    }
}
