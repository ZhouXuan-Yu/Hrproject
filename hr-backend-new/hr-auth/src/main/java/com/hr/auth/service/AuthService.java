package com.hr.auth.service;

import com.hr.auth.dto.LoginRequest;
import com.hr.auth.dto.LoginResponse;
import com.hr.auth.entity.IamUser;
import com.hr.auth.entity.PasswordResetToken;
import com.hr.auth.repository.IamUserRepository;
import com.hr.auth.repository.PasswordResetTokenRepository;
import com.hr.auth.security.TokenProvider;
import com.hr.auth.security.WerkzeugPasswordEncoder;
import com.hr.common.dto.ApiResponse;
import com.hr.common.enums.RoleMenus;
import com.hr.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务：登录、登出、当前用户信息、密码重置、首次设置。
 * 对齐 Flask api/auth.py 的 login() 等逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILURES = 5;
    private static final long FAILURE_WINDOW_SECONDS = 900; // 15 分钟
    private static final String FAIL_KEY = "hrlock:v2:login:fail:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final IamUserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
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
                user.getUsername() == null ? username : user.getUsername(),
                user.getRealName(),
                user.getDeptId(),
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

    // ── 密码重置（自助找回，对齐 Flask forgot_password / verify_reset_code） ──

    /**
     * POST /api/auth/forgot-password — 发送密码重置验证码。
     * Body: { username, channel?: 'email'|'phone' }
     */
    public Map<String, Object> forgotPassword(Map<String, Object> body) {
        String username = body != null && body.get("username") != null
                ? String.valueOf(body.get("username")).trim() : "";
        String channel = body != null && body.get("channel") != null
                ? String.valueOf(body.get("channel")).trim() : "email";
        if (username.isEmpty()) {
            throw BusinessException.invalidInput("请输入用户名");
        }

        IamUser user = userRepository.findByUsernameOrEmployeeNo(username, username)
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .filter(u -> u.getIsDeleted() == null || u.getIsDeleted() == 0)
                .orElse(null);
        if (user == null) {
            // 不暴露账号是否存在（对齐 Flask）
            return Map.of("sent", false, "message", "如果该账号存在，您将收到验证码。");
        }

        String code = generateResetCode();
        String target;
        String effectiveChannel;

        if ("phone".equals(channel) && user.getMobile() != null && !user.getMobile().isBlank()) {
            effectiveChannel = "phone";
            target = maskMobile(user.getMobile());
            log.info("[PASSWORD_RESET] phone={} code={} user={}", user.getMobile(), code, username);
        } else if (user.getEmail() != null && !user.getEmail().isBlank()) {
            effectiveChannel = "email";
            target = maskEmail(user.getEmail());
            // 邮件发送失败时提示联系管理员（对齐 Flask 行为）
            boolean mailOk = sendResetMail(user, code);
            if (!mailOk) {
                return Map.of("sent", false, "message",
                        "邮件发送失败，请联系管理员重置密码", "contactAdmin", true);
            }
        } else {
            return Map.of("sent", false, "message",
                    "该账号未配置邮箱/手机号，请联系管理员重置密码", "contactAdmin", true);
        }

        saveResetToken(user.getUserId(), effectiveChannel, target, code);
        return Map.of("sent", true, "channel", effectiveChannel, "target", target,
                "message", "验证码已发送至您的" + ("email".equals(effectiveChannel) ? "邮箱" : "手机") + " " + target,
                "expiresIn", 300);
    }

    /**
     * POST /api/auth/verify-reset-code — 验证验证码并设置新密码。
     * Body: { username, code, newPassword }
     */
    @Transactional
    public Map<String, Object> verifyResetCode(Map<String, Object> body) {
        String username = body != null && body.get("username") != null
                ? String.valueOf(body.get("username")).trim() : "";
        String code = body != null && body.get("code") != null
                ? String.valueOf(body.get("code")).trim() : "";
        String newPwd = body != null && body.get("newPassword") != null
                ? String.valueOf(body.get("newPassword")).trim() : "";
        if (username.isEmpty() || code.isEmpty() || newPwd.isEmpty()) {
            throw BusinessException.invalidInput("请填写完整信息");
        }
        if (newPwd.length() < 6) {
            throw BusinessException.invalidInput("新密码至少6位");
        }

        IamUser user = userRepository.findByUsernameOrEmployeeNo(username, username)
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .filter(u -> u.getIsDeleted() == null || u.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken reset = resetTokenRepository
                .findFirstByUserIdAndTokenAndStatusAndIsDeletedOrderByIdDesc(
                        user.getUserId(), code, "pending", 0)
                .filter(t -> t.getExpiresAt() != null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BusinessException("INVALID_CODE", "验证码错误或已过期，请重新获取", 400));

        user.setPasswordHash(UserManagementService.werkzeugScrypt(newPwd));
        user.setMustChangePassword(0);
        user.setPasswordUpdatedAt(now);
        user.setUpdateTime(now);
        userRepository.save(user);

        reset.setStatus("used");
        reset.setUsedAt(now);
        resetTokenRepository.save(reset);

        // 过期该用户其他 pending 令牌
        resetTokenRepository.expirePendingTokens(user.getUserId(), now);

        return Map.of("reset", true, "message", "密码重置成功，请使用新密码登录");
    }

    /**
     * GET /api/auth/setup-status — 系统是否需要首次管理员设置。
     */
    public Map<String, Object> setupStatus() {
        long adminCount = userRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("roleCode"), "admin"),
                        cb.equal(root.get("status"), 1),
                        cb.equal(root.get("isDeleted"), 0)))
                .size();
        return Map.of("needsSetup", adminCount == 0, "hasAdmin", adminCount > 0);
    }

    /**
     * POST /api/auth/setup — 首次创建管理员账号（仅当无 admin 时可用）。
     */
    @Transactional
    public Map<String, Object> firstTimeSetup(Map<String, Object> body) {
        long existing = userRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("roleCode"), "admin"),
                        cb.equal(root.get("status"), 1),
                        cb.equal(root.get("isDeleted"), 0)))
                .size();
        if (existing > 0) {
            throw new BusinessException("FORBIDDEN", "系统已完成初始化，请使用管理员账号登录", 403);
        }

        String username = body != null && body.get("username") != null
                ? String.valueOf(body.get("username")).trim() : "admin";
        String realName = body != null && body.get("realName") != null
                ? String.valueOf(body.get("realName")).trim() : "系统管理员";
        String password = body != null && body.get("password") != null
                ? String.valueOf(body.get("password")).trim() : "";
        if (username.isEmpty() || password.isEmpty()) {
            throw BusinessException.invalidInput("请填写用户名和密码");
        }
        if (password.length() < 6) {
            throw BusinessException.invalidInput("密码至少6位");
        }

        Long maxId = userRepository.maxUserId();
        IamUser user = new IamUser();
        user.setUserId(maxId != null ? Math.max(100, maxId + 1) : 100L);
        user.setUsername(username);
        user.setEmployeeNo(username);
        user.setRealName(realName);
        user.setRoleCode("admin");
        user.setPasswordHash(UserManagementService.werkzeugScrypt(password));
        user.setMustChangePassword(0);
        user.setStatus(1);
        user.setIsDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", username);
        userInfo.put("realName", realName);
        userInfo.put("role", "admin");

        Map<String, Object> result = new HashMap<>();
        result.put("created", true);
        result.put("user", userInfo);
        result.put("message", "系统初始化完成，请登录");
        return result;
    }

    // ── 工具方法 ────────────────────────────────────────────

    private String generateResetCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private void saveResetToken(Long userId, String channel, String target, String code) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setToken(code);
        token.setChannel(channel);
        token.setTarget(target);
        token.setStatus("pending");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        token.setIsDeleted(0);
        token.setCreateTime(LocalDateTime.now());
        token.setUpdateTime(LocalDateTime.now());
        resetTokenRepository.save(token);
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile != null ? mobile : "";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email != null ? email : "";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        String maskedName = name.length() <= 3 ? name.substring(0, 1) + "***" : name.substring(0, 3) + "***";
        return maskedName + "@" + domain;
    }

    /** 邮件发送：当前无 SMTP 配置时返回 false 走「联系管理员」分支。 */
    private boolean sendResetMail(IamUser user, String code) {
        // 对齐 Flask mail_sender：本项目邮件通道由 hr-integration 邮箱采集负责，
        // 自助重置邮件尚未接 SMTP 时，返回 false 引导联系管理员
        log.info("[PASSWORD_RESET] email={} code={} user={} (SMTP 未配置，跳过真实发送)",
                user.getEmail(), code, user.getUsername());
        return false;
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
