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
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 飞书开放平台客户端：租户令牌 + 视频会议预约（vc/v1/reserves/apply）。
 *
 * 对齐飞书官方文档：
 * - 请求体顶层 end_time（unix 秒，必填），预约到期后自动失效
 * - meeting_settings.topic 会议主题、meeting_initial_type=1 多人会议
 * - 响应 data.reserve.url 为入会链接、meeting_no 为会议号
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
    private volatile long tokenExpiresAt; // epoch seconds

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

    /**
     * 创建视频会议预约。
     *
     * @param topic        会议主题
     * @param startEpochSec 开始时间（unix 秒）
     * @param durationMinutes 时长（分钟）
     * @return {"meeting_url", "meeting_code"}；未配置凭证时返回空
     */
    public Map<String, String> createVcMeeting(String topic, long startEpochSec, int durationMinutes) {
        if (!isConfigured()) {
            log.info("[FEISHU-MOCK] createVcMeeting topic={} — 未配置飞书凭证，跳过创建", topic);
            return Map.of("meeting_url", "", "meeting_code", "");
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
                throw new IllegalStateException("reserves/apply 未返回入会链接: " + reserve);
            }
            log.info("飞书会议创建成功: url={} meeting_no={} topic={}", url, meetingNo, topic);
            return Map.of("meeting_url", url, "meeting_code", meetingNo);
        } catch (Exception e) {
            log.warn("飞书创建会议失败: {}", e.getMessage());
            return Map.of("meeting_url", "", "meeting_code", "");
        }
    }

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
