package com.hr.integration.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 飞书开放平台客户端：租户令牌 + 视频会议 + 消息推送。
 *
 * 会议：POST /vc/v1/reserves/apply
 * 消息：POST /im/v1/messages?receive_id_type=open_id
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuClient {

    private static final String API_BASE = "https://open.feishu.cn/open-apis";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ConfigCredentials configCredentials;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${feishu.app-id:}")
    private String appId;

    @Value("${feishu.app-secret:}")
    private String appSecret;

    private volatile String token;
    private volatile long tokenExpiresAt;

    public boolean isConfigured() {
        return getAppId() != null && !getAppId().isBlank()
                && getAppSecret() != null && !getAppSecret().isBlank();
    }

    /** 环境变量优先，其次数据库配置页保存的凭证。 */
    private String getAppId() {
        if (appId != null && !appId.isBlank()) {
            return appId;
        }
        return configCredentials.get("feishu_app_id");
    }

    private String getAppSecret() {
        if (appSecret != null && !appSecret.isBlank()) {
            return appSecret;
        }
        return configCredentials.get("feishu");
    }

    // ════════════════════════════════════════════════════════════
    // 视频会议
    // ════════════════════════════════════════════════════════════

    /**
     * 创建视频会议预约。
     *
     * @param topic          会议主题
     * @param startEpochSec  开始时间（unix 秒）
     * @param durationMinutes 时长（分钟）
     * @return {"meeting_url", "meeting_code", "error"}；
     *         未配置时 meeting_url 为 null，error 包含原因说明
     */
    public Map<String, String> createVcMeeting(String topic, long startEpochSec, int durationMinutes) {
        if (!isConfigured()) {
            log.info("[FEISHU] createVcMeeting 跳过 — 未配置飞书凭证");
            return Map.of("meeting_url", "",
                    "meeting_code", "",
                    "error", "飞书未配置：请在 application.yml 设置 FEISHU_APP_ID / FEISHU_APP_SECRET 环境变量，或写入 t_hr_api_key_config 表");
        }
        long endEpochSec = startEpochSec + (long) durationMinutes * 60;
        String body = """
                {
                  "end_time": "%d",
                  "meeting_settings": {
                    "topic": "%s",
                    "meeting_initial_type": 1,
                    "action_permissions": []
                  },
                  "end_meeting_at_once": false
                }
                """.formatted(endEpochSec, escapeJson(topic));

        try {
            JsonNode data = post("/vc/v1/reserves/apply", body);
            JsonNode reserve = data.path("reserve");
            String url = reserve.path("url").asText("");
            String meetingNo = reserve.path("meeting_no").asText("");
            if (url.isEmpty()) {
                log.warn("飞书会议创建返回空链接: reserve={}", reserve);
                return Map.of("meeting_url", "",
                        "meeting_code", meetingNo,
                        "error", "飞书 API 返回了空入会链接，请联系管理员检查飞书应用权限");
            }
            log.info("飞书会议创建成功: url={} meeting_no={} topic={}", url, meetingNo, topic);
            return Map.of("meeting_url", url,
                    "meeting_code", meetingNo,
                    "error", "");
        } catch (Exception e) {
            log.warn("飞书创建会议失败: {}", e.getMessage());
            return Map.of("meeting_url", "",
                    "meeting_code", "",
                    "error", "飞书 API 调用失败: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // 消息推送
    // ════════════════════════════════════════════════════════════

    /**
     * 发送文本消息到指定用户的飞书客户端。
     *
     * @param openId 接收者 feishu_open_id（ou_ 前缀）
     * @param text   文本内容
     * @return {"success": true/false, "message_id": "..."}
     */
    public Map<String, Object> sendTextMessage(String openId, String text) {
        if (!isConfigured()) {
            log.info("[FEISHU] sendTextMessage 跳过 — 未配置飞书凭证, text={}", text.length() > 60 ? text.substring(0, 60) + "..." : text);
            return Map.of("success", false, "reason", "飞书未配置");
        }
        if (openId == null || openId.isBlank()) {
            log.warn("[FEISHU] sendTextMessage 跳过 — openId 为空");
            return Map.of("success", false, "reason", "接收人 openId 为空");
        }
        try {
            Map<String, Object> msgBody = new LinkedHashMap<>();
            msgBody.put("receive_id", openId);
            msgBody.put("msg_type", "text");
            msgBody.put("content", mapper.writeValueAsString(Map.of("text", text)));

            String path = "/im/v1/messages?receive_id_type=open_id";
            JsonNode data = post(path, mapper.writeValueAsString(msgBody));
            String messageId = data.path("message_id").asText("");
            log.info("飞书消息发送成功: openId={} messageId={}", openId, messageId);
            return Map.of("success", true, "message_id", messageId);
        } catch (Exception e) {
            log.warn("飞书消息发送失败: openId={} error={}", openId, e.getMessage());
            return Map.of("success", false, "reason", e.getMessage());
        }
    }

    /**
     * 发送面试逾期告警消息。
     * 未配置或 openId 为空时仅日志记录，不抛异常。
     *
     * @param interviewerName 面试官姓名（用于日志和消息正文）
     * @param openId          面试官飞书 open_id，可为 null
     * @param overdueCount    逾期条数
     * @return {"success": true/false}
     */
    public Map<String, Object> sendOverdueAlert(String interviewerName, String openId, int overdueCount) {
        if (!isConfigured()) {
            log.info("[FEISHU] sendOverdueAlert 跳过 — 飞书未配置 (面试官={}, 逾期{}条)",
                    interviewerName, overdueCount);
            return Map.of("success", false, "reason", "飞书未配置");
        }
        if (openId == null || openId.isBlank()) {
            log.info("[FEISHU] sendOverdueAlert 跳过 — 面试官 {} 无飞书 open_id (逾期{}条)",
                    interviewerName, overdueCount);
            return Map.of("success", false, "reason", "面试官未绑定飞书");
        }

        String text = "【面试评价提醒】\n"
                + "您有 " + overdueCount + " 条面试超过 3 天未完成评价，请尽快登录系统处理。\n"
                + "面试官: " + interviewerName + "\n"
                + "逾期条数: " + overdueCount;

        return sendTextMessage(openId, text);
    }

    // ════════════════════════════════════════════════════════════
    // HTTP 通用
    // ════════════════════════════════════════════════════════════

    private JsonNode post(String path, String jsonBody) throws Exception {
        Request request = new Request.Builder()
                .url(API_BASE + path)
                .header("Authorization", "Bearer " + getTenantAccessToken())
                .post(RequestBody.create(jsonBody, JSON))
                .build();
        try (Response resp = http.newCall(request).execute()) {
            String text = resp.body() != null ? resp.body().string() : "";
            JsonNode json = mapper.readTree(text);
            int code = json.path("code").asInt(-1);
            if (code != 0) {
                throw new IllegalStateException("飞书 API " + path + " 失败: code=" + code
                        + " msg=" + json.path("msg").asText(""));
            }
            return json.path("data");
        }
    }

    private String getTenantAccessToken() throws Exception {
        if (token != null && System.currentTimeMillis() / 1000 < tokenExpiresAt - 60) {
            return token;
        }
        String body = mapper.writeValueAsString(Map.of(
                "app_id", getAppId(), "app_secret", getAppSecret()));
        Request request = new Request.Builder()
                .url(API_BASE + "/auth/v3/tenant_access_token/internal")
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response resp = http.newCall(request).execute()) {
            JsonNode json = mapper.readTree(resp.body() != null ? resp.body().string() : "");
            int code = json.path("code").asInt(-1);
            if (code != 0) {
                throw new IllegalStateException("获取 tenant_access_token 失败: code=" + code
                        + " msg=" + json.path("msg").asText(""));
            }
            token = json.path("tenant_access_token").asText("");
            tokenExpiresAt = Instant.now().getEpochSecond() + json.path("expire").asLong(7200);
            return token;
        }
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
