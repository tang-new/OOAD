package com.xmu.rbac.gateway;

import com.xmu.rbac.gateway.config.GatewayProperties;
import java.net.URI;
import java.util.List;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {
    GatewayProperties properties(String base, boolean allowHttp, int timeout) {
        return new GatewayProperties(URI.create(base), URI.create("https://course.example"), "issuer",
                URI.create("https://identity.example/.well-known/jwks.json"), allowHttp, timeout, 1024, 1024);
    }
    @Test void untrustedOrMalformedOriginsAndUnboundedConfigurationFail() {
        for (String uri : List.of("http://identity.example", "https://user:password@identity.example", "https://identity.example/path",
                "https://identity.example?url=http://evil", "https://identity.example#fragment", "file:///etc/passwd", "/relative")) {
            assertThrows(IllegalArgumentException.class, () -> properties(uri, false, 2000));
        }
        assertNotNull(properties("http://identity.example", true, 2000));
        assertNotNull(properties("https://identity.example/", false, 2000));
        try (jakarta.validation.ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(properties("https://identity.example", false, 0)).isEmpty());
            assertFalse(factory.getValidator().validate(properties("https://identity.example", false, 30001)).isEmpty());
            assertTrue(factory.getValidator().validate(properties("https://identity.example", false, 2000)).isEmpty());
        }
    }
}
