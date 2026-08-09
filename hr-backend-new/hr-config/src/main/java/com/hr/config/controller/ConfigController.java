package com.hr.config.controller;

import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础配置接口 /api/config/*，对齐 Flask api/config.py。
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@RequireRole({"admin", "hr"})
public class ConfigController {

    private final ConfigService configService;

    @GetMapping("/channels")
    public Map<String, Object> channels() {
        List<Map<String, Object>> data = configService.getChannels();
        return listResponse(data);
    }

    @PostMapping("/channels")
    public ApiResponse<Map<String, Object>> createChannel(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.createChannel(body));
    }

    @PutMapping("/channels/{code}")
    public ApiResponse<Map<String, Object>> updateChannel(@PathVariable String code,
                                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.updateChannel(code, body));
    }

    @GetMapping("/email-accounts")
    public Map<String, Object> emailAccounts() {
        return listResponse(configService.getEmailAccounts());
    }

    @GetMapping("/notify-templates")
    public Map<String, Object> notifyTemplates() {
        return listResponse(configService.getNotifyTemplates());
    }

    @GetMapping("/knowledge-base")
    public ApiResponse<Map<String, Object>> knowledgeBase() {
        return ApiResponse.success(configService.getKnowledgeBase());
    }

    @PutMapping("/knowledge-base")
    public ApiResponse<Map<String, Object>> updateKnowledgeBase(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.updateKnowledgeBase(body));
    }

    @GetMapping("/score-rules")
    public ApiResponse<Map<String, Object>> scoreRules() {
        return ApiResponse.success(configService.getScoreRules());
    }

    @PutMapping("/score-rules")
    public ApiResponse<Map<String, Object>> updateScoreRules(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.updateScoreRules(body));
    }

    @GetMapping("/audit-logs")
    public Map<String, Object> auditLogs(@RequestParam(defaultValue = "50") int limit) {
        return listResponse(configService.getAuditLogs(limit));
    }

    @GetMapping("/api-keys")
    public ApiResponse<Map<String, Object>> apiKeys() {
        return ApiResponse.success(configService.getApiKeys());
    }

    private Map<String, Object> listResponse(List<Map<String, Object>> data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data);
        m.put("total", data.size());
        return m;
    }
}
