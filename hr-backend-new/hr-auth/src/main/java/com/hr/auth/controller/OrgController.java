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
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    @Cacheable(cacheNames = "org", key = "'departments'")
    public ApiResponse<List<IamDept>> departments() {
        return ApiResponse.success(deptRepository.findByStatusAndIsDeleted(1, 0));
    }

    @GetMapping("/departments/{deptId}")
    public ApiResponse<IamDept> departmentDetail(@PathVariable Long deptId) {
        return ApiResponse.success(deptRepository.findById(deptId)
                .orElseThrow(() -> BusinessException.notFound("部门不存在")));
    }

    @GetMapping("/positions")
    @Cacheable(cacheNames = "org", key = "'positions'")
    public ApiResponse<List<IamPosition>> positions() {
        return ApiResponse.success(positionRepository.findByStatusAndIsDeleted(1, 0));
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
