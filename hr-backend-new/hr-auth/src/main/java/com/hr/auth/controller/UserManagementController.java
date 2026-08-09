package com.hr.auth.controller;

import com.hr.auth.entity.IamDept;
import com.hr.auth.entity.IamPosition;
import com.hr.auth.entity.IamUser;
import com.hr.auth.repository.IamDeptRepository;
import com.hr.auth.repository.IamPositionRepository;
import com.hr.auth.repository.IamUserRepository;
import com.hr.auth.service.UserManagementService;
import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户 / 部门 / 岗位管理端点（admin），对齐 Flask api/auth.py 管理部分。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@RequireRole({"admin"})
public class UserManagementController {

    private final UserManagementService managementService;
    private final IamUserRepository userRepository;
    private final IamDeptRepository deptRepository;
    private final IamPositionRepository positionRepository;

    // ── 用户管理 ──────────────────────────────────────────────

    @PostMapping("/users")
    public ApiResponse<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(managementService.createUser(body));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.success(managementService.updateUser(id, body));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<Map<String, Object>> toggleUserStatus(@PathVariable Long id) {
        return ApiResponse.success(managementService.toggleUserStatus(id));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        managementService.deleteUser(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/users/{id}/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable Long id) {
        return ApiResponse.success(managementService.resetPassword(id));
    }

    @PostMapping("/users/batch")
    public ApiResponse<Map<String, Object>> batchCreate(@RequestBody Map<String, Object> body) {
        Object usersObj = body != null ? body.get("users") : null;
        List<Map<String, Object>> users = new ArrayList<>();
        if (usersObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    m.forEach((k, v) -> item.put(String.valueOf(k), v));
                    users.add(item);
                }
            }
        }
        return ApiResponse.success(managementService.batchCreateUsers(users));
    }

    @PutMapping("/change-password")
    public ApiResponse<Map<String, Object>> changePassword(@RequestBody Map<String, Object> body) {
        String oldPwd = body != null && body.get("oldPassword") != null
                ? String.valueOf(body.get("oldPassword")) : null;
        String newPwd = body != null && body.get("newPassword") != null
                ? String.valueOf(body.get("newPassword")) : null;
        return ApiResponse.success(managementService.changePassword(SecurityUtils.getUserId(), oldPwd, newPwd));
    }

    @GetMapping("/pending-accounts")
    public ApiResponse<List<Map<String, Object>>> pendingAccounts() {
        return ApiResponse.success(findPendingAccounts());
    }

    private List<Map<String, Object>> findPendingAccounts() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<IamUser> allUsers = userRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("isDeleted"), 0));
        for (IamUser u : allUsers) {
            if (u.getStatus() != null && u.getStatus() == 1 && u.getMustChangePassword() != null
                    && u.getMustChangePassword() == 1) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("candidateId", u.getUserId());
                m.put("candidateName", u.getRealName());
                m.put("mobile", u.getMobile() != null ? u.getMobile() : "");
                m.put("email", u.getEmail() != null ? u.getEmail() : "");
                m.put("deptId", u.getDeptId());
                m.put("deptName", resolveDeptName(u.getDeptId()));
                m.put("positionId", u.getPositionId());
                m.put("positionName", resolvePositionName(u.getPositionId()));
                m.put("statusLabel", "待首次登录");
                result.add(m);
            }
        }
        return result;
    }

    private String resolveDeptName(Long deptId) {
        if (deptId == null) {
            return "";
        }
        return deptRepository.findById(deptId)
                .map(IamDept::getDeptName).orElse("");
    }

    private String resolvePositionName(Long positionId) {
        if (positionId == null) {
            return "";
        }
        return positionRepository.findById(positionId)
                .map(IamPosition::getPositionName).orElse("");
    }

    // ── 部门管理 ──────────────────────────────────────────────

    @PostMapping("/departments")
    public ApiResponse<Map<String, Object>> createDepartment(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(managementService.createDepartment(body));
    }

    @PutMapping("/departments/{id}")
    public ApiResponse<Map<String, Object>> updateDepartment(@PathVariable Long id,
                                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.success(managementService.updateDepartment(id, body));
    }

    @DeleteMapping("/departments/{id}")
    public ApiResponse<Void> deleteDepartment(@PathVariable Long id) {
        managementService.deleteDepartment(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/departments/{id}/status")
    public ApiResponse<Map<String, Object>> toggleDepartmentStatus(@PathVariable Long id,
                                                                   @RequestBody Map<String, Object> body) {
        int status = body != null && body.get("status") != null
                ? ((Number) body.get("status")).intValue() : 1;
        return ApiResponse.success(managementService.toggleDepartmentStatus(id, status));
    }

    // ── 岗位管理 ──────────────────────────────────────────────

    @PostMapping("/positions")
    public ApiResponse<Map<String, Object>> createPosition(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(managementService.createPosition(body));
    }

    @PutMapping("/positions/{id}")
    public ApiResponse<Map<String, Object>> updatePosition(@PathVariable Long id,
                                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.success(managementService.updatePosition(id, body));
    }

    @DeleteMapping("/positions/{id}")
    public ApiResponse<Void> deletePosition(@PathVariable Long id) {
        managementService.deletePosition(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/positions/{id}/status")
    public ApiResponse<Map<String, Object>> togglePositionStatus(@PathVariable Long id,
                                                                 @RequestBody Map<String, Object> body) {
        int status = body != null && body.get("status") != null
                ? ((Number) body.get("status")).intValue() : 1;
        return ApiResponse.success(managementService.togglePositionStatus(id, status));
    }
}
