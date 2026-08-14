package com.hr.hire.controller;

import com.hr.common.annotation.RequireRole;
import com.hr.common.dto.ApiResponse;
import com.hr.hire.service.HireService;
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

import java.util.Map;

/**
 * 录用管理接口 /api/hire/*，对齐 Flask api/hire.py。
 */
@RestController
@RequestMapping("/api/hire")
@RequiredArgsConstructor
@RequireRole({"admin", "hr"})
public class HireController {

    private final HireService hireService;

    @GetMapping("/offers")
    public Map<String, Object> listOffers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return hireService.listOffers(page, pageSize);
    }

    @PostMapping("/offer/create")
    public ApiResponse<Map<String, Object>> createOffer(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(hireService.createOffer(body));
    }

    @GetMapping("/offer/{id}")
    public ApiResponse<Map<String, Object>> getOffer(@PathVariable String id) {
        return ApiResponse.success(hireService.getOffer(id));
    }

    @PatchMapping("/offer/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable String id,
                                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.success(hireService.updateOfferStatus(id, body));
    }

    @PostMapping("/offer/{id}/send")
    public ApiResponse<Map<String, Object>> send(@PathVariable String id) {
        return ApiResponse.success(hireService.sendOffer(id));
    }

    @PostMapping("/offer/{id}/accept")
    public ApiResponse<Map<String, Object>> accept(@PathVariable String id) {
        return ApiResponse.success(hireService.acceptOffer(id));
    }

    @PostMapping("/offer/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable String id,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null
                ? String.valueOf(body.get("reason")) : null;
        return ApiResponse.success(hireService.rejectOffer(id, reason));
    }

    @DeleteMapping("/offer/{id}")
    public ApiResponse<Map<String, Object>> withdraw(@PathVariable String id,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null
                ? String.valueOf(body.get("reason")) : null;
        return ApiResponse.success(hireService.withdrawOffer(id, reason));
    }

    @PostMapping("/offers/expire")
    public ApiResponse<Map<String, Object>> expireOffers() {
        return ApiResponse.success(hireService.expireOffers());
    }

    @PostMapping("/offers/followup")
    public ApiResponse<Map<String, Object>> offerFollowup() {
        return ApiResponse.success(hireService.offerFollowup());
    }

    @GetMapping("/entries")
    public Map<String, Object> listEntries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return hireService.listEntries(page, pageSize);
    }

    @PostMapping("/entry/create")
    public ApiResponse<Map<String, Object>> createEntry(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(hireService.createEntry(body));
    }

    @GetMapping("/entry/{id}")
    public ApiResponse<Map<String, Object>> getEntry(@PathVariable String id) {
        return ApiResponse.success(hireService.getEntry(id));
    }
}
