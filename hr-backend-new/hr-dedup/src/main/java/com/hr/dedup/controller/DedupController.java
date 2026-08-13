package com.hr.dedup.controller;

import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.common.exception.BusinessException;
import com.hr.dedup.service.DedupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 候选人去重接口 /api/dedup/*，对齐 Flask api/dedup.py。
 */
@RestController
@RequestMapping("/api/dedup")
@RequiredArgsConstructor
@RequireRole({"admin", "hr"})
public class DedupController {

    private final DedupService dedupService;

    /**
     * POST /api/dedup/check — 按姓名/手机号/邮箱查重。
     */
    @PostMapping("/check")
    public ApiResponse<Map<String, Object>> check(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("name") && !body.containsKey("phone") && !body.containsKey("email")) {
            throw BusinessException.invalidInput("请提供姓名、手机号或邮箱中至少一项");
        }
        return ApiResponse.success(dedupService.checkDuplicates(body));
    }

    /**
     * GET /api/dedup/scan — 全局扫描重复候选人组。
     */
    @GetMapping("/scan")
    public ApiResponse<Map<String, Object>> scan() {
        return ApiResponse.success(dedupService.scanDuplicates());
    }

    /**
     * POST /api/dedup/merge — 合并重复候选人。
     */
    @PostMapping("/merge")
    public ApiResponse<Map<String, Object>> merge(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(dedupService.mergeCandidates(body));
    }
}
