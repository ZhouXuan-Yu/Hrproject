package com.hr.auth.controller;

import com.hr.auth.dto.LoginRequest;
import com.hr.auth.dto.LoginResponse;
import com.hr.auth.service.AuthService;
import com.hr.common.dto.ApiResponse;
import com.hr.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                .maxAge(Duration.ofSeconds(
                        Boolean.TRUE.equals(req.getRememberMe()) ? 2592000 : 86400))
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("hr_token", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw com.hr.common.exception.BusinessException.unauthorized();
        }
        return authService.me(userId);
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
}
