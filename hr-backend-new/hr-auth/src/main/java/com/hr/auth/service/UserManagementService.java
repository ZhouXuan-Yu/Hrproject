package com.hr.auth.service;

import com.hr.auth.entity.IamDept;
import com.hr.auth.entity.IamPosition;
import com.hr.auth.entity.IamUser;
import com.hr.auth.repository.IamDeptRepository;
import com.hr.auth.repository.IamPositionRepository;
import com.hr.auth.repository.IamUserRepository;
import com.hr.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 用户 / 部门 / 岗位管理服务，对齐 Flask api/auth.py 的管理端点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final List<String> VALID_ROLES =
            List.of("admin", "hr", "dept_head", "director", "employee", "interviewer");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final IamUserRepository userRepository;
    private final IamDeptRepository deptRepository;
    private final IamPositionRepository positionRepository;

    // ── 用户管理 ──────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createUser(Map<String, Object> body) {
        String employeeNo = str(body.get("employeeNo"));
        String realName = str(body.get("realName"));
        String mobile = str(body.get("mobile"));
        String roleCode = str(body.get("roleCode")).isEmpty() ? "employee" : str(body.get("roleCode"));
        Long deptId = num(body.get("deptId"));
        Long positionId = num(body.get("positionId"));
        String email = str(body.get("email"));

        if (employeeNo.isEmpty()) {
            employeeNo = generateEmployeeNo();
        }
        if (realName.isEmpty()) {
            throw BusinessException.invalidInput("请输入真实姓名");
        }
        if (!mobile.isEmpty() && !MOBILE_PATTERN.matcher(mobile).matches()) {
            throw BusinessException.invalidInput("手机号格式不正确");
        }
        if (!VALID_ROLES.contains(roleCode)) {
            throw BusinessException.invalidInput("无效的角色: " + roleCode);
        }
        if (userRepository.findByEmployeeNo(employeeNo).isPresent()) {
            throw new BusinessException("DUPLICATE", "工号 " + employeeNo + " 已存在", 409);
        }
        if (!mobile.isEmpty() && userRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("mobile"), mobile),
                        cb.equal(root.get("isDeleted"), 0))).stream().findAny().isPresent()) {
            throw new BusinessException("DUPLICATE", "手机号 " + mobile + " 已存在", 409);
        }

        String password = "123456";
        IamUser u = new IamUser();
        u.setEmployeeNo(employeeNo);
        u.setUsername(employeeNo);
        u.setRealName(realName);
        u.setDeptId(deptId);
        u.setPositionId(positionId);
        u.setRoleCode(roleCode);
        u.setEmail(email.isEmpty() ? null : email);
        u.setMobile(mobile.isEmpty() ? null : mobile);
        u.setPasswordHash(werkzeugScrypt(password));
        u.setMustChangePassword(1);
        u.setStatus(1);
        u.setIsDeleted(0);
        u.setCreateTime(LocalDateTime.now());
        u.setUpdateTime(LocalDateTime.now());
        userRepository.save(u);

        Map<String, Object> m = userToMap(u);
        m.put("initialPassword", password);
        return m;
    }

    @Transactional
    public Map<String, Object> updateUser(Long userId, Map<String, Object> body) {
        IamUser u = getActiveUser(userId);
        if (body.get("realName") != null) {
            u.setRealName(str(body.get("realName")));
        }
        if (body.get("roleCode") != null) {
            String role = str(body.get("roleCode"));
            if (!VALID_ROLES.contains(role)) {
                throw BusinessException.invalidInput("无效的角色: " + role);
            }
            u.setRoleCode(role);
        }
        if (body.get("deptId") != null) {
            u.setDeptId(num(body.get("deptId")));
        }
        if (body.get("positionId") != null) {
            u.setPositionId(num(body.get("positionId")));
        }
        if (body.get("email") != null) {
            u.setEmail(str(body.get("email")).isEmpty() ? null : str(body.get("email")));
        }
        if (body.get("mobile") != null) {
            String mobile = str(body.get("mobile"));
            if (!mobile.isEmpty() && !MOBILE_PATTERN.matcher(mobile).matches()) {
                throw BusinessException.invalidInput("手机号格式不正确");
            }
            u.setMobile(mobile.isEmpty() ? null : mobile);
        }
        u.setUpdateTime(LocalDateTime.now());
        userRepository.save(u);
        return userToMap(u);
    }

    @Transactional
    public Map<String, Object> toggleUserStatus(Long userId) {
        IamUser u = getActiveUser(userId);
        u.setStatus(u.getStatus() != null && u.getStatus() == 1 ? 0 : 1);
        u.setUpdateTime(LocalDateTime.now());
        userRepository.save(u);
        return userToMap(u);
    }

    @Transactional
    public void deleteUser(Long userId) {
        IamUser u = getActiveUser(userId);
        u.setIsDeleted(1);
        u.setStatus(0);
        u.setUpdateTime(LocalDateTime.now());
        userRepository.save(u);
    }

    @Transactional
    public Map<String, Object> resetPassword(Long userId) {
        IamUser u = getActiveUser(userId);
        u.setPasswordHash(werkzeugScrypt("123456"));
        u.setMustChangePassword(1);
        u.setPasswordUpdatedAt(LocalDateTime.now());
        u.setUpdateTime(LocalDateTime.now());
        userRepository.save(u);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reset", true);
        m.put("userId", userId);
        m.put("initialPassword", "123456");
        return m;
    }

    @Transactional
    public Map<String, Object> changePassword(Long userId, String oldPwd, String newPwd) {
        if (oldPwd == null || oldPwd.isBlank() || newPwd == null || newPwd.isBlank()) {
            throw BusinessException.invalidInput("旧密码和新密码不能为空");
        }
        IamUser u = userRepository.findById(userId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(BusinessException::unauthorized);
        if (u.getPasswordHash() == null || !verifyPassword(oldPwd, u.getPasswordHash())) {
            throw BusinessException.invalidInput("原密码不正确");
        }
        if (newPwd.length() < 6) {
            throw BusinessException.invalidInput("新密码长度不能少于 6 位");
        }
        u.setPasswordHash(werkzeugScrypt(newPwd));
        u.setMustChangePassword(0);
        u.setPasswordUpdatedAt(LocalDateTime.now());
        u.setUpdateTime(LocalDateTime.now());
        userRepository.save(u);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("changed", true);
        return m;
    }

    @Transactional
    public Map<String, Object> batchCreateUsers(List<Map<String, Object>> users) {
        if (users == null || users.isEmpty()) {
            throw BusinessException.invalidInput("缺少用户列表");
        }
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        int ok = 0;
        for (Map<String, Object> u : users) {
            try {
                results.add(createUser(u));
                ok++;
            } catch (BusinessException e) {
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("employeeNo", u != null ? u.get("employeeNo") : "?");
                fail.put("error", e.getMessage());
                results.add(fail);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", ok);
        out.put("total", users.size());
        out.put("results", results);
        return out;
    }

    private String generateEmployeeNo() {
        String yyyy = String.valueOf(LocalDateTime.now().getYear());
        List<IamUser> all = userRepository.findAll((root, query, cb) -> cb.like(root.get("employeeNo"), yyyy + "%"));
        int maxSeq = 0;
        for (IamUser u : all) {
            String no = u.getEmployeeNo();
            if (no != null && no.length() == 8 && no.startsWith(yyyy)) {
                try {
                    maxSeq = Math.max(maxSeq, Integer.parseInt(no.substring(4)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String candidate;
        do {
            maxSeq++;
            candidate = yyyy + String.format("%04d", maxSeq);
        } while (userRepository.findByEmployeeNo(candidate).isPresent());
        return candidate;
    }

    // ── 部门管理 ──────────────────────────────────────────────

    @Transactional
    @CacheEvict(cacheNames = "org", allEntries = true)
    public Map<String, Object> createDepartment(Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name.isEmpty()) {
            throw BusinessException.invalidInput("请输入部门名称");
        }
        IamDept d = new IamDept();
        Long maxId = deptRepository.findAll().stream()
                .mapToLong(IamDept::getDeptId).max().orElse(0L);
        d.setDeptId(Math.max(10L, maxId + 1));
        d.setDeptName(name);
        d.setParentDeptId(num(body.get("parentDeptId")));
        d.setDeptPath(str(body.get("deptPath")).isEmpty() ? "/" + d.getDeptId() : str(body.get("deptPath")));
        d.setSortNum(body.get("sortNum") != null ? ((Number) body.get("sortNum")).intValue() : 0);
        d.setStatus(1);
        d.setIsDeleted(0);
        deptRepository.save(d);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deptId", d.getDeptId());
        m.put("deptName", d.getDeptName());
        m.put("status", d.getStatus());
        return m;
    }

    @Transactional
    @CacheEvict(cacheNames = "org", allEntries = true)
    public Map<String, Object> updateDepartment(Long deptId, Map<String, Object> body) {
        IamDept d = deptRepository.findById(deptId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("部门不存在"));
        if (body.get("deptName") != null) {
            d.setDeptName(str(body.get("deptName")));
        }
        if (body.get("parentDeptId") != null) {
            d.setParentDeptId(num(body.get("parentDeptId")));
        }
        if (body.get("status") != null) {
            d.setStatus(((Number) body.get("status")).intValue());
        }
        deptRepository.save(d);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deptId", d.getDeptId());
        m.put("deptName", d.getDeptName());
        m.put("status", d.getStatus());
        return m;
    }

    @Transactional
    @CacheEvict(cacheNames = "org", allEntries = true)
    public void deleteDepartment(Long deptId) {
        IamDept d = deptRepository.findById(deptId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("部门不存在"));
        d.setIsDeleted(1);
        d.setStatus(0);
        deptRepository.save(d);
    }

    @Transactional
    @CacheEvict(cacheNames = "org", allEntries = true)
    public Map<String, Object> toggleDepartmentStatus(Long deptId, int status) {
        IamDept d = deptRepository.findById(deptId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("部门不存在"));
        d.setStatus(status);
        deptRepository.save(d);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deptId", d.getDeptId());
        m.put("status", d.getStatus());
        return m;
    }

    // ── 岗位管理 ──────────────────────────────────────────────

    @Transactional
    @CacheEvict(cacheNames = "org", allEntries = true)
    public Map<String, Object> createPosition(Map<String, Object> body) {
        String name = str(body.get("positionName"));
        if (name.isEmpty()) {
            throw BusinessException.invalidInput("请输入岗位名称");
        }
        IamPosition p = new IamPosition();
        Long maxId = positionRepository.findAll().stream()
                .mapToLong(IamPosition::getPositionId).max().orElse(0L);
        p.setPositionId(Math.max(10L, maxId + 1));
        p.setPositionName(name);
        p.setDeptId(num(body.get("deptId")));
        p.setStatus(1);
        p.setIsDeleted(0);
        positionRepository.save(p);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("positionId", p.getPositionId());
        m.put("positionName", p.getPositionName());
        m.put("status", p.getStatus());
        return m;
    }

    @Transactional
    @CacheEvict(cacheNames = "org", allEntries = true)
    public Map<String, Object> updatePosition(Long positionId, Map<String, Object> body) {
        IamPosition p = positionRepository.findById(positionId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("岗位不存在"));
        if (body.get("positionName") != null) {
            p.setPositionName(str(body.get("positionName")));
        }
        if (body.get("deptId") != null) {
            p.setDeptId(num(body.get("deptId")));
        }
        if (body.get("status") != null) {
            p.setStatus(((Number) body.get("status")).intValue());
        }
        positionRepository.save(p);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("positionId", p.getPositionId());
        m.put("positionName", p.getPositionName());
        m.put("status", p.getStatus());
        return m;
    }

    @Transactional
    @CacheEvict(cacheNames = "org", allEntries = true)
    public void deletePosition(Long positionId) {
        IamPosition p = positionRepository.findById(positionId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("岗位不存在"));
        p.setIsDeleted(1);
        p.setStatus(0);
        positionRepository.save(p);
    }

    @Transactional
    @CacheEvict(cacheNames = "org", allEntries = true)
    public Map<String, Object> togglePositionStatus(Long positionId, int status) {
        IamPosition p = positionRepository.findById(positionId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("岗位不存在"));
        p.setStatus(status);
        positionRepository.save(p);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("positionId", p.getPositionId());
        m.put("status", p.getStatus());
        return m;
    }

    // ── 工具 ──────────────────────────────────────────────────

    private IamUser getActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    private Map<String, Object> userToMap(IamUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getUserId());
        m.put("username", u.getUsername());
        m.put("employeeNo", u.getEmployeeNo());
        m.put("realName", u.getRealName());
        m.put("roleCode", u.getRoleCode());
        m.put("deptId", u.getDeptId());
        m.put("positionId", u.getPositionId());
        m.put("email", u.getEmail());
        m.put("mobile", u.getMobile());
        m.put("status", u.getStatus());
        m.put("mustChangePassword", u.getMustChangePassword() != null && u.getMustChangePassword() == 1);
        return m;
    }

    /** werkzeug generate_password_hash 兼容的 scrypt 哈希（werkzeug 默认 n=16384 r=8 p=1 dklen=64）。 */
    public static String werkzeugScrypt(String password) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = SCrypt.generate(
                password.getBytes(StandardCharsets.UTF_8), salt, 16384, 8, 1, 64);
        return "scrypt:16384:8:1$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    /** 兼容 werkzeug scrypt / bcrypt / legacy sha256 的校验。 */
    public boolean verifyPassword(String raw, String stored) {
        return new com.hr.auth.security.WerkzeugPasswordEncoder(
                "default-salt-change-me").matches(raw, stored);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Long num(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
