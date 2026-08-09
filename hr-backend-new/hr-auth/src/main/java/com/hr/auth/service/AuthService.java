package com.hr.auth.service;

import com.hr.auth.dto.LoginRequest;
import com.hr.auth.dto.LoginResponse;
import com.hr.auth.entity.IamUser;
import com.hr.auth.repository.IamUserRepository;
import com.hr.auth.security.TokenProvider;
import com.hr.auth.security.WerkzeugPasswordEncoder;
import com.hr.common.dto.ApiResponse;
import com.hr.common.enums.RoleMenus;
import com.hr.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务：登录、登出、当前用户信息。
 * 对齐 Flask api/auth.py 的 login() 逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILURES = 5;
    private static final long FAILURE_WINDOW_SECONDS = 900; // 15 分钟
    private static final String FAIL_KEY = "hrlock:v2:login:fail:";

    private final IamUserRepository userRepository;
    private final TokenProvider tokenProvider;
    private final WerkzeugPasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    public ApiResponse<LoginResponse> login(LoginRequest req, String ip) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        String password = req.getPassword() == null ? "" : req.getPassword();
        if (username.isEmpty()) {
            throw BusinessException.invalidInput("请输入用户名");
        }
        if (password.isEmpty()) {
            throw BusinessException.invalidInput("请输入密码");
        }

        // 登录失败锁定检查
        checkLocked(username);

        // 查询用户（支持 username 或 employee_no）
        IamUser user = userRepository.findByUsernameOrEmployeeNo(username, username)
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .filter(u -> u.getIsDeleted() == null || u.getIsDeleted() == 0)
                .orElse(null);
        if (user == null) {
            recordFailure(username);
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误", 400);
        }

        // 验证密码（支持 werkzeug scrypt / bcrypt / legacy SHA-256）
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            recordFailure(username);
            throw new BusinessException("AUTH_FAILED", "用户名或密码错误", 400);
        }

        // 清除失败记录
        clearFailures(username);

        boolean rememberMe = Boolean.TRUE.equals(req.getRememberMe());
        String token = tokenProvider.createToken(
                user.getUserId(), user.getRoleCode(), 1L,
                user.getRealName() == null ? username : user.getRealName(),
                rememberMe);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getUserId());
        userInfo.put("name", user.getRealName() == null ? username : user.getRealName());
        userInfo.put("role", user.getRoleCode());
        userInfo.put("avatar", null);
        userInfo.put("mustChangePassword", user.getMustChangePassword() != null && user.getMustChangePassword() == 1);

        return ApiResponse.success(LoginResponse.of(token, userInfo, RoleMenus.getMenus(user.getRoleCode())));
    }

    public ApiResponse<Map<String, Object>> me(Long userId) {
        IamUser user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.unauthorized());
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", String.valueOf(user.getUserId()));
        userInfo.put("name", user.getRealName());
        userInfo.put("role", user.getRoleCode());
        userInfo.put("avatar", null);
        data.put("user", userInfo);
        data.put("menus", RoleMenus.getMenus(user.getRoleCode()));
        return ApiResponse.success(data);
    }

    // ── 登录失败锁定（Redis 存储） ────────────────────────────
    private void checkLocked(String username) {
        try {
            Object count = redisTemplate.opsForValue().get(FAIL_KEY + username);
            if (count != null && ((Number) count).intValue() >= MAX_FAILURES) {
                throw new BusinessException("ACCOUNT_LOCKED",
                        "登录失败次数过多，账户已锁定 15 分钟", 429);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
            // Redis 不可用时跳过锁定检查
        }
    }

    private void recordFailure(String username) {
        try {
            Long count = redisTemplate.opsForValue().increment(FAIL_KEY + username);
            if (count != null && count == 1) {
                redisTemplate.expire(FAIL_KEY + username, Duration.ofSeconds(FAILURE_WINDOW_SECONDS));
            }
        } catch (Exception ignored) {
        }
    }

    private void clearFailures(String username) {
        try {
            redisTemplate.delete(FAIL_KEY + username);
        } catch (Exception ignored) {
        }
    }
}
