package com.xmu.rbac.structured;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.config.WebConfig;
import com.xmu.rbac.structured.dto.Inputs;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** 冻结JSON合同不允许把数字账号自动转成字符串账号。 */
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
}
