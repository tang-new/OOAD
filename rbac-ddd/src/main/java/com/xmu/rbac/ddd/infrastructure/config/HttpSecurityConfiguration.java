package com.xmu.rbac.ddd.infrastructure.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** bearer 校验由接入层统一执行；禁用交互式登录与 session。 */
@Configuration
public class HttpSecurityConfiguration {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable())
                .sessionManagement(
                        c ->
                                c.sessionCreationPolicy(
                                        org.springframework.security.config.http
                                                .SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c -> c.anyRequest().permitAll())
                .httpBasic(c -> c.disable())
                .formLogin(c -> c.disable())
                .build();
    }
}
