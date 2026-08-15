package com.hr.auth.controller;

import com.hr.auth.entity.IamDept;
import com.hr.auth.entity.IamPosition;
import com.hr.auth.entity.IamUser;
import com.hr.auth.repository.IamDeptRepository;
import com.hr.auth.repository.IamPositionRepository;
import com.hr.auth.repository.IamUserRepository;
import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 部门 / 岗位 / 面试官查询接口 /api/auth/*。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class OrgController {

    private final IamDeptRepository deptRepository;
    private final IamPositionRepository positionRepository;
    private final IamUserRepository userRepository;

    @GetMapping("/departments")
    public ApiResponse<List<Map<String, Object>>> departments(
            @RequestParam(name = "all", defaultValue = "0") int all) {
        if (all == 1 && !SecurityUtils.hasAnyRole("admin", "hr")) {
            throw BusinessException.forbidden();
        }
        // all=1 时返回含停用的全部未删除部门，否则仅启用部门
        List<IamDept> depts = (all == 1)
                ? deptRepository.findByIsDeleted(0)
                : deptRepository.findByStatusAndIsDeleted(1, 0);

        // 按部门统计在职员工数
        Map<Long, Long> headcountMap = new java.util.HashMap<>();
        List<Object[]> rows = userRepository.countUsersByDept();
        if (rows != null) {
            for (Object[] row : rows) {
                Long deptId = row[0] != null ? ((Number) row[0]).longValue() : null;
                Long cnt = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                if (deptId != null) headcountMap.put(deptId, cnt);
            }
        }

        List<Map<String, Object>> result = depts.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getDeptId());
            m.put("name", d.getDeptName());
            m.put("parentDeptId", d.getParentDeptId());
            m.put("deptPath", d.getDeptPath());
            m.put("sortNum", d.getSortNum());
            m.put("status", d.getStatus());
            m.put("headcount", headcountMap.getOrDefault(d.getDeptId(), 0L));
            return m;
        }).toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/departments/{deptId}")
    public ApiResponse<IamDept> departmentDetail(@PathVariable Long deptId) {
        return ApiResponse.success(deptRepository.findById(deptId)
                .orElseThrow(() -> BusinessException.notFound("部门不存在")));
    }

    @GetMapping("/positions")
    public ApiResponse<List<IamPosition>> positions(
            @RequestParam(name = "all", defaultValue = "0") int all) {
        if (all == 1 && !SecurityUtils.hasAnyRole("admin", "hr")) {
            throw BusinessException.forbidden();
        }
        // all=1 返回全部（含停用），否则仅启用
        List<IamPosition> positions = (all == 1)
                ? positionRepository.findByIsDeleted(0)
                : positionRepository.findByStatusAndIsDeleted(1, 0);

        // 部门负责人只看本部门岗位（对齐 Python）
        String role = SecurityUtils.getRoleCode();
        if ("dept_head".equals(role)) {
            Long deptId = null;
            Optional<IamUser> optUser = userRepository.findFirstActiveByUserId(
                    SecurityUtils.getUserId());
            if (optUser.isPresent() && optUser.get().getDeptId() != null) {
                deptId = optUser.get().getDeptId();
            }
            if (deptId != null) {
                final Long filterDeptId = deptId;
                positions = positions.stream()
                        .filter(p -> filterDeptId.equals(p.getDeptId()))
                        .toList();
            }
        }

        return ApiResponse.success(positions);
    }

    @GetMapping("/positions/{positionId}")
    public ApiResponse<IamPosition> positionDetail(@PathVariable Long positionId) {
        return ApiResponse.success(positionRepository.findById(positionId)
                .orElseThrow(() -> BusinessException.notFound("岗位不存在")));
    }

    /**
     * 面试官下拉（对齐 Flask GET /api/auth/interviewers）。
     * 角色：admin, hr, dept_head, director, interviewer, temp_interviewer
     */
    @GetMapping("/interviewers")
    @RequireRole({"admin", "hr", "dept_head", "director"})
    public ApiResponse<List<Map<String, Object>>> interviewers() {
        List<String> roles = List.of("interviewer", "temp_interviewer", "dept_head", "hr", "admin");
        List<IamUser> users = userRepository.findByRoleCodeInAndStatusAndIsDeleted(roles, 1, 0);
        List<Map<String, Object>> result = users.stream()
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", String.valueOf(u.getUserId()));
                    m.put("name", u.getRealName());
                    return m;
                })
                .toList();
        return ApiResponse.success(result);
    }
}
