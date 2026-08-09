package com.hr.auth.controller;

import com.hr.auth.entity.IamDept;
import com.hr.auth.entity.IamPosition;
import com.hr.auth.entity.IamUser;
import com.hr.auth.repository.IamDeptRepository;
import com.hr.auth.repository.IamPositionRepository;
import com.hr.auth.repository.IamUserRepository;
import com.hr.common.dto.ApiResponse;
import com.hr.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<List<IamDept>> departments() {
        return ApiResponse.success(deptRepository.findByStatusAndIsDeleted(1, 0));
    }

    @GetMapping("/departments/{deptId}")
    public ApiResponse<IamDept> departmentDetail(@PathVariable Long deptId) {
        return ApiResponse.success(deptRepository.findById(deptId)
                .orElseThrow(() -> BusinessException.notFound("部门不存在")));
    }

    @GetMapping("/positions")
    public ApiResponse<List<IamPosition>> positions() {
        return ApiResponse.success(positionRepository.findByStatusAndIsDeleted(1, 0));
    }

    /**
     * 面试官下拉（HR/dept_head/director 角色用户）。
     */
    @GetMapping("/interviewers")
    public ApiResponse<List<Map<String, Object>>> interviewers() {
        List<IamUser> users = userRepository.findByStatusAndIsDeleted(1, 0);
        List<Map<String, Object>> result = users.stream()
                .filter(u -> isInterviewerRole(u.getRoleCode()))
                .map(u -> Map.<String, Object>of(
                        "id", u.getUserId(),
                        "name", u.getRealName(),
                        "role", u.getRoleCode(),
                        "deptId", u.getDeptId() == null ? 0L : u.getDeptId()))
                .toList();
        return ApiResponse.success(result);
    }

    private boolean isInterviewerRole(String roleCode) {
        return "hr".equals(roleCode) || "dept_head".equals(roleCode)
                || "director".equals(roleCode) || "interviewer".equals(roleCode);
    }
}
