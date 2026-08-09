package com.hr.demand.controller;

import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.common.util.SecurityUtils;
import com.hr.demand.service.DemandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 需求管理接口 /api/demand/*，对齐 Flask api/demand.py。
 */
@RestController
@RequestMapping("/api/demand")
@RequiredArgsConstructor
@RequireRole({"admin", "hr", "dept_head", "director", "employee"})
public class DemandController {

    private final DemandService demandService;

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long deptId) {
        return demandService.listDemands(page, pageSize, keyword, status, deptId,
                SecurityUtils.getRoleCode(), SecurityUtils.getUserId(),
                SecurityUtils.getCurrentUser() != null ? SecurityUtils.getCurrentUser().getDeptId() : null);
    }

    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(demandService.createDemand(body,
                SecurityUtils.getUserId(), SecurityUtils.getRoleCode()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(demandService.getDemandDetail(id));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        return ApiResponse.success(demandService.submitForApproval(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        String opinion = body != null && body.get("opinion") != null
                ? String.valueOf(body.get("opinion")) : null;
        return ApiResponse.success(demandService.approve(id, SecurityUtils.getUserId(),
                SecurityUtils.getRoleCode(), opinion));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        String opinion = body != null && body.get("opinion") != null
                ? String.valueOf(body.get("opinion")) : null;
        return ApiResponse.success(demandService.reject(id, SecurityUtils.getUserId(),
                SecurityUtils.getRoleCode(), opinion));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null
                ? String.valueOf(body.get("reason")) : null;
        return ApiResponse.success(demandService.close(id, reason));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        demandService.deleteDemand(id);
        return ApiResponse.success(null);
    }
}
