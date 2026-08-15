package com.hr.auth.security;

import com.hr.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置，对齐 Flask middleware/auth.py + 角色 guard。
 * 白名单端点直接放行，其余端点要求认证。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 公开端点（不需要 JWT），对齐 Flask AUTH_WHITELIST。
     */
    public static final List<String> WHITELIST = List.of(
            "/api/auth/login",
            "/api/auth/csrf",
            "/api/auth/register",
            "/api/auth/setup-status",
            "/api/auth/setup",
            "/api/auth/forgot-password",
            "/api/auth/verify-reset-code",
            "/api/health",
            "/api/v1/health",
            "/confirm/**",
            "/api/confirm/**",
            "/error",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-ui.html"
    );

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;
    private final boolean cookieSecure;
    private final boolean hstsEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper,
                          @Value("${app.security.allowed-origins:http://localhost:7100,http://127.0.0.1:7100}") String allowedOrigins,
                          @Value("${app.security.cookie-secure:false}") boolean cookieSecure,
                          @Value("${app.security.hsts-enabled:false}") boolean hstsEnabled) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        this.cookieSecure = cookieSecure;
        this.hstsEnabled = hstsEnabled;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 兼容 werkzeug scrypt/bcrypt/pbkdf2 生成的哈希（Spring BCrypt 可校验 $2a$ 格式）
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository cookieRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        cookieRepository.setCookieCustomizer(cookie -> cookie
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/"));
        // Keep the double-submit cookie stable across read-only requests. Spring's deferred
        // repository may otherwise save null after a GET and make the next mutation fail.
        CsrfTokenRepository csrfRepository = new CsrfTokenRepository() {
            @Override
            public CsrfToken generateToken(HttpServletRequest request) {
                return cookieRepository.generateToken(request);
            }

            @Override
            public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
                if (token != null) {
                    cookieRepository.saveToken(token, request, response);
                }
            }

            @Override
            public CsrfToken loadToken(HttpServletRequest request) {
                return cookieRepository.loadToken(request);
            }
        };
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/setup",
                                "/api/auth/forgot-password",
                                "/api/auth/verify-reset-code",
                                "/api/confirm/**",
                                "/confirm/**"))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITELIST.toArray(new String[0])).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    ApiResponse.error("UNAUTHORIZED", "请先登录")));
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    ApiResponse.error("FORBIDDEN", "无权限访问")));
                        })
                )
                .headers(headers -> {
                    headers.frameOptions(frame -> frame.deny())
                            .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                            .referrerPolicy(referrer -> referrer.policy(
                                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                            .contentSecurityPolicy(csp -> csp.policyDirectives(
                                    "default-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                            .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                    "camera=(), microphone=(), geolocation=(), payment=()"));
                    if (hstsEnabled) {
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31536000));
                    }
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin",
                "X-Requested-With", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
