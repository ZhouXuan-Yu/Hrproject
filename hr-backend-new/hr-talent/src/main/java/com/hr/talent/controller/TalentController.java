package com.hr.talent.controller;

import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.SecurityUtils;
import com.hr.talent.service.TalentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * GET /api/talent/match — 内部员工人岗匹配结果。
     */
    @GetMapping("/match")
    public ApiResponse<Map<String, Object>> matchResults(@RequestParam String demandId) {
        return ApiResponse.success(talentService.getMatchResults(demandId));
    }

    /**
     * POST /api/talent/match — 计算候选人对需求的匹配分。
     */
    @PostMapping("/match")
    public ApiResponse<Map<String, Object>> createMatch(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(talentService.createMatch(body));
    }

    /**
     * POST /api/talent/link — 批量关联候选人到需求。
     */
    @PostMapping("/link")
    public ApiResponse<Map<String, Object>> link(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(talentService.linkToDemand(body));
    }

    /**
     * GET /api/talent/candidate/{id}/contact-info — 完整联系方式（HR 及以上角色）。
     */
    @GetMapping("/candidate/{id}/contact-info")
    public ApiResponse<Map<String, Object>> contactInfo(@PathVariable Long id) {
        requireHrLevel();
        return ApiResponse.success(talentService.getCandidateContact(id));
    }

    /**
     * POST /api/talent/contact — 记录（可选发送）候选人联系动作。
     */
    @PostMapping("/contact")
    public ApiResponse<Map<String, Object>> contact(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(talentService.recordContact(body));
    }

    /**
     * GET /api/talent/ingest-log — 最近简历入库记录。
     */
    @GetMapping("/ingest-log")
    public ApiResponse<Map<String, Object>> ingestLog(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(Map.of("items", talentService.getIngestLog(limit)));
    }

    /**
     * GET /api/talent/mail-log — 系统外发邮件日志。
     */
    @GetMapping("/mail-log")
    public ApiResponse<Map<String, Object>> mailLog(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(Map.of("items", talentService.getMailLog(limit)));
    }

    /**
     * POST /api/talent/upload-resume — 简历文件上传（multipart）。
     */
    @PostMapping(value = "/upload-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String note) {
        return ApiResponse.success(talentService.uploadResume(file, position, note));
    }

    /**
     * GET /api/talent/resume-file/{resumeId} — 简历原件下载/预览（HR 及以上）。
     */
    @GetMapping("/resume-file/{resumeId}")
    public ResponseEntity<Resource> resumeFile(@PathVariable Long resumeId) {
        requireHrLevel();
        Path path = talentService.resolveResumeFilePath(resumeId);
        try {
            Resource resource = new UrlResource(path.toUri());
            String encoded = URLEncoder.encode(path.getFileName().toString(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename*=UTF-8''" + encoded)
                    .body(resource);
        } catch (Exception e) {
            throw BusinessException.notFound("简历文件读取失败");
        }
    }

    /**
     * GET /api/talent/candidate/{id}/export — 候选人数据导出（PIPL）。
     */
    @GetMapping("/candidate/{id}/export")
    public ApiResponse<Map<String, Object>> export(@PathVariable Long id) {
        return ApiResponse.success(talentService.exportCandidate(id));
    }

    /**
     * DELETE /api/talent/candidate/{id}/hard — 彻底删除候选人及关联数据（PIPL，仅 admin）。
     */
    @DeleteMapping("/candidate/{id}/hard")
    @RequireRole({"admin"})
    public ApiResponse<Map<String, Object>> hardDelete(@PathVariable Long id) {
        return ApiResponse.success(talentService.hardDeleteCandidate(id));
    }

    private void requireHrLevel() {
        String role = SecurityUtils.getRoleCode();
        if (role == null) {
            throw BusinessException.unauthorized();
        }
        if (!"admin".equals(role) && !"hr".equals(role)
                && !"director".equals(role) && !"dept_head".equals(role)) {
            throw BusinessException.forbidden();
        }
    }
}
