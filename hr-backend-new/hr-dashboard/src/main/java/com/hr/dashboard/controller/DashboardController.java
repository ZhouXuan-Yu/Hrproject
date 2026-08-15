package com.hr.dashboard.controller;

import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.common.util.SecurityUtils;
import com.hr.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 招聘看板接口 /api/dashboard/*，对齐 Flask api/dashboard.py。
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@RequireRole({"admin", "hr", "dept_head", "director"})
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/kpi")
    public ApiResponse<List<Map<String, Object>>> kpi() {
        return ApiResponse.success(dashboardService.getKpi(
                SecurityUtils.getRoleCode(), SecurityUtils.getUserId()));
    }

    @GetMapping("/funnel")
    public ApiResponse<Map<String, Object>> funnel() {
        return ApiResponse.success(dashboardService.getFunnel());
    }

    @GetMapping("/dept-progress")
    public ApiResponse<List<Map<String, Object>>> deptProgress() {
        return ApiResponse.success(dashboardService.getDeptProgress());
    }

    @GetMapping("/channel")
    public ApiResponse<List<Map<String, Object>>> channel() {
        return ApiResponse.success(dashboardService.getChannel());
    }

    @GetMapping("/risk-alerts")
    public ApiResponse<List<Map<String, Object>>> riskAlerts() {
        return ApiResponse.success(dashboardService.getRiskAlerts());
    }

    @GetMapping("/monthly")
    public ApiResponse<Map<String, Object>> monthly(
            @RequestParam(required = false) Integer year,
            @RequestParam(name = "dept_id", required = false) Long deptId,
            @RequestParam(name = "position_id", required = false) Long positionId) {
        return ApiResponse.success(dashboardService.getMonthlyStats(year, deptId, positionId));
    }

    /**
     * GET /api/dashboard/home — 角色感知个人工作台首页。
     * 对齐 Flask GET /api/dashboard/home。
     */
    @GetMapping("/home")
    @RequireRole({"admin", "hr", "dept_head", "director", "employee", "interviewer", "temp_interviewer"})
    public ApiResponse<Map<String, Object>> home() {
        return ApiResponse.success(dashboardService.getHomeData(
                SecurityUtils.getRoleCode(), SecurityUtils.getUserId()));
    }
}
