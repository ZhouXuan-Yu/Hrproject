package com.hr.demand.controller;

import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.common.util.SecurityUtils;
import com.hr.demand.service.DemandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 需求管理接口 /api/demand/*，对齐 Flask api/demand.py。
 */
@RestController
@RequestMapping("/api/demand")
@RequiredArgsConstructor
@RequireRole({"admin", "hr", "dept_head", "director", "employee"})
public class DemandController {

    private final DemandService demandService;

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long deptId) {
        return demandService.listDemands(page, pageSize, keyword, status, deptId,
                SecurityUtils.getRoleCode(), SecurityUtils.getUserId(),
                SecurityUtils.getCurrentUser() != null ? SecurityUtils.getCurrentUser().getDeptId() : null);
    }

    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(demandService.createDemand(body,
                SecurityUtils.getUserId(), SecurityUtils.getRoleCode()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        return ApiResponse.success(demandService.getDemandDetail(demandService.resolveDemandId(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.success(demandService.updateDemand(demandService.resolveDemandId(id), body));
    }

    @GetMapping("/{id}/candidates")
    public ApiResponse<List<Map<String, Object>>> candidates(@PathVariable String id,
                                                             @RequestParam(required = false) String source) {
        return ApiResponse.success(demandService.listDemandCandidates(demandService.resolveDemandId(id), source));
    }

    @GetMapping("/{id}/candidates/{name}/detail")
    public ApiResponse<Map<String, Object>> candidateDetail(@PathVariable String id,
                                                            @PathVariable String name) {
        return ApiResponse.success(demandService.getCandidateMatchDetail(demandService.resolveDemandId(id), name));
    }

    @PostMapping("/{id}/candidates/{name}/link")
    public ApiResponse<Map<String, Object>> linkCandidate(@PathVariable String id,
                                                          @PathVariable String name,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        boolean link = body == null || !Boolean.FALSE.equals(body.get("link"));
        if (!link) {
            return ApiResponse.success(demandService.unlinkCandidate(demandService.resolveDemandId(id), name));
        }
        return ApiResponse.success(demandService.linkCandidate(demandService.resolveDemandId(id), name));
    }

    @PostMapping("/{id}/match")
    public ApiResponse<Map<String, Object>> match(@PathVariable String id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(demandService.matchCandidates(demandService.resolveDemandId(id), body));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String id) {
        return ApiResponse.success(demandService.submitForApproval(demandService.resolveDemandId(id)));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable String id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        String opinion = body != null && body.get("opinion") != null
                ? String.valueOf(body.get("opinion")) : null;
        Integer level = body != null && body.get("level") instanceof Number n
                ? n.intValue() : null;
        return ApiResponse.success(demandService.approve(demandService.resolveDemandId(id), SecurityUtils.getUserId(),
                SecurityUtils.getRoleCode(), opinion, level));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable String id,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        String opinion = body != null && body.get("opinion") != null
                ? String.valueOf(body.get("opinion")) : null;
        Integer level = body != null && body.get("level") instanceof Number n
                ? n.intValue() : null;
        return ApiResponse.success(demandService.reject(demandService.resolveDemandId(id), SecurityUtils.getUserId(),
                SecurityUtils.getRoleCode(), opinion, level));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable String id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null
                ? String.valueOf(body.get("reason")) : null;
        return ApiResponse.success(demandService.close(demandService.resolveDemandId(id), reason));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        demandService.deleteDemand(demandService.resolveDemandId(id));
        return ApiResponse.success(null);
    }
}
