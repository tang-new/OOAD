package com.xmu.rbac.gateway;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RouteRegistryTest {
    final ObjectMapper mapper = new ObjectMapper();
    @Test void everyRealOperationAndCopyCourseRole() throws Exception {
        RouteRegistry registry = new RouteRegistry(mapper);
        JsonNode manifest = mapper.readTree(getClass().getResourceAsStream("/catalog/course-operations.json"));
        assertEquals(80, manifest.required("operations").size());
        for (JsonNode operation : manifest.required("operations")) {
            String path = operation.required("path").asText().replaceAll("[{][^}]+[}]", "123");
            assertEquals(operation.required("operationId").asText(), registry.resolve(operation.required("method").asText(), path));
            if (operation.required("operationId").asText().equals("copyCourse")) {
                assertEquals("teacher", operation.required("requiredRole").asText());
                assertNotEquals("CC", operation.path("permissionCode").asText());
            }
        }
        for (JsonNode excluded : manifest.required("excludedProviderOperations")) {
            assertThrows(GatewayFailure.class, () -> registry.resolve(excluded.required("method").asText(), excluded.required("path").asText()));
        }
    }
    JsonNode ops(String... values) throws Exception { return mapper.readTree("[" + String.join(",", values) + "]"); }
    String op(String id, String method, String path) { return "{\"operationId\":\""+id+"\",\"method\":\""+method+"\",\"path\":\""+path+"\"}"; }
    @Test void duplicateAndAmbiguityFailAtStartup() throws Exception {
        assertThrows(IllegalStateException.class, () -> RouteRegistry.load(ops(op("one","GET","/x/{id}"),op("two","GET","/x/{name}"))));
        assertThrows(IllegalStateException.class, () -> RouteRegistry.load(ops(op("one","GET","/x/{id}"),op("two","GET","/{name}/x"))));
        assertThrows(IllegalStateException.class, () -> RouteRegistry.load(ops(op("one","GET","/x"),op("two","GET","/x"))));
        assertThrows(IllegalStateException.class, () -> RouteRegistry.load(ops(op("one","GET","/x"),op("one","POST","/x"))));
        for (String bad : List.of(op("bad-id","GET","/x"),op("one","TRACE","/x"),op("one","GET","relative"))) {
            assertThrows(IllegalStateException.class, () -> RouteRegistry.load(ops(bad)));
        }
        List<RouteRegistry.Route> routes = RouteRegistry.load(ops(op("dynamic","GET","/x/{id}"),op("staticOp","GET","/x/copy"),op("post","POST","/x/{id}")));
        assertEquals("staticOp", routes.getFirst().id);
        assertTrue(routes.getFirst().matches("/x/copy"));
        assertFalse(routes.getFirst().matches("/x/no"));
        assertFalse(routes.getLast().matches("/x/"));
    }
    @Test void unsafePathsAndUnknownMethod() throws Exception {
        for (String path : List.of("/..", "/./x", "/a/../", "/a//b", "/%2f", "/%252e", "/a;b", "/a" + (char)92 + "b", "http://evil", "x", "/" + "x".repeat(4097))) {
            assertThrows(GatewayFailure.class, () -> RouteRegistry.validatePath(path), path);
        }
        assertThrows(GatewayFailure.class, () -> RouteRegistry.validatePath(null));
        assertThrows(GatewayFailure.class, () -> new RouteRegistry(mapper).resolve("TRACE", "/me"));
        RouteRegistry.validatePath("/a-b_1.2~3");
    }
    @Test void boundedBytesAndConnectionTokens() throws Exception {
        assertArrayEquals(new byte[]{0,1,2}, BoundedBody.read(new ByteArrayInputStream(new byte[]{0,1,2}),3,413));
        assertEquals(413, assertThrows(GatewayFailure.class, () -> BoundedBody.read(new ByteArrayInputStream(new byte[]{0,1}),1,413)).status());
        assertEquals(java.util.Set.of(), ProxyClient.connectionTokens(null));
        assertEquals(java.util.Set.of("authorization","content-type"), ProxyClient.connectionTokens("Authorization, Content-Type"));
    }
}
