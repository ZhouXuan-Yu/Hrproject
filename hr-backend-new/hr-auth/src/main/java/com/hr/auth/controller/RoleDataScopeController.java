package com.hr.auth.controller;

import com.hr.auth.service.DataScopeService;
import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 角色数据范围（五档）管理端点，仅管理员。个人覆盖走 /api/auth/users 的 dataScope 字段。
 */
@RestController
@RequestMapping("/api/auth/role-data-scopes")
@RequiredArgsConstructor
@RequireRole({"admin"})
public class RoleDataScopeController {

    private final DataScopeService dataScopeService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(dataScopeService.listRoleDataScopes());
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(dataScopeService.updateRoleDataScopes(body));
    }
}
