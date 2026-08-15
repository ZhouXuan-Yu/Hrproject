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
        Map<String, Object> result = configService.createChannel(body);
        if (Boolean.TRUE.equals(result.get("created"))) {
            configService.appendAuditLog("系统", "配置", "新增渠道",
                    "新增渠道 " + result.get("name"));
        }
        return ApiResponse.success(result);
    }

    @PutMapping("/channels/{code}")
    public ApiResponse<Map<String, Object>> updateChannel(@PathVariable String code,
                                                          @RequestBody Map<String, Object> body) {
        Map<String, Object> result = configService.updateChannel(code, body);
        if (Boolean.TRUE.equals(result.get("updated"))) {
            configService.appendAuditLog("系统", "配置", "修改渠道",
                    "修改渠道 " + code);
        }
        return ApiResponse.success(result);
    }

    @DeleteMapping("/channels/{code}")
    public ApiResponse<Map<String, Object>> deleteChannel(@PathVariable String code) {
        Map<String, Object> result = configService.deleteChannel(code);
        if (Boolean.TRUE.equals(result.get("deleted"))) {
            configService.appendAuditLog("系统", "配置", "删除渠道",
                    "删除渠道 " + code);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/email-accounts")
    public Map<String, Object> emailAccounts() {
        return listResponse(configService.getEmailAccounts());
    }

    @PostMapping("/email-accounts")
    public ApiResponse<Map<String, Object>> createEmailAccount(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = configService.createEmailAccount(body);
        if (Boolean.TRUE.equals(result.get("created"))) {
            configService.appendAuditLog("系统", "配置", "添加邮箱",
                    "添加邮箱 " + result.get("address"));
        }
        return ApiResponse.success(result);
    }

    @PutMapping("/email-accounts/{id}")
    public ApiResponse<Map<String, Object>> updateEmailAccount(@PathVariable Long id,
                                                               @RequestBody Map<String, Object> body) {
        Map<String, Object> result = configService.updateEmailAccount(id, body);
        if (Boolean.TRUE.equals(result.get("deleted"))) {
            configService.appendAuditLog("系统", "配置", "删除邮箱",
                    "删除邮箱账号 #" + id);
        } else if (body != null && body.get("__test_conn") != null) {
            configService.appendAuditLog("系统", "配置", "测试连接",
                    "测试邮箱 #" + id);
        }
        return ApiResponse.success(result);
    }

    @DeleteMapping("/email-accounts/{id}")
    public ApiResponse<Map<String, Object>> deleteEmailAccount(@PathVariable Long id) {
        Map<String, Object> result = configService.deleteEmailAccount(id);
        if (Boolean.TRUE.equals(result.get("deleted"))) {
            configService.appendAuditLog("系统", "配置", "删除邮箱",
                    "删除邮箱账号 #" + id);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/email-accounts/sync")
    public ApiResponse<Map<String, Object>> syncAllEmailAccounts() {
        return ApiResponse.success(configService.syncAllEmailAccounts());
    }

    @PostMapping("/email-accounts/{id}/sync")
    public ApiResponse<Map<String, Object>> syncEmailAccount(@PathVariable Long id) {
        return ApiResponse.success(configService.syncEmailAccountById(id));
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
        Map<String, Object> result = configService.updateRolePermissions(body);
        if (Boolean.TRUE.equals(result.get("updated"))) {
            configService.appendAuditLog("系统", "配置", "更新角色权限",
                    "更新了角色菜单权限");
        }
        return ApiResponse.success(result);
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
        Map<String, Object> result = configService.updateKnowledgeBase(body);
        if (Boolean.TRUE.equals(result.get("updated"))) {
            configService.appendAuditLog("系统", "配置", "更新知识库",
                    "更新了公司画像与 AI 上下文");
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/score-rules")
    public ApiResponse<Map<String, Object>> scoreRules() {
        return ApiResponse.success(configService.getScoreRules());
    }

    @PutMapping("/score-rules")
    public ApiResponse<Map<String, Object>> updateScoreRules(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = configService.updateScoreRules(body);
        if (Boolean.TRUE.equals(result.get("updated"))) {
            configService.appendAuditLog("系统", "配置", "修改打分规则",
                    "更新了招聘打分权重与阈值");
        }
        return ApiResponse.success(result);
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
        Map<String, Object> result = configService.saveApiKeys(body);
        if (Boolean.TRUE.equals(result.get("saved"))) {
            Object keysObj = result.get("keys");
            String keysStr = keysObj instanceof List<?> list
                    ? String.join(", ", list.stream().map(String::valueOf).toList())
                    : "";
            configService.appendAuditLog("系统", "配置", "更新密钥",
                    "更新密钥: " + keysStr);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/api-keys/test")
    public ApiResponse<Map<String, Object>> testApiKey(@RequestBody(required = false) Map<String, Object> body) {
        String keyName = body != null && body.get("key_name") != null
                ? String.valueOf(body.get("key_name")) : "";
        if (keyName.isBlank()) {
            return ApiResponse.success(Map.of("ok", false, "supported", false, "message", "缺少 key_name"));
        }
        Map<String, Object> result = configService.testApiKey(keyName);
        configService.appendAuditLog("系统", "配置", "测试密钥连接",
                "测试 " + keyName + ": " + (Boolean.TRUE.equals(result.get("ok")) ? "成功" : "失败"));
        return ApiResponse.success(result);
    }

    /**
     * GET /api/config/email-rules — resume detection rule config (DB persisted).
     */
    @GetMapping("/email-rules")
    public ApiResponse<Map<String, Object>> emailRules() {
        return ApiResponse.success(configService.getEmailRules());
    }

    /**
     * PUT /api/config/email-rules — save resume detection rule config.
     */
    @PutMapping("/email-rules")
    public ApiResponse<Map<String, Object>> saveEmailRules(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(configService.saveEmailRules(body));
    }

    /**
     * GET /api/config/channel-costs — persisted channel cost overrides.
     */
    @GetMapping("/channel-costs")
    public ApiResponse<Map<String, Object>> channelCosts() {
        return ApiResponse.success(configService.getChannelCosts());
    }

    /**
     * PUT /api/config/channel-costs — save a single channel's cost.
     */
    @PutMapping("/channel-costs")
    public ApiResponse<Map<String, Object>> saveChannelCost(@RequestBody Map<String, Object> body) {
        String code = body.get("code") != null ? String.valueOf(body.get("code")) : "";
        String cost = body.get("cost") != null ? String.valueOf(body.get("cost")) : "";
        if (code.isEmpty()) {
            return ApiResponse.success(Map.of("saved", false, "message", "缺少渠道编码"));
        }
        return ApiResponse.success(configService.saveChannelCost(code, cost));
    }

    private Map<String, Object> listResponse(List<Map<String, Object>> data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data);
        m.put("total", data.size());
        return m;
    }
}
