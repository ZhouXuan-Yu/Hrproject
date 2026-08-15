package com.hr.auth.service;

import com.hr.auth.entity.IamDept;
import com.hr.auth.entity.IamPosition;
import com.hr.auth.entity.IamUser;
import com.hr.auth.repository.IamDeptRepository;
import com.hr.auth.repository.IamPositionRepository;
import com.hr.auth.repository.IamUserRepository;
import com.hr.auth.security.WerkzeugPasswordEncoder;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final List<String> VALID_DATA_SCOPES =
            List.of("all", "dept", "dept_and_self", "self", "none");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final IamUserRepository userRepository;
    private final IamDeptRepository deptRepository;
    private final IamPositionRepository positionRepository;
    private final EntityManager entityManager;
    private final WerkzeugPasswordEncoder passwordEncoder;

    /** 原生查询跨模块统计需求数（避免模块间依赖） */
    private long countDemandsByField(String field, Object value) {
        String sql = "SELECT COUNT(*) FROM t_hr_recruit_demand WHERE "
                + field + " = :val AND is_deleted = 0";
        Object result = entityManager.createNativeQuery(sql)
                .setParameter("val", value)
                .getSingleResult();
        return result instanceof Number n ? n.longValue() : 0;
    }

    /** 原生查询统计用户数 */
    private long countUsersByField(String field, Object value) {
        String sql = "SELECT COUNT(*) FROM t_core_user WHERE "
                + field + " = :val AND is_deleted = 0 AND status = 1";
        Object result = entityManager.createNativeQuery(sql)
                .setParameter("val", value)
                .getSingleResult();
        return result instanceof Number n ? n.longValue() : 0;
    }

    /** 原生查询统计岗位数 */
    private long countPositionsByField(String field, Object value) {
        String sql = "SELECT COUNT(*) FROM t_core_position WHERE "
                + field + " = :val AND is_deleted = 0 AND status = 1";
        Object result = entityManager.createNativeQuery(sql)
                .setParameter("val", value)
                .getSingleResult();
        return result instanceof Number n ? n.longValue() : 0;
    }

    // ── 用户管理 ──────────────────────────────────────────────

    public Map<String, Object> listUsers(String keyword, String roleFilter, Integer statusFilter,
                                         int page, int pageSize) {
        page = Math.max(1, page);
        pageSize = Math.min(100, Math.max(1, pageSize));

        Specification<IamUser> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), 0));
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("username"), kw),
                        cb.like(root.get("realName"), kw),
                        cb.like(root.get("mobile"), kw)));
            }
            if (roleFilter != null && !roleFilter.isBlank()) {
                predicates.add(cb.equal(root.get("roleCode"), roleFilter.trim()));
            }
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<IamUser> pageResult = userRepository.findAll(spec,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "userId")));
        long total = pageResult.getTotalElements();
        List<IamUser> pageItems = pageResult.getContent();

        Set<Long> deptIds = new HashSet<>();
        Set<Long> positionIds = new HashSet<>();
        for (IamUser user : pageItems) {
            if (user.getDeptId() != null) deptIds.add(user.getDeptId());
            if (user.getPositionId() != null) positionIds.add(user.getPositionId());
        }
        Map<Long, String> deptNames = new HashMap<>();
        for (IamDept dept : deptRepository.findAllById(deptIds)) {
            deptNames.put(dept.getDeptId(), dept.getDeptName());
        }
        Map<Long, String> positionNames = new HashMap<>();
        for (IamPosition position : positionRepository.findAllById(positionIds)) {
            positionNames.put(position.getPositionId(), position.getPositionName());
        }

        List<Map<String, Object>> items = pageItems.stream()
                .map(user -> userToMap(user, deptNames, positionNames)).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", items);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @Transactional
    public Map<String, Object> createUser(Map<String, Object> body) {
        String employeeNo = str(body.get("employeeNo"));
        String realName = str(body.get("realName"));
        String mobile = str(body.get("mobile"));
        String roleCode = str(body.get("roleCode")).isEmpty() ? "employee" : str(body.get("roleCode"));
        Long deptId = num(body.get("deptId"));
        Long positionId = num(body.get("positionId"));
        String email = str(body.get("email"));
        String dataScope = normalizeDataScope(body.get("dataScope"));

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
        // user_id 为业务主键（t_core_user.id 是自增物理主键），手动分配 max+1，对齐 Flask
        Long maxId = userRepository.maxUserId();
        u.setUserId(maxId != null ? maxId + 1 : 3001L);
        u.setEmployeeNo(employeeNo);
        u.setUsername(employeeNo);
        u.setRealName(realName);
        u.setDeptId(deptId);
        u.setPositionId(positionId);
        u.setRoleCode(roleCode);
        u.setDataScope(dataScope);
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
        if (body.containsKey("dataScope")) {
            u.setDataScope(normalizeDataScope(body.get("dataScope")));
        }
        u.setUpdateTime(LocalDateTime.now());
        userRepository.save(u);
        return userToMap(u);
    }

    @Transactional
    public Map<String, Object> toggleUserStatus(Long userId) {
        IamUser u = getActiveUser(userId);
        // 禁止操作自己（对齐 Python）
        Long currentUserId = SecurityUtils.getUserId();
        if (currentUserId != null && currentUserId.equals(u.getUserId())) {
            throw new BusinessException("FORBIDDEN", "不能操作自己的账号", 403);
        }
        u.setStatus(u.getStatus() != null && u.getStatus() == 1 ? 0 : 1);
        u.setUpdateTime(LocalDateTime.now());
        userRepository.save(u);
        return userToMap(u);
    }

    @Transactional
    public void deleteUser(Long userId) {
        IamUser u = getActiveUser(userId);
        // 禁止删除自己（对齐 Python）
        Long currentUserId = SecurityUtils.getUserId();
        if (currentUserId != null && currentUserId.equals(u.getUserId())) {
            throw new BusinessException("FORBIDDEN", "不能删除自己的账号", 403);
        }
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

    /**
     * 查询待激活账号：已入职/已接受 Offer 但尚无系统账号的候选人。
     * 使用原生 SQL 跨表 JOIN 对齐 Flask pending_accounts() 逻辑。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findPendingAccounts() {
        String sql = """
                SELECT c.id AS candidate_id, c.candidate_name, c.mobile, c.email,
                       rp.process_status, d.id AS dept_id, d.dept_name,
                       d.position_id, p.position_name
                FROM t_hr_candidate c
                JOIN t_hr_resume r ON r.candidate_id = c.id AND r.is_deleted = 0
                JOIN t_hr_recruit_process rp ON rp.resume_id = r.id AND rp.is_deleted = 0
                JOIN t_hr_recruit_demand d ON d.id = rp.demand_id AND d.is_deleted = 0
                LEFT JOIN t_core_position p ON p.position_id = d.position_id AND p.is_deleted = 0
                WHERE c.status = 'hired'
                  AND c.is_deleted = 0
                  AND rp.process_status IN (6, 8)
                ORDER BY c.id DESC
                LIMIT 100""";

        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] row : rows) {
            Long candidateId = ((Number) row[0]).longValue();
            String candidateName = (String) row[1];
            String mobile = (String) row[2];
            String email = (String) row[3];
            Integer processStatus = row[4] != null ? ((Number) row[4]).intValue() : null;

            // 检查是否已有 IamUser 账号（按 mobile 或 email 匹配）
            boolean hasAccount = false;
            if (mobile != null && !mobile.isEmpty()) {
                hasAccount = userRepository.existsByMobileAndIsDeleted(mobile, 0);
            }
            if (!hasAccount && email != null && !email.isEmpty()) {
                hasAccount = userRepository.existsByEmailAndIsDeleted(email, 0);
            }

            if (hasAccount) {
                continue; // 已有账号，跳过
            }

            Long deptId = row[5] != null ? ((Number) row[5]).longValue() : null;
            String deptName = (String) row[6];
            Long positionId = row[7] != null ? ((Number) row[7]).longValue() : null;
            String positionName = (String) row[8];

            String statusLabel = processStatus != null && processStatus == 8 ? "已入职" : "已接受Offer";

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("candidateId", candidateId);
            m.put("candidateName", candidateName != null ? candidateName : "");
            m.put("mobile", mobile != null ? mobile : "");
            m.put("email", email != null ? email : "");
            m.put("deptId", deptId);
            m.put("deptName", deptName != null ? deptName : "");
            m.put("positionId", positionId);
            m.put("positionName", positionName != null ? positionName : "");
            m.put("processStatus", processStatus);
            m.put("statusLabel", statusLabel);
            result.add(m);
        }
        return result;
    }

    private Long nextUserId() {
        Long max = userRepository.maxUserId();
        return Math.max(max, 9L) + 1; // 对齐 Flask：max+1，空库从 10 开始
    }

    private String generateEmployeeNo() {
        String yyyy = String.valueOf(LocalDateTime.now().getYear());
        int maxSeq = 0;
        String maxNo = userRepository.maxEmployeeNoLike(yyyy + "%");
        if (maxNo != null && maxNo.length() == 8 && maxNo.startsWith(yyyy)) {
            try {
                maxSeq = Integer.parseInt(maxNo.substring(4));
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy employee numbers.
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
    @CacheEvict(cacheNames = "org", key = "'departments'")
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
    @CacheEvict(cacheNames = "org", key = "'departments'")
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
    @CacheEvict(cacheNames = "org", key = "'departments'")
    public void deleteDepartment(Long deptId) {
        IamDept d = deptRepository.findById(deptId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("部门不存在"));

        // 引用检查（对齐 Python）
        long userCount = countUsersByField("dept_id", deptId);
        long demandCount = countDemandsByField("dept_id", deptId);
        long posCount = countPositionsByField("dept_id", deptId);

        List<String> refs = new ArrayList<>();
        if (userCount > 0) refs.add(userCount + " 个用户");
        if (demandCount > 0) refs.add(demandCount + " 个招聘需求");
        if (posCount > 0) refs.add(posCount + " 个岗位");
        if (!refs.isEmpty()) {
            throw new BusinessException("HAS_REFERENCES",
                    "部门被引用（" + String.join(", ", refs) + "），无法删除，请先停用", 400);
        }

        d.setIsDeleted(1);
        d.setStatus(0);
        deptRepository.save(d);
    }

    @Transactional
    @CacheEvict(cacheNames = "org", key = "'departments'")
    public Map<String, Object> toggleDepartmentStatus(Long deptId, int status) {
        IamDept d = deptRepository.findById(deptId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("部门不存在"));

        // 停用时检查是否有在职员工（对齐 Python）
        if (status == 0) {
            long activeUsers = countUsersByField("dept_id", deptId);
            if (activeUsers > 0) {
                throw new BusinessException("HAS_ACTIVE_USERS",
                        "部门下有 " + activeUsers + " 名在职员工，请先转岗后再停用", 400);
            }
        }

        d.setStatus(status);
        deptRepository.save(d);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deptId", d.getDeptId());
        m.put("status", d.getStatus());
        return m;
    }

    // ── 岗位管理 ──────────────────────────────────────────────

    @Transactional
    @CacheEvict(cacheNames = "org", key = "'positions'")
    public Map<String, Object> createPosition(Map<String, Object> body) {
        String name = str(body.get("positionName"));
        if (name.isEmpty()) {
            throw BusinessException.invalidInput("请输入岗位名称");
        }
        IamPosition p = new IamPosition();
        Long maxId = positionRepository.findAll().stream()
                .mapToLong(IamPosition::getPositionId).max().orElse(0L);
        p.setPositionId(Math.max(10L, maxId + 1));
        p.setPositionNo(generatePositionNo());
        p.setPositionName(name);
        p.setDeptId(num(body.get("deptId")));
        p.setStatus(1);
        p.setIsDeleted(0);
        positionRepository.save(p);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("positionId", p.getPositionId());
        m.put("positionNo", p.getPositionNo());
        m.put("positionName", p.getPositionName());
        m.put("status", p.getStatus());
        return m;
    }

    /** 生成岗位编号 PO{YYYYMM}{seq:04d}，对齐 Python id_generator.next_position_no */
    private String generatePositionNo() {
        String prefix = "PO" + String.format("%tY%<tm",
                java.time.LocalDate.now());
        // 找当前月份最大序号
        Long maxSeq = positionRepository.findAll().stream()
                .filter(x -> x.getPositionNo() != null && x.getPositionNo().startsWith(prefix))
                .map(IamPosition::getPositionNo)
                .map(no -> {
                    try {
                        return Long.parseLong(no.substring(8)); // after PO202608
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                })
                .max(Long::compareTo).orElse(0L);
        return prefix + String.format("%04d", maxSeq + 1);
    }

    @Transactional
    @CacheEvict(cacheNames = "org", key = "'positions'")
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
    @CacheEvict(cacheNames = "org", key = "'positions'")
    public void deletePosition(Long positionId) {
        IamPosition p = positionRepository.findById(positionId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("岗位不存在"));

        // 引用检查（对齐 Python）
        long userCount = countUsersByField("position_id", positionId);
        long demandCount = countDemandsByField("position_id", positionId);

        List<String> refs = new ArrayList<>();
        if (userCount > 0) refs.add(userCount + " 个用户");
        if (demandCount > 0) refs.add(demandCount + " 个招聘需求");
        if (!refs.isEmpty()) {
            throw new BusinessException("HAS_REFERENCES",
                    "岗位被引用（" + String.join(", ", refs) + "），无法删除，请先停用", 400);
        }

        p.setIsDeleted(1);
        p.setStatus(0);
        positionRepository.save(p);
    }

    @Transactional
    @CacheEvict(cacheNames = "org", key = "'positions'")
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
        return userToMap(u, null, null);
    }

    private Map<String, Object> userToMap(IamUser u, Map<Long, String> deptNames,
                                          Map<Long, String> positionNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getUserId());
        m.put("username", u.getUsername());
        m.put("employeeNo", u.getEmployeeNo());
        m.put("realName", u.getRealName());
        m.put("roleCode", u.getRoleCode());
        m.put("dataScope", u.getDataScope());
        m.put("deptId", u.getDeptId());
        m.put("deptName", deptNames != null && u.getDeptId() != null
                ? deptNames.getOrDefault(u.getDeptId(), "—") : resolveDeptName(u.getDeptId()));
        m.put("positionId", u.getPositionId());
        m.put("positionName", positionNames != null && u.getPositionId() != null
                ? positionNames.getOrDefault(u.getPositionId(), "—") : resolvePositionName(u.getPositionId()));
        m.put("email", u.getEmail());
        m.put("mobile", u.getMobile());
        m.put("status", u.getStatus());
        m.put("statusLabel", u.getStatus() != null && u.getStatus() == 1 ? "启用" : "停用");
        m.put("mustChangePassword", u.getMustChangePassword() != null && u.getMustChangePassword() == 1);
        return m;
    }

    /** 解析部门名称（对齐 Python resolve_dept_name） */
    private String resolveDeptName(Long deptId) {
        if (deptId == null) return "—";
        return deptRepository.findById(deptId)
                .filter(d -> d.getIsDeleted() == null || d.getIsDeleted() == 0)
                .map(IamDept::getDeptName)
                .orElse("—");
    }

    /** 解析岗位名称（对齐 Python resolve_position_name） */
    private String resolvePositionName(Long positionId) {
        if (positionId == null) return "—";
        return positionRepository.findById(positionId)
                .filter(p -> p.getIsDeleted() == null || p.getIsDeleted() == 0)
                .map(IamPosition::getPositionName)
                .orElse("—");
    }

    /** werkzeug generate_password_hash 兼容的 scrypt 哈希（werkzeug 默认 n=32768 r=8 p=1，盐为 ASCII hex 字符，哈希 hex）。 */
    public static String werkzeugScrypt(String password) {
        byte[] rawSalt = new byte[8];
        SECURE_RANDOM.nextBytes(rawSalt);
        String saltStr = com.hr.common.util.Sha256Util.sha256Hex(rawSalt).substring(0, 16);
        byte[] salt = saltStr.getBytes(StandardCharsets.UTF_8);
        byte[] hash = SCrypt.generate(
                password.getBytes(StandardCharsets.UTF_8), salt, 32768, 8, 1, 64);
        return "scrypt:32768:8:1$" + saltStr + "$" + toHex(hash);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** 兼容 werkzeug scrypt / bcrypt / legacy sha256 的校验。 */
    public boolean verifyPassword(String raw, String stored) {
        return passwordEncoder.matches(raw, stored);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    /** 校验并归一化个人数据范围覆盖：空串/null → null（跟随角色），合法五档原样返回，否则抛错。 */
    private static String normalizeDataScope(Object raw) {
        String s = raw == null ? "" : String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        if (!VALID_DATA_SCOPES.contains(s)) {
            throw BusinessException.invalidInput("无效的数据范围: " + s);
        }
        return s;
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
