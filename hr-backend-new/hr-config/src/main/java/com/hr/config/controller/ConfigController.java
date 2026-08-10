package com.hr.config.controller;

import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @PostMapping("/email-accounts")
    public ApiResponse<Map<String, Object>> createEmailAccount(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.createEmailAccount(body));
    }

    @PutMapping("/email-accounts/{id}")
    public ApiResponse<Map<String, Object>> updateEmailAccount(@PathVariable Long id,
                                                               @RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.updateEmailAccount(id, body));
    }

    @DeleteMapping("/email-accounts/{id}")
    public ApiResponse<Map<String, Object>> deleteEmailAccount(@PathVariable Long id) {
        return ApiResponse.success(configService.deleteEmailAccount(id));
    }

    @PostMapping("/email-accounts/sync")
    public ApiResponse<Map<String, Object>> syncAllEmailAccounts() {
        return ApiResponse.success(configService.syncAllEmailAccounts());
    }

    @PostMapping("/email-accounts/{id}/sync")
    public ApiResponse<Map<String, Object>> syncEmailAccount(@PathVariable Long id) {
        return ApiResponse.success(Map.of("accepted", true, "mode", "async",
                "taskId", "sync-" + id, "message", "同步已开始，请稍后刷新查看结果"));
    }

    @GetMapping("/email-accounts/resolve")
    public ApiResponse<Map<String, Object>> resolveEmailServer(@RequestParam String email) {
        return ApiResponse.success(configService.resolveEmailServer(email));
    }

    /**
     * POST /api/config/email-accounts/detect — 自动探测 IMAP 服务器。
     * 对齐 Flask POST /api/config/email-accounts/detect。
     */
    @PostMapping("/email-accounts/detect")
    public ApiResponse<Map<String, Object>> detectImapServer(@RequestBody(required = false) Map<String, Object> body) {
        String email = body != null && body.get("email") != null
                ? String.valueOf(body.get("email")).trim() : "";
        return ApiResponse.success(configService.detectImapServer(email));
    }

    /**
     * POST /api/config/email-accounts/get-preview — 邮箱预览（不实际连接）。
     * 对齐 Flask POST /api/config/email-accounts/get-preview。
     */
    @PostMapping("/email-accounts/get-preview")
    public ApiResponse<List<Map<String, Object>>> getEmailPreview() {
        return ApiResponse.success(configService.getEmailPreview());
    }

    /**
     * GET /api/config/tencent-meeting/status — 腾讯会议配置状态。
     */
    @GetMapping("/tencent-meeting/status")
    public ApiResponse<Map<String, Object>> tencentMeetingStatus() {
        return ApiResponse.success(configService.getTencentMeetingStatus());
    }

    /**
     * GET /api/config/feishu/status — 飞书配置状态。
     */
    @GetMapping("/feishu/status")
    public ApiResponse<Map<String, Object>> feishuStatus() {
        return ApiResponse.success(configService.getFeishuStatus());
    }

    @GetMapping("/role-permissions")
    public ApiResponse<List<Map<String, Object>>> rolePermissions() {
        return ApiResponse.success(configService.getRolePermissions());
    }

    @PutMapping("/role-permissions")
    public ApiResponse<Map<String, Object>> updateRolePermissions(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.updateRolePermissions(body));
    }

    @GetMapping("/notify-templates")
    public Map<String, Object> notifyTemplates() {
        return listResponse(configService.getNotifyTemplates());
    }

    @PostMapping("/notify-templates")
    public ApiResponse<Map<String, Object>> createNotifyTemplate(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.createNotifyTemplate(body));
    }

    @PutMapping("/notify-templates/{id}")
    public ApiResponse<Map<String, Object>> updateNotifyTemplate(@PathVariable Long id,
                                                                 @RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.updateNotifyTemplate(id, body));
    }

    @DeleteMapping("/notify-templates/{id}")
    public ApiResponse<Map<String, Object>> deleteNotifyTemplate(@PathVariable Long id) {
        return ApiResponse.success(configService.deleteNotifyTemplate(id));
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

    @PutMapping("/api-keys")
    public ApiResponse<Map<String, Object>> saveApiKeys(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.saveApiKeys(body));
    }

    @PostMapping("/api-keys/test")
    public ApiResponse<Map<String, Object>> testApiKey(@RequestBody(required = false) Map<String, Object> body) {
        String keyName = body != null && body.get("key_name") != null
                ? String.valueOf(body.get("key_name")) : "";
        if (keyName.isBlank()) {
            return ApiResponse.success(Map.of("ok", false, "supported", false, "message", "缺少 key_name"));
        }
        return ApiResponse.success(configService.testApiKey(keyName));
    }

    private Map<String, Object> listResponse(List<Map<String, Object>> data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data);
        m.put("total", data.size());
        return m;
    }
}
