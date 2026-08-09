package com.hr.talent.controller;

import com.hr.common.dto.ApiResponse;
import com.hr.talent.service.TalentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 人才库接口 /api/talent/*，对齐 Flask api/talent.py。
 */
@RestController
@RequestMapping("/api/talent")
@RequiredArgsConstructor
public class TalentController {

    private final TalentService talentService;

    /**
     * GET /api/talent/list — 候选人列表（tab=external 候选人 / tab=internal 内部员工）。
     */
    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "external") String tab) {
        if ("internal".equals(tab)) {
            return talentService.listEmployees(page, pageSize, keyword);
        }
        return talentService.listCandidates(page, pageSize, keyword, status);
    }

    /**
     * GET /api/talent/candidate/{id} — 候选人详情。
     */
    @GetMapping("/candidate/{id}")
    public ApiResponse<Map<String, Object>> candidateDetail(@PathVariable Long id) {
        return ApiResponse.success(talentService.getCandidateDetail(id));
    }

    /**
     * GET /api/talent/employee/{id} — 内部员工详情。
     */
    @GetMapping("/employee/{id}")
    public ApiResponse<Map<String, Object>> employeeDetail(@PathVariable Long id) {
        return ApiResponse.success(talentService.getEmployeeDetail(id));
    }

    /**
     * PATCH /api/talent/{id}/note — 更新候选人备注。
     */
    @PatchMapping("/{id}/note")
    public ApiResponse<Map<String, Object>> updateNote(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        String note = body != null && body.get("note") != null
                ? String.valueOf(body.get("note")) : "";
        return ApiResponse.success(talentService.updateNote(id, note));
    }
}
