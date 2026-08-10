package com.hr.bootstrap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.common.dto.ApiResponse;
import com.hr.common.exception.BusinessException;
import com.hr.hire.entity.Offer;
import com.hr.hire.repository.OfferRepository;
import com.hr.hire.service.HireService;
import com.hr.interview.entity.InterviewBook;
import com.hr.interview.repository.InterviewBookRepository;
import com.hr.talent.entity.Candidate;
import com.hr.talent.entity.Resume;
import com.hr.talent.repository.CandidateRepository;
import com.hr.talent.repository.ResumeRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选人确认端（H5 免登录），对齐 Flask api/confirm.py。
 * - GET  /confirm/{token}      候选人确认 H5 页面（HTML）
 * - POST /api/confirm/{token}  接受/拒绝面试或 Offer
 */
@RestController
@RequiredArgsConstructor
public class ConfirmController {

    private static final String TOKEN_PURPOSE = "candidate-confirm";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<Integer, String> INTERVIEW_TYPE_MAP = Map.of(
            1, "飞书视频", 2, "腾讯会议", 3, "线上视频", 4, "线下面试");

    private final InterviewBookRepository bookRepository;
    private final OfferRepository offerRepository;
    private final HireService hireService;
    private final CandidateRepository candidateRepository;
    private final ResumeRepository resumeRepository;
    private final EntityManager entityManager;

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * GET /confirm/{token} — 候选人确认 H5 页面（HTML）。
     * 对齐 Flask confirm_page() + _PAGE 模板。
     */
    @GetMapping(value = "/confirm/{token}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> confirmPage(@PathVariable String token) {
        Claims payload;
        try {
            payload = verifyToken(token);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(renderError(e.getMessage()));
        }

        try {
            String kind = String.valueOf(payload.get("kind"));
            Map<String, Object> data = loadPageData(kind, payload);
            return ResponseEntity.ok(renderPage(token, data));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(renderError(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(renderError("加载确认信息失败，请联系 HR"));
        }
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

    // ── 数据加载 ─────────────────────────────────────────────

    private Map<String, Object> loadPageData(String kind, Claims payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", kind);

        if ("interview".equals(kind)) {
            long bookId = Long.parseLong(String.valueOf(payload.get("ref")));
            InterviewBook book = bookRepository.findById(bookId)
                    .filter(b -> b.getIsDeleted() == null || b.getIsDeleted() == 0)
                    .orElseThrow(() -> BusinessException.notFound("面试预约不存在"));

            // 解析候选人信息
            Resume resume = resumeRepository.findById(book.getResumeId())
                    .filter(r -> r.getIsDeleted() == null || r.getIsDeleted() == 0)
                    .orElse(null);
            String candidateName = "";
            if (resume != null) {
                Candidate candidate = candidateRepository.findById(resume.getCandidateId())
                        .filter(c -> c.getIsDeleted() == null || c.getIsDeleted() == 0)
                        .orElse(null);
                if (candidate != null) {
                    candidateName = candidate.getCandidateName();
                }
            }

            data.put("title", "面试邀请确认");
            data.put("candidateName", candidateName);
            data.put("position", resolvePositionLabel(book.getDemandId()));
            data.put("time", book.getBookTime() != null ? book.getBookTime().format(DT_FMT) : "—");
            data.put("method", INTERVIEW_TYPE_MAP.getOrDefault(book.getInterviewType(), "待定"));
            data.put("meetingUrl", book.getMeetingUrl() != null ? book.getMeetingUrl() : "");
            data.put("address", book.getAddress() != null ? book.getAddress() : "");
            data.put("round", "第" + (book.getInterviewRound() != null ? book.getInterviewRound() : 1) + "轮面试");
            data.put("already", checkInterviewAlready(book));
            data.put("deadline", formatDeadline(payload));
            data.put("sub", candidateName + " · " + data.get("position"));

        } else if ("offer".equals(kind)) {
            String offerNo = String.valueOf(payload.get("ref"));
            Offer offer = offerRepository.findByOfferNoAndIsDeleted(offerNo, 0)
                    .orElseThrow(() -> BusinessException.notFound("Offer 不存在"));

            // 解析候选人信息
            Resume resume = resumeRepository.findById(offer.getResumeId())
                    .filter(r -> r.getIsDeleted() == null || r.getIsDeleted() == 0)
                    .orElse(null);
            String candidateName = "";
            if (resume != null) {
                Candidate candidate = candidateRepository.findById(resume.getCandidateId())
                        .filter(c -> c.getIsDeleted() == null || c.getIsDeleted() == 0)
                        .orElse(null);
                if (candidate != null) {
                    candidateName = candidate.getCandidateName();
                }
            }

            String salary = "详见 Offer 正文";
            if (offer.getSalaryJson() != null) {
                try {
                    Map<String, Object> sj = MAPPER.readValue(offer.getSalaryJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    if (sj.get("text") != null) {
                        salary = String.valueOf(sj.get("text"));
                    } else if (sj.get("base") != null) {
                        salary = String.valueOf(sj.get("base"));
                    }
                } catch (Exception ignored) {}
            }

            data.put("title", "录用 Offer 确认");
            data.put("candidateName", candidateName);
            data.put("position", resolvePositionLabel(offer.getDemandId()));
            data.put("salary", salary);
            data.put("offerContent", offer.getOfferContent() != null ? offer.getOfferContent() : "");
            data.put("deadline", offer.getValidDeadline() != null
                    ? offer.getValidDeadline().format(DEADLINE_FMT) : "—");
            data.put("already", checkOfferAlready(offer));
            data.put("sub", candidateName + " · " + data.get("position"));
        }

        return data;
    }

    private String resolvePositionLabel(Long demandId) {
        if (demandId == null) {
            return "—";
        }
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT demand_no, work_city FROM t_hr_recruit_demand WHERE id = ?1 AND is_deleted = 0")
                    .setParameter(1, demandId)
                    .getResultList();
            if (!rows.isEmpty()) {
                Object[] row = rows.get(0);
                String no = row[0] != null ? String.valueOf(row[0]) : "";
                String city = row[1] != null ? String.valueOf(row[1]) : "";
                return "岗位 " + no + (city.isEmpty() ? "" : "（" + city + "）");
            }
        } catch (Exception e) {
            // fallthrough
        }
        return "—";
    }

    private String checkInterviewAlready(InterviewBook book) {
        if (book.getInviteJson() != null && book.getInviteJson().contains("\"candidate_confirm\"")) {
            try {
                Map<String, Object> invite = MAPPER.readValue(book.getInviteJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                Object confirm = invite.get("candidate_confirm");
                return confirm != null ? String.valueOf(confirm) : "";
            } catch (Exception ignored) {}
        }
        return "";
    }

    private String checkOfferAlready(Offer offer) {
        if (offer.getOfferStatus() != null) {
            if (offer.getOfferStatus() == 2) return "accepted";
            if (offer.getOfferStatus() == 3) return "rejected";
        }
        return "";
    }

    private String formatDeadline(Claims payload) {
        Object exp = payload.get("exp");
        if (exp instanceof Number) {
            long expSec = ((Number) exp).longValue();
            return LocalDateTime.ofEpochSecond(expSec, 0, java.time.ZoneOffset.ofHours(8))
                    .format(DEADLINE_FMT);
        }
        return "—";
    }

    // ── HTML 渲染 ─────────────────────────────────────────────

    private String renderPage(String token, Map<String, Object> data) {
        String title = str(data.get("title"));
        String sub = str(data.get("sub"));
        String deadline = str(data.get("deadline"));
        String already = str(data.get("already"));
        String kind = str(data.get("kind"));

        StringBuilder rows = new StringBuilder();
        if ("interview".equals(kind)) {
            addRow(rows, "候选人", str(data.get("candidateName")));
            addRow(rows, "面试岗位", str(data.get("position")));
            addRow(rows, "面试轮次", str(data.get("round")));
            addRow(rows, "面试时间", str(data.get("time")));
            addRow(rows, "面试方式", str(data.get("method")));
            String url = str(data.get("meetingUrl"));
            if (!url.isEmpty()) {
                rows.append("<div class=\"row\"><span class=\"k\">会议链接</span>")
                    .append("<span class=\"v\"><a href=\"").append(html(url)).append("\" target=\"_blank\">")
                    .append(html(url)).append("</a></span></div>");
            }
            String addr = str(data.get("address"));
            if (!addr.isEmpty()) {
                addRow(rows, "面试地点", addr);
            }
        } else {
            addRow(rows, "候选人", str(data.get("candidateName")));
            addRow(rows, "录用岗位", str(data.get("position")));
            addRow(rows, "薪资方案", str(data.get("salary")));
            String content = str(data.get("offerContent"));
            if (!content.isEmpty()) {
                rows.append("<div class=\"offer-body\">").append(html(content)).append("</div>");
            }
        }

        String alreadyBlock = "";
        if (!already.isEmpty()) {
            String label = "interview".equals(kind)
                    ? ("accept".equals(already) ? "您已确认参加本场面试" : "您已反馈无法参加本场面试")
                    : ("accepted".equals(already) ? "您已接受该 Offer" : "该 Offer 已标记为拒绝");
            alreadyBlock = "<div class=\"already\">" + label + "</div>";
        }

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>""" + html(title) + """
                </title>
                <style>
                  * { box-sizing: border-box; margin: 0; padding: 0; }
                  body { font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
                         background: #f2f4f9; color: #222; padding: 16px; }
                  .card { max-width: 480px; margin: 24px auto; background: #fff; border-radius: 14px;
                          padding: 28px 22px; box-shadow: 0 4px 24px rgba(20,30,60,.08); }
                  h1 { font-size: 20px; color: #4F6EF7; margin-bottom: 6px; }
                  .sub { color: #888; font-size: 13px; margin-bottom: 18px; }
                  .row { display: flex; padding: 10px 0; border-bottom: 1px solid #f0f2f7; font-size: 15px; }
                  .row .k { width: 88px; color: #999; flex-shrink: 0; }
                  .row .v { flex: 1; font-weight: 600; word-break: break-all; }
                  .offer-body { background: #f6f8ff; border-radius: 10px; padding: 14px;
                                font-size: 14px; line-height: 1.9; margin: 12px 0; white-space: pre-wrap; }
                  .deadline { background: #fff8e6; color: #b45309; border-radius: 8px;
                              padding: 10px 12px; font-size: 13px; margin: 16px 0; }
                  .btns { display: flex; gap: 12px; margin-top: 20px; }
                  button { flex: 1; padding: 14px; border: none; border-radius: 10px;
                           font-size: 16px; font-weight: 700; cursor: pointer; }
                  .accept { background: #4F6EF7; color: #fff; }
                  .reject { background: #f2f4f9; color: #666; }
                  button:disabled { opacity: .55; }
                  .result { text-align: center; padding: 30px 0 10px; font-size: 16px; font-weight: 600; }
                  .reason { width: 100%; margin-top: 14px; padding: 10px; border: 1px solid #e1e6ef;
                            border-radius: 8px; font-size: 14px; display: none; font-family: inherit; }
                  .already { text-align:center; color:#22a06b; font-weight:700; padding: 18px 0 4px; }
                </style>
                </head>
                <body>
                <div class="card">
                  <h1>""" + html(title) + """
                  </h1>
                  <div class="sub">""" + html(sub) + """
                  </div>
                """ + rows + """
                """ + alreadyBlock + """
                  <div class="deadline">⏰ 请在 """ + html(deadline) + """ 前完成确认</div>
                """ + (already.isEmpty() ? """
                  <div id="actionArea">
                    <textarea id="reason" class="reason" rows="2" placeholder="可填写原因（选填）"></textarea>
                    <div class="btns">
                      <button class="accept" onclick="submit('accept')">✔ 接受</button>
                      <button class="reject" onclick="toggleReject()">✘ 拒绝</button>
                    </div>
                  </div>
                """ : "") + """
                  <div class="result" id="resultArea" style="display:none"></div>
                </div>
                <script>
                var TOKEN = \"""" + token + """\";
                var rejecting = false;
                function toggleReject() {
                  var r = document.getElementById('reason');
                  if (!rejecting) {
                    rejecting = true;
                    r.style.display = 'block';
                    event.target.textContent = '确认拒绝';
                    return;
                  }
                  submit('reject');
                }
                function submit(action) {
                  var btns = document.querySelectorAll('button');
                  btns.forEach(function(b){ b.disabled = true; });
                  fetch('/api/confirm/' + TOKEN, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({action: action, reason: document.getElementById('reason').value})
                  }).then(function(r){ return r.json(); }).then(function(resp) {
                    var d = resp.data || {};
                    showResult(d.message || (action === 'accept' ? '已确认' : '已反馈'));
                  }).catch(function() {
                    showResult('网络异常，请稍后重试或联系 HR');
                    btns.forEach(function(b){ b.disabled = false; });
                  });
                }
                function showResult(text) {
                  document.getElementById('actionArea').style.display = 'none';
                  var el = document.getElementById('resultArea');
                  el.style.display = 'block';
                  el.textContent = text;
                }
                </script>
                </body>
                </html>""";
    }

    private String renderError(String message) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1"><title>提示</title>
                <style>body{font-family:-apple-system,"PingFang SC",sans-serif;background:#f2f4f9;padding:16px}
                .card{max-width:480px;margin:60px auto;background:#fff;border-radius:14px;padding:40px 24px;
                text-align:center;box-shadow:0 4px 24px rgba(20,30,60,.08)}
                h2{color:#ef4444;margin-bottom:12px}.msg{color:#666;font-size:15px}</style>
                </head><body><div class="card"><h2>⚠ 提示</h2>
                <p class="msg">""" + html(message) + """
                </p></div></body></html>""";
    }

    private void addRow(StringBuilder sb, String key, String value) {
        sb.append("<div class=\"row\"><span class=\"k\">").append(html(key))
          .append("</span><span class=\"v\">").append(html(value)).append("</span></div>");
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o) : "";
    }

    private static String html(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ── Token 验证 ───────────────────────────────────────────

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
        } catch (ExpiredJwtException e) {
            throw new BusinessException("TOKEN_EXPIRED", "确认链接已过期，请联系 HR 重新发送", 400);
        } catch (JwtException e) {
            throw new BusinessException("TOKEN_INVALID", "确认链接无效", 400);
        }
    }

    // ── 确认操作 ─────────────────────────────────────────────

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
            Map<String, Object> invite = MAPPER.readValue(inviteJson == null ? "{}" : inviteJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            invite.put("candidate_confirm", action);
            invite.put("candidate_confirm_at", LocalDateTime.now().toString());
            if (reason != null && !reason.isBlank()) {
                invite.put("candidate_reject_reason", reason);
            }
            return MAPPER.writeValueAsString(invite);
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
