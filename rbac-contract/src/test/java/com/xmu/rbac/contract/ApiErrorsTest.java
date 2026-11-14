package com.xmu.rbac.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiErrorsTest {
    @Test
    void projectsIndependentInternalReasonsToIdenticalPublicVocabulary() {
        Map<String, String> aliases = Map.ofEntries(
            Map.entry("TOKEN_INVALID", "UNAUTHENTICATED"), Map.entry("TOKEN_REQUIRED", "UNAUTHENTICATED"),
            Map.entry("SYSTEM_DISABLED", "UNAUTHENTICATED"), Map.entry("CREDENTIAL_INVALID", "INVALID_CREDENTIALS"),
            Map.entry("INVALID_REQUEST", "VALIDATION_ERROR"), Map.entry("INVALID_PAGE", "VALIDATION_ERROR"),
            Map.entry("INVALID_PAGE_OR_TIME", "VALIDATION_ERROR"), Map.entry("INVALID_QUERY", "VALIDATION_ERROR"),
            Map.entry("INVALID_AUDIT_TYPE", "VALIDATION_ERROR"), Map.entry("INVALID_AUDIT_OBJECT", "VALIDATION_ERROR"),
            Map.entry("AUDIT_OBJECT_MISMATCH", "VALIDATION_ERROR"), Map.entry("AUDIENCE_FORBIDDEN", "AUDIENCE_MISMATCH"),
            Map.entry("DEPARTMENT_OUT_OF_SCOPE", "OUT_OF_MANAGE_SCOPE"), Map.entry("GRANT_EXCEEDS_ACTOR", "PRIVILEGE_ESCALATION"),
            Map.entry("AUDIT_SERVICE_FORBIDDEN", "AUDIT_SERVICE_REQUIRED"), Map.entry("FORBIDDEN", "PERMISSION_DENIED"),
            Map.entry("API_UNREGISTERED", "API_NOT_REGISTERED"));
        aliases.forEach((reason, code) -> assertEquals(code, ApiErrors.of(403, reason).code()));
        assertEquals("AUDIT_DUPLICATE_CONFLICT", ApiErrors.of(400, "AUDIT_DUPLICATE_CONFLICT").code());
        assertEquals(400, ApiErrors.of(409, "CROSS_SYSTEM_ROLE").status());
        assertEquals(400, ApiErrors.of(409, "CROSS_SYSTEM_MAPPING").status());
    }

    @Test
    void usesOnlyStableMessagesNotImplementationExceptionDetails() {
        for (int status : new int[]{400, 401, 403, 404, 409, 503}) {
            assertEquals(ApiErrors.of(status, "REASON_A").message(), ApiErrors.of(status, "REASON_B").message());
            assertEquals(status, ApiErrors.of(status, "REASON_A").status());
        }
    }
}
