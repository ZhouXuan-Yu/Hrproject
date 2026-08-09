package com.hr.interview.controller;

import com.hr.common.dto.ApiResponse;
import com.hr.common.util.SecurityUtils;
import com.hr.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 面试管理接口 /api/interview/*，对齐 Flask api/interview.py。
 */
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * GET /api/interview/list — 面试列表（分页 + 状态筛选）。
     */
    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return interviewService.listInterviews(page, pageSize, status);
    }

    /**
     * POST /api/interview/create — 创建面试预约（含去重锁）。
     */
    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(interviewService.createInterview(body, SecurityUtils.getUserId()));
    }

    /**
     * POST /api/interview/{id}/evaluate — 提交面试评价。
     */
    @PostMapping("/{id}/evaluate")
    public ApiResponse<Map<String, Object>> evaluate(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.success(interviewService.evaluateInterview(id, body, SecurityUtils.getUserId()));
    }

    /**
     * DELETE /api/interview/{id} — 取消面试。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null
                ? String.valueOf(body.get("reason")) : "";
        return ApiResponse.success(interviewService.cancelInterview(id, reason));
    }
}
