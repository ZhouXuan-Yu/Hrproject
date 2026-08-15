package com.hr.auth.service;

import com.hr.auth.entity.IamUser;
import com.hr.auth.entity.RoleDataScope;
import com.hr.auth.repository.IamUserRepository;
import com.hr.auth.repository.RoleDataScopeRepository;
import com.hr.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据范围解析服务。五档: all 全公司 / dept 本部门 / dept_and_self 本部门+指定给自己的 / self 仅自己 / none 无。
 * <p>
 * 优先级: 个人(t_core_user.data_scope) > 角色(t_hr_role_data_scope) > 硬编码默认。
 * 对齐 Flask data_scope.py 的 apply_demand_scope 语义，并扩展为五档 DB 驱动。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    public static final String ALL = "all";
    public static final String DEPT = "dept";
    public static final String DEPT_AND_SELF = "dept_and_self";
    public static final String SELF = "self";
    public static final String NONE = "none";

    private final IamUserRepository userRepository;
    private final RoleDataScopeRepository roleDataScopeRepository;

    /**
     * 解析某用户的有效数据范围。
     *
     * @param roleCode 角色编码（可为 null）
     * @param userId   登录用户 user_id（可为 null）
     * @return all / dept / dept_and_self / self / none
     */
    public String resolveScope(String roleCode, Long userId) {
        // 1. 个人覆盖（t_core_user.data_scope）
        if (userId != null) {
            IamUser user = userRepository.findFirstActiveByUserId(userId).orElse(null);
            if (user != null && isNotBlank(user.getDataScope())) {
                return user.getDataScope().trim();
            }
        }

        // 2. 角色默认（t_hr_role_data_scope）
        if (isNotBlank(roleCode)) {
            RoleDataScope cfg = roleDataScopeRepository
                    .findFirstByRoleCodeAndEnabledAndIsDeleted(roleCode.trim(), 1, 0)
                    .orElse(null);
            if (cfg != null && isNotBlank(cfg.getScopeType())) {
                return cfg.getScopeType().trim();
            }
        }

        // 3. 硬编码兜底（DB 未迁移或未配置时保证正确行为）
        return defaultScope(roleCode);
    }

    private String defaultScope(String roleCode) {
        if (!isNotBlank(roleCode)) {
            return NONE;
        }
        return switch (roleCode.trim()) {
            case "admin", "hr", "director" -> ALL;
            case "dept_head" -> DEPT_AND_SELF;
            case "employee", "interviewer", "temp_interviewer" -> SELF;
            default -> NONE;
        };
    }

    private static final List<String> VALID_SCOPES = List.of(ALL, DEPT, DEPT_AND_SELF, SELF, NONE);
    private static final List<String> SCOPE_ROLES = List.of(
            "admin", "hr", "dept_head", "director", "employee", "interviewer", "temp_interviewer", "no_recruit");
    private static final Map<String, String> ROLE_LABELS = Map.of(
            "admin", "管理员", "hr", "HR", "dept_head", "部门负责人", "director", "总监",
            "employee", "员工", "interviewer", "面试官", "temp_interviewer", "临时面试官", "no_recruit", "非招聘人员");

    /**
     * 列出 8 个角色的数据范围（五档）。scopeType 取 DB 显式配置，未配置则回退硬编码默认，
     * 用 isDefault 标记是否来自兜底，供 UI 展示「系统默认」。
     */
    public List<Map<String, Object>> listRoleDataScopes() {
        Map<String, String> dbMap = new LinkedHashMap<>();
        for (RoleDataScope r : roleDataScopeRepository.findAll()) {
            if (r.getEnabled() != null && r.getEnabled() == 1
                    && (r.getIsDeleted() == null || r.getIsDeleted() == 0)
                    && isNotBlank(r.getScopeType())) {
                dbMap.put(r.getRoleCode(), r.getScopeType().trim());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String roleCode : SCOPE_ROLES) {
            String dbScope = dbMap.get(roleCode);
            boolean fromDb = dbScope != null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roleCode", roleCode);
            row.put("role", ROLE_LABELS.getOrDefault(roleCode, roleCode));
            row.put("scopeType", fromDb ? dbScope : defaultScope(roleCode));
            row.put("isDefault", !fromDb);
            result.add(row);
        }
        return result;
    }

    /**
     * 保存角色数据范围（{roleCode: scopeType} 映射），就地 upsert。
     * scope 在缓存 key 内（resolveScope 参与），改库后新 key 自然失配，无需显式清缓存。
     */
    @Transactional
    public Map<String, Object> updateRoleDataScopes(Map<String, Object> data) {
        int updated = 0;
        if (data != null) {
            for (Map.Entry<String, Object> e : data.entrySet()) {
                String roleCode = e.getKey();
                String scope = e.getValue() == null ? "" : String.valueOf(e.getValue()).trim();
                if (scope.isEmpty()) {
                    continue;
                }
                if (!VALID_SCOPES.contains(scope)) {
                    throw BusinessException.invalidInput("无效的数据范围: " + scope);
                }
                RoleDataScope row = roleDataScopeRepository
                        .findFirstByRoleCodeAndEnabledAndIsDeleted(roleCode, 1, 0)
                        .orElse(null);
                if (row == null) {
                    row = new RoleDataScope();
                    row.setRoleCode(roleCode);
                    row.setEnabled(1);
                    row.setIsDeleted(0);
                    row.setCreatedAt(LocalDateTime.now());
                }
                row.setScopeType(scope);
                row.setUpdatedAt(LocalDateTime.now());
                roleDataScopeRepository.save(row);
                updated++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", true);
        result.put("count", updated);
        return result;
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
