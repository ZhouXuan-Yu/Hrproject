package com.hr.bootstrap.controller;

import com.hr.common.dto.ApiResponse;
import com.hr.common.exception.BusinessException;
import com.hr.hire.entity.Offer;
import com.hr.hire.repository.OfferRepository;
import com.hr.hire.service.HireService;
import com.hr.interview.entity.InterviewBook;
import com.hr.interview.repository.InterviewBookRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 候选人确认端（H5 免登录），对齐 Flask api/confirm.py。
 * - GET  /confirm/{token}      候选人确认页数据
 * - POST /api/confirm/{token}  接受/拒绝面试或 Offer
 */
@RestController
@RequiredArgsConstructor
public class ConfirmController {

    private static final String TOKEN_PURPOSE = "candidate-confirm";

    private final InterviewBookRepository bookRepository;
    private final OfferRepository offerRepository;
    private final HireService hireService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @GetMapping("/confirm/{token}")
    public Map<String, Object> confirmPage(@PathVariable String token) {
        Claims payload = verifyToken(token);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", payload.get("kind"));
        data.put("ref", payload.get("ref"));
        data.put("expired", false);
        return data;
    }

    @PostMapping("/api/confirm/{token}")
    public ApiResponse<Map<String, Object>> confirmSubmit(@PathVariable String token,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        Claims payload = verifyToken(token);
        String action = body != null && body.get("action") != null
                ? String.valueOf(body.get("action")) : "";
        String reason = body != null && body.get("reason") != null
                ? String.valueOf(body.get("reason")) : "";
        if (!"accept".equals(action) && !"reject".equals(action)) {
            throw BusinessException.invalidInput("无效操作");
        }
        return ApiResponse.success(applyAction(payload, action, reason));
    }

    private Claims verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("TOKEN_INVALID", "确认链接无效", 400);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TOKEN_PURPOSE.equals(claims.get("purpose"))) {
                throw new BusinessException("TOKEN_INVALID", "确认链接无效", 400);
            }
            return claims;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new BusinessException("TOKEN_EXPIRED", "确认链接已过期，请联系 HR 重新发送", 400);
        } catch (JwtException e) {
            throw new BusinessException("TOKEN_INVALID", "确认链接无效", 400);
        }
    }

    private Map<String, Object> applyAction(Claims payload, String action, String reason) {
        String kind = String.valueOf(payload.get("kind"));
        long ref;
        try {
            ref = Long.parseLong(String.valueOf(payload.get("ref")));
        } catch (NumberFormatException e) {
            throw new BusinessException("TOKEN_INVALID", "确认链接无效", 400);
        }

        if ("interview".equals(kind)) {
            InterviewBook book = bookRepository.findById(ref)
                    .filter(b -> b.getIsDeleted() == null || b.getIsDeleted() == 0)
                    .orElseThrow(() -> BusinessException.notFound("面试预约不存在"));
            String inviteJson = book.getInviteJson();
            if (inviteJson != null && inviteJson.contains("\"candidate_confirm\"")) {
                return ok(true, "您已确认过本场面试，无需重复操作");
            }
            String updated = updateInviteJson(inviteJson, action, reason);
            book.setInviteJson(updated);
            book.setUpdatedAt(LocalDateTime.now());
            bookRepository.save(book);
            return ok(false, "accept".equals(action)
                    ? "已确认参加面试，请准时出席"
                    : "已反馈无法参加，HR 将与您重新协商时间");
        }

        if ("offer".equals(kind)) {
            Offer offer = offerRepository.findByOfferNoAndIsDeleted(String.valueOf(payload.get("ref")), 0)
                    .orElseThrow(() -> BusinessException.notFound("Offer 不存在"));
            if (offer.getOfferStatus() != null && offer.getOfferStatus() == 2) {
                return ok(true, "您已接受该 Offer");
            }
            if (offer.getOfferStatus() != null && offer.getOfferStatus() == 3) {
                return ok(true, "该 Offer 已标记为拒绝");
            }
            if ("accept".equals(action)) {
                hireService.acceptOffer(offer.getOfferNo());
                return ok(false, "已接受 Offer！入职材料清单已发送至您的邮箱，HR 将与您确认入职时间");
            }
            hireService.rejectOffer(offer.getOfferNo(),
                    reason.isEmpty() ? "候选人通过确认链接拒绝" : reason);
            return ok(false, "已反馈拒绝该 Offer，感谢您的参与");
        }

        throw new BusinessException("TOKEN_INVALID", "未知的确认类型", 400);
    }

    private String updateInviteJson(String inviteJson, String action, String reason) {
        try {
            Map<String, Object> invite = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(inviteJson == null ? "{}" : inviteJson,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            invite.put("candidate_confirm", action);
            invite.put("candidate_confirm_at", LocalDateTime.now().toString());
            if (reason != null && !reason.isBlank()) {
                invite.put("candidate_reject_reason", reason);
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(invite);
        } catch (Exception e) {
            return inviteJson;
        }
    }

    private Map<String, Object> ok(boolean already, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("already", already);
        m.put("message", message);
        return m;
    }
}
