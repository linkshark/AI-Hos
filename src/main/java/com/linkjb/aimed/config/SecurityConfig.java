package com.linkjb.aimed.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.dto.response.ApiErrorResponse;
import com.linkjb.aimed.config.skywalk.RequestTraceFilter;
import com.linkjb.aimed.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AppSecurityProperties appSecurityProperties;

    public SecurityConfig(AppSecurityProperties appSecurityProperties) {
        this.appSecurityProperties = appSecurityProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Streaming responses trigger an async/error re-dispatch after the
                        // controller has already started writing the body. Do not re-apply
                        // endpoint authorization on those internal dispatches.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                        .requestMatchers("/error", "/doc.html", "/favicon.ico", "/webjars/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health").permitAll()
                        .requestMatchers(
                                "/aimed/auth/login",
                                "/aimed/auth/register",
                                "/aimed/auth/register/send-code",
                                "/aimed/auth/password/send-code",
                                "/aimed/auth/password/reset",
                                "/aimed/auth/refresh",
                                "/aimed/mcp/weather",
                                "/api/aimed/mcp/weather"
                        ).permitAll()
                        .requestMatchers("/aimed/auth/me", "/aimed/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/aimed/dept/tree").authenticated()
                        .requestMatchers("/aimed/dept/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/aimed/chat/sessions").authenticated()
                        .requestMatchers(HttpMethod.GET, "/aimed/chat/provider-config").authenticated()
                        .requestMatchers(HttpMethod.GET, "/aimed/chat/histories", "/aimed/chat/histories/*").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/aimed/chat/histories/*/rename", "/aimed/chat/histories/*/pin").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/aimed/chat/histories/*").authenticated()
                        .requestMatchers("/aimed/knowledge/**").hasRole("ADMIN")
                        .requestMatchers("/aimed/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/aimed/chat").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeErrorResponse(objectMapper, request, response, HttpStatus.UNAUTHORIZED, "请先登录后再继续操作"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeErrorResponse(objectMapper, request, response, HttpStatus.FORBIDDEN, "当前账号没有权限访问该资源"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(new ArrayList<>(appSecurityProperties.getAllowedOriginPatterns()));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeErrorResponse(ObjectMapper objectMapper,
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    HttpStatus status,
                                    String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = new ApiErrorResponse();
        body.setTimestamp(OffsetDateTime.now());
        body.setTraceId(MDC.get(RequestTraceFilter.TRACE_ID_KEY));
        body.setPath(request.getRequestURI());
        body.setMessage(message);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
