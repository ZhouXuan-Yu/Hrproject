package com.hr.bootstrap.controller;

import com.hr.common.dto.ApiResponse;
import com.hr.common.util.Sha256Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查端点，对齐 Flask 的 /api/health 与 /api/v1/health。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @Autowired
    private Environment env;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> db = new HashMap<>();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            db.put("connected", true);
            db.put("type", env.getProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"));
        } catch (Exception e) {
            db.put("connected", false);
            db.put("error", e.getMessage());
        }
        data.put("database", db);

        Map<String, Object> redis = new HashMap<>();
        redis.put("enabled", true);
        try {
            if (redisTemplate != null) {
                String ping = redisTemplate.getConnectionFactory().getConnection().ping();
                redis.put("connected", "PONG".equalsIgnoreCase(String.valueOf(ping)));
            } else {
                redis.put("connected", false);
            }
        } catch (Exception e) {
            redis.put("connected", false);
            redis.put("error", e.getMessage());
        }
        data.put("redis", redis);

        String deepseekKey = env.getProperty("deepseek.api-key", "");
        Map<String, Object> deepseek = new HashMap<>();
        deepseek.put("key_configured", deepseekKey != null && !deepseekKey.isEmpty());
        data.put("deepseek", deepseek);
        data.put("mock_fallback", false);
        data.put("status", "ok");
        return data;
    }

    @GetMapping("/v1/health")
    public Map<String, Object> v1Health() {
        Map<String, Object> components = new LinkedHashMap<>();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            components.put("database", "ok");
        } catch (Exception e) {
            components.put("database", Map.of("status", "error", "message", "数据库连接异常"));
        }
        try {
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            components.put("redis", "PONG".equalsIgnoreCase(String.valueOf(ping)) ? "ok" : "unreachable");
        } catch (Exception e) {
            components.put("redis", "unreachable");
        }
        String deepseekKey = env.getProperty("deepseek.api-key", "");
        components.put("deepseek", (deepseekKey != null && !deepseekKey.isEmpty()) ? "configured" : "unconfigured");
        components.put("version", "0.1.0");
        return components;
    }
}
