package com.hr.bootstrap.controller;

import com.hr.common.dto.ApiResponse;
import com.hr.common.util.Sha256Util;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            db.put("connected", jdbcTemplate != null
                    && jdbcTemplate.queryForObject("SELECT 1", Integer.class) != null);
        } catch (Exception e) {
            db.put("connected", false);
        }
        data.put("database", db);

        Map<String, Object> redis = new LinkedHashMap<>();
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
        }
        data.put("redis", redis);
        data.put("mock_fallback", false);
        data.put("status", Boolean.TRUE.equals(((Map<?, ?>) db).get("connected"))
                && Boolean.TRUE.equals(((Map<?, ?>) redis).get("connected")) ? "ok" : "degraded");
        return data;
    }

    @GetMapping("/v1/health")
    public Map<String, Object> v1Health() {
        Map<String, Object> components = new LinkedHashMap<>();
        try {
            components.put("database", jdbcTemplate != null
                    && jdbcTemplate.queryForObject("SELECT 1", Integer.class) != null ? "ok" : "unreachable");
        } catch (Exception e) {
            components.put("database", "unreachable");
        }
        try {
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            components.put("redis", "PONG".equalsIgnoreCase(String.valueOf(ping)) ? "ok" : "unreachable");
        } catch (Exception e) {
            components.put("redis", "unreachable");
        }
        components.put("version", "0.1.0");
        return components;
    }
}
