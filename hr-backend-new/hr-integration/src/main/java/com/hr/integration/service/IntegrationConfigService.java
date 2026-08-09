package com.hr.integration.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部集成配置状态服务，从环境变量读取飞书/腾讯会议配置状态。
 */
@Service
@RequiredArgsConstructor
public class IntegrationConfigService {

    private final Environment env;

    public Map<String, Object> getFeishuStatus() {
        boolean appId = hasText(env.getProperty("FEISHU_APP_ID"));
        boolean appSecret = hasText(env.getProperty("FEISHU_APP_SECRET"));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("app_id", appId);
        fields.put("app_secret", appSecret);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", appId && appSecret);
        result.put("fields", fields);
        return result;
    }

    public Map<String, Object> getTencentMeetingStatus() {
        String[] keyNames = {"tencent_appid", "tencent_secretid", "tencent_secretkey", "tencent_userid"};
        Map<String, Object> fields = new LinkedHashMap<>();
        boolean all = true;
        for (String k : keyNames) {
            boolean has = hasText(env.getProperty(k));
            fields.put(k, has);
            all &= has;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", all);
        result.put("fields", fields);
        return result;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
