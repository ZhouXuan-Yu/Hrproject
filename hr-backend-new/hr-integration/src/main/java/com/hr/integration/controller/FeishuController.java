package com.hr.integration.controller;

import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.integration.service.IntegrationConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 飞书/腾讯会议外部集成配置状态接口，对齐 Flask api/config.py 的
 * /api/config/feishu/status 与 /api/config/tencent-meeting/status。
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@RequireRole({"admin", "hr"})
public class FeishuController {

    private final IntegrationConfigService integrationConfigService;

    @GetMapping("/feishu/status")
    public ApiResponse<Map<String, Object>> feishuStatus() {
        return ApiResponse.success(integrationConfigService.getFeishuStatus());
    }

    @GetMapping("/tencent-meeting/status")
    public ApiResponse<Map<String, Object>> tencentMeetingStatus() {
        return ApiResponse.success(integrationConfigService.getTencentMeetingStatus());
    }
}
