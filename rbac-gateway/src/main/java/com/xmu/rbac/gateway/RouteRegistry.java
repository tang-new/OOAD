package com.xmu.rbac.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 从契约清单构建唯一映射；静态段优先，启动拒绝同方法重复或歧义模板。 */
@Component
public final class RouteRegistry {
    private final List<Route> routes;
    private static final Pattern SAFE_PATH = Pattern.compile("/[A-Za-z0-9_~./-]*");

    public RouteRegistry(ObjectMapper mapper) throws IOException {
        try (InputStream input = new ClassPathResource("catalog/course-operations.json").getInputStream()) {
            routes = load(mapper.readTree(input).required("operations"));
        }
    }

    /** 仅允许固定课程契约中的课程登录匿名进入。 */
    public static boolean isCourseLogin(String method, String path) {
        return "POST".equals(method) && "/login".equals(path);
    }

    static List<Route> load(JsonNode operations) {
        List<Route> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode operation : operations) {
            String id = operation.required("operationId").asText();
            String method = operation.required("method").asText();
            String path = operation.required("path").asText();
            if (!id.matches("[A-Za-z][A-Za-z0-9_]*") || !ids.add(id)
                    || !method.matches("GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") || !path.startsWith("/")) {
                throw new IllegalStateException("课程清单非法");
            }
            boolean publicRoute = "PUBLIC".equals(operation.path("accessPolicy").asText());
            boolean login = isCourseLogin(method, path);
            if (publicRoute != login || "courseLogin".equals(id) != login) {
                throw new IllegalStateException("课程公开路由与契约不符");
            }
            Route route = new Route(method, path, id);
            for (Route existing : result) {
                if (existing.method.equals(method) && existing.overlaps(route)
                        && !existing.dominates(route) && !route.dominates(existing)) {
                    throw new IllegalStateException("课程清单路由歧义");
                }
            }
            result.add(route);
        }
        result.sort(Comparator.comparingInt(Route::staticCount).reversed());
        return List.copyOf(result);
    }

    public String resolve(String method, String rawPath) {
        validatePath(rawPath);
        for (Route route : routes) {
            if (route.method.equals(method) && route.matches(rawPath)) { return route.id; }
        }
        throw new GatewayFailure(404, "COURSE_OPERATION_NOT_FOUND");
    }

    public static void validatePath(String path) {
        if (path == null || path.length() > 4096 || !SAFE_PATH.matcher(path).matches()
                || path.contains("//") || path.contains("\\") || path.contains("%")
                || Pattern.compile("(^|/)[.]{1,2}(/|$)").matcher(path).find()) {
            throw new GatewayFailure(400, "INVALID_COURSE_PATH");
        }
    }

    static final class Route {
        final String method;
        final String id;
        final String[] segments;
        Route(String method, String path, String id) {
            this.method = method;

            this.id = id;
            this.segments = path.split("/", -1);
        }
        boolean template(String value) { return value.matches("[{][A-Za-z][A-Za-z0-9_]*[}]"); }
        int staticCount() {
            int count = 0;
            for (String segment : segments) { if (!template(segment)) { count++; } }
            return count;
        }
        boolean matches(String path) {
            String[] actual = path.split("/", -1);
            if (actual.length != segments.length) { return false; }
            for (int i = 0; i < actual.length; i++) {
                if (!(segments[i].equals(actual[i]) || template(segments[i]) && !actual[i].isEmpty())) { return false; }
            }
            return true;
        }
        boolean overlaps(Route other) {
            if (segments.length != other.segments.length) { return false; }
            for (int i = 0; i < segments.length; i++) {
                if (!segments[i].equals(other.segments[i]) && !template(segments[i]) && !template(other.segments[i])) { return false; }
            }
            return true;
        }
        boolean dominates(Route other) {
            boolean strict = false;
            for (int i = 0; i < segments.length; i++) {
                if (template(segments[i]) && !template(other.segments[i])) { return false; }
                if (!template(segments[i]) && template(other.segments[i])) { strict = true; }
            }
            return strict;
        }
    }
}
