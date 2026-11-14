package com.xmu.rbac.structured.config;
import com.xmu.rbac.structured.controller.PermissionInterceptor;
import com.xmu.rbac.structured.service.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
/** Spring技术装配；路由与OAS双向核验，不承载管理业务规则。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final PermissionInterceptor interceptor;
    public WebConfig(PermissionInterceptor interceptor) {
        this.interceptor = interceptor;
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
    /** 严格JSON类型，禁止字符串和数值隐式转换掩盖合同输入错误。 */
    @Bean
    public static org.springframework.boot.autoconfigure.jackson
        .Jackson2ObjectMapperBuilderCustomizer
        strictJsonTypes() {
        return builder -> {
            builder.featuresToDisable(
                com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS,
                com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT);
            builder.postConfigurer(mapper
                -> mapper.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
                    .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,
                        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
                    .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,
                        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
                    .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean,
                        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail));
        };
    }

    @Bean
    public static PasswordEncoder passwords() {
        return new CompatiblePasswordEncoder();
    }
    @Bean
    public ApplicationRunner startup(BootstrapService bootstrap, RouteRegistry routes,
        @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
        return args -> {
            Set<String> implemented = new HashSet<>();
            mappings.getHandlerMethods().forEach((info, method) -> {
                if (!method.getBeanType().getPackageName().startsWith(
                        "com.xmu.rbac.structured.controller"))
                    return;
                for (String pattern : info.getPatternValues())
                    for (org.springframework.web.bind.annotation.RequestMethod verb :
                        info.getMethodsCondition().getMethods()) {
                        if (routes.route(verb.name(), pattern) == null)
                            throw new IllegalStateException("HTTP路由未登记OAS");
                        implemented.add(verb.name() + " " + pattern);
                    }
            });
            for (RouteRegistry.Route route : routes.all())
                if (!route.path().startsWith("/api/v1/actuator/")
                    && !implemented.contains(route.method() + " " + route.path()))
                    throw new IllegalStateException("OAS路由未实现: " + route.operationId());
            bootstrap.initialize();
        };
    }
}
