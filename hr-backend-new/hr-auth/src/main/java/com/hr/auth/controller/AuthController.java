package com.hr.auth.controller;

import com.hr.auth.dto.LoginRequest;
import com.hr.auth.dto.LoginResponse;
import com.hr.auth.service.AuthService;
import com.hr.auth.service.UserManagementService;
import com.hr.auth.security.TokenRevocationService;
import com.hr.common.dto.ApiResponse;
import com.hr.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.csrf.CsrfToken;

import java.time.Duration;
import java.util.Map;

/**
 * 认证接口 /api/auth/*，对齐 Flask api/auth.py。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserManagementService userManagementService;
    private final TokenRevocationService tokenRevocationService;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.security.expose-token:false}")
    private boolean exposeToken;

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token) {
        return ApiResponse.success(Map.of("token", token.getToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        String ip = request.getRemoteAddr();
        ApiResponse<LoginResponse> result = authService.login(req, ip);
        // 设置 httpOnly cookie，对齐 Flask _set_auth_cookie
        String token = result.getData().getToken();
        ResponseCookie cookie = ResponseCookie.from("hr_token", token)
                .httpOnly(true)
                .path("/")
                .secure(cookieSecure)
                .maxAge(Duration.ofSeconds(
                        Boolean.TRUE.equals(req.getRememberMe()) ? 2592000 : 86400))
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        if (!exposeToken) {
            result.getData().setToken(null);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        tokenRevocationService.revoke(extractToken(request));
        ResponseCookie cookie = ResponseCookie.from("hr_token", "")
                .httpOnly(true)
                .path("/")
                .secure(cookieSecure)
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        return ApiResponse.success(null);
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("hr_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw com.hr.common.exception.BusinessException.unauthorized();
        }
        return authService.me(userId);
    }

    @PutMapping("/change-password")
    public ApiResponse<Map<String, Object>> changePassword(@RequestBody Map<String, Object> body) {
        String oldPwd = body != null && body.get("oldPassword") != null
                ? String.valueOf(body.get("oldPassword")) : null;
        String newPwd = body != null && body.get("newPassword") != null
                ? String.valueOf(body.get("newPassword")) : null;
        return ApiResponse.success(userManagementService.changePassword(SecurityUtils.getUserId(), oldPwd, newPwd));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Map<String, Object>> forgotPassword(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(authService.forgotPassword(body));
    }

    @PostMapping("/verify-reset-code")
    public ApiResponse<Map<String, Object>> verifyResetCode(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(authService.verifyResetCode(body));
    }

    @GetMapping("/setup-status")
    public ApiResponse<Map<String, Object>> setupStatus() {
        return ApiResponse.success(authService.setupStatus());
    }

    @PostMapping("/setup")
    public ApiResponse<Map<String, Object>> setup(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(authService.firstTimeSetup(body));
    }

    /**
     * POST /api/auth/register — 自助注册。
     * Body: { email, code, realName, mobile?, password }
     */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(authService.register(body));
    }
}
