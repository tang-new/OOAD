package com.xmu.rbac.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CourseProxyFilterTest {
    private HttpServer upstream;
    private final AtomicReference<String> forwarded = new AtomicReference<>();

    @BeforeEach
    void startUpstream() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            try (exchange) {
                forwarded.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                        + exchange.getRequestHeaders().getFirst("Authorization") + " "
                        + exchange.getRequestHeaders().getFirst("X-Audit-Service-Key") + " "
                        + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        upstream.start();
    }

    @AfterEach
    void stopUpstream() {
        upstream.stop(0);
    }

    @Test
    void allowUsesCatalogAndPreservesBusinessRequestAndResponse() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/terms");
        request.addHeader("Authorization", "Bearer test-token");
        request.addHeader("X-Audit-Service-Key", "untrusted");
        request.addHeader("X-Operation-Id", "forged");
        request.setQueryString("a=%2F");
        request.setContent("{\"name\":\"Term\"}".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter((bearer, operation) -> {
            assertEquals("Bearer test-token", bearer);
            assertEquals("createTerm", operation);
        }).doFilter(request, response, chain);

        assertEquals(201, response.getStatus());
        assertEquals("{\"id\":1}", response.getContentAsString());
        assertEquals("POST /terms?a=%2F Bearer test-token null {\"name\":\"Term\"}", forwarded.get());
        assertNull(chain.getRequest());
    }

    @Test
    void deniesMissingTokenUnknownRouteAndRejectedOperationBeforeForwarding() throws Exception {
        var filter = filter((bearer, operation) -> {
            throw new GatewayFailure(403, "COURSE_ACCESS_DENIED");
        });
        var missing = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/terms"), missing, new MockFilterChain());
        assertEquals(401, missing.getStatus());
        var unknown = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/unknown"), unknown, new MockFilterChain());
        assertEquals(404, unknown.getStatus());
        var login = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/login"), login, new MockFilterChain());
        assertEquals(404, login.getStatus());
        var request = new MockHttpServletRequest("GET", "/api/terms");
        request.addHeader("Authorization", "Bearer test-token");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(403, response.getStatus());
        assertNull(forwarded.get());
    }

    @Test
    void preservesCenterManagementRoutes() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/users");
        var chain = new MockFilterChain();
        filter((bearer, operation) -> fail("Center route entered course authorization"))
                .doFilter(request, new MockHttpServletResponse(), chain);
        assertSame(request, chain.getRequest());
        assertNull(forwarded.get());
    }

    private CourseProxyFilter filter(CourseProxyFilter.Authorize authorize) throws Exception {
        var origin = URI.create("http://127.0.0.1:" + upstream.getAddress().getPort());
        return new CourseProxyFilter(new ObjectMapper(), CourseUpstream.defaults(origin), authorize);
    }
}
