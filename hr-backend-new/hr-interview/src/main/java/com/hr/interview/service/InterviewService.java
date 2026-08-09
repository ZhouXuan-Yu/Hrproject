package com.hr.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.LockUtil;
import com.hr.common.util.Sha256Util;
import com.hr.demand.entity.RecruitDemand;
import com.hr.demand.repository.RecruitDemandRepository;
import com.hr.interview.entity.InterviewBook;
import com.hr.interview.entity.InterviewRecord;
import com.hr.interview.repository.InterviewBookRepository;
import com.hr.interview.repository.InterviewRecordRepository;
import com.hr.interview.repository.InterviewSlotRepository;
import com.hr.talent.entity.Candidate;
import com.hr.talent.entity.Resume;
import com.hr.talent.repository.CandidateRepository;
import com.hr.talent.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 面试管理服务，对齐 Flask interview_service.py 核心逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final Map<Integer, String> METHOD_LABELS = Map.of(
            1, "飞书视频", 2, "腾讯会议", 3, "其他线上", 4, "线下");
    private static final Map<String, Integer> METHOD_CODES = Map.of(
            "飞书视频", 1, "腾讯会议", 2, "其他线上", 3, "线下", 4, "电话", 1, "现场", 4);
    private static final DateTimeFormatter FULL_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InterviewBookRepository bookRepository;
    private final InterviewRecordRepository recordRepository;
    private final InterviewSlotRepository slotRepository;
    private final CandidateRepository candidateRepository;
    private final ResumeRepository resumeRepository;
    private final RecruitDemandRepository demandRepository;
    private final LockUtil lockUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    /**
     * 面试列表（分页 + 状态筛选）。
     * 状态为派生状态：pending/scheduled/evaluating/offer/done。
     */
    public Map<String, Object> listInterviews(int page, int pageSize, String status) {
        Specification<InterviewBook> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), 0));
            if (status != null && !status.isBlank() && !"all".equals(status)) {
                predicates.add(buildStatusPredicate(root, query, cb, status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<InterviewBook> result = bookRepository.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), pageSize, Sort.by(Sort.Direction.DESC, "bookTime")));

        List<Map<String, Object>> list = result.getContent().stream().map(this::toBookMap).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", list);
        m.put("total", result.getTotalElements());
        m.put("page", page);
        m.put("pageSize", pageSize);
        return m;
    }

    /**
     * 创建面试预约（含去重锁，防止重复提交）。
     */
    @Transactional
    public Map<String, Object> createInterview(Map<String, Object> body, Long userId) {
        String digest = Sha256Util.shortHash(String.valueOf(body), 24);
        Map<String, Object> result = lockUtil.withLock("interview:create:" + digest, 5, () -> doCreate(body, userId));
        if (result == null) {
            throw new BusinessException("DUPLICATE_REQUEST", "面试安排正在处理中，请勿重复提交", 409);
        }
        return result;
    }

    /**
     * 提交面试评价。
     */
    @Transactional
    public Map<String, Object> evaluateInterview(Long bookId, Map<String, Object> body, Long userId) {
        InterviewBook book = getActiveBook(bookId);
        InterviewRecord record = recordRepository.findFirstByBookIdAndIsDeleted(book.getId(), 0)
                .orElseThrow(() -> BusinessException.invalidInput("面试预约尚未完成面试"));

        if (record.getInterviewResult() != null && record.getInterviewResult() != 0) {
            throw BusinessException.invalidInput("面试预约已评价，请勿重复提交");
        }

        String result = body != null && body.get("result") != null
                ? String.valueOf(body.get("result")) : "hold";
        if (!List.of("pass", "fail", "hold").contains(result)) {
            throw BusinessException.invalidInput("评价结果只能是 pass/fail/hold");
        }

        String comment = body != null && body.get("comment") != null
                ? String.valueOf(body.get("comment")).trim() : "";
        if (comment.length() < 5) {
            throw BusinessException.invalidInput("必须填写评价理由（不少于 5 个字），不能仅提交通过/拒绝");
        }

        int score = body != null && body.get("score") != null
                ? ((Number) body.get("score")).intValue() : 75;
        if (score < 0 || score > 100) {
            score = 75;
        }

        record.setInterviewResult(Map.of("pass", 1, "fail", 2, "hold", 3).get(result));
        record.setEvaluateText(comment);
        record.setSubmitInterviewerId(userId != null ? userId : record.getSubmitInterviewerId());
        record.setScoreJson(buildScoreJson(body != null ? body.get("scoreJson") : null, score));
        record.setEndTime(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        recordRepository.save(record);

        String newStatus;
        String newLabel;
        if ("pass".equals(result)) {
            newStatus = "offer";
            newLabel = "待录用";
        } else if ("fail".equals(result)) {
            newStatus = "done";
            newLabel = "已淘汰";
        } else {
            newStatus = "pending";
            newLabel = "待安排";
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evaluated", true);
        m.put("newStatus", newStatus);
        m.put("newStatusLabel", newLabel);
        m.put("recordId", record.getId());
        return m;
    }

    /**
     * 取消面试（软删除）。
     */
    @Transactional
    public Map<String, Object> cancelInterview(Long bookId, String reason) {
        InterviewBook book = getActiveBook(bookId);

        recordRepository.findFirstByBookIdAndIsDeleted(book.getId(), 0).ifPresent(record -> {
            if (record.getInterviewResult() != null && record.getInterviewResult() == 1) {
                throw BusinessException.invalidInput("面试已通过，无法取消");
            }
            if (record.getEvaluateText() == null || record.getEvaluateText().isBlank()) {
                throw BusinessException.invalidInput("请先完成面试评价，才能取消");
            }
        });

        book.setIsDeleted(1);
        book.setUpdatedAt(LocalDateTime.now());
        bookRepository.save(book);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cancelled", true);
        m.put("id", book.getId());
        return m;
    }

    // ── 私有方法 ──────────────────────────────────────────────

    private Map<String, Object> doCreate(Map<String, Object> body, Long userId) {
        LocalDateTime bookTime = parseBookTime(body);
        int interviewRound = parseRound(body);
        int interviewType = parseInterviewType(body);
        String meetingCode = body.get("meetingCode") != null ? String.valueOf(body.get("meetingCode")) : "";
        String meetingPwd = body.get("meetingPwd") != null ? String.valueOf(body.get("meetingPwd")) : "";
        String meetingUrl = "";
        if (interviewType == 3) {
            meetingUrl = body.get("meetingUrl") != null ? String.valueOf(body.get("meetingUrl")).trim() : "";
        } else if (interviewType == 1 || interviewType == 2) {
            meetingUrl = body.get("meetingUrl") != null ? String.valueOf(body.get("meetingUrl")).trim() : "";
            if (meetingPwd.isEmpty()) {
                meetingPwd = randomDigits(random.nextInt(3) + 4);
            }
        }

        long[] links = resolveLinks(body);
        long resumeId = links[0];
        long demandId = links[1];

        InterviewBook book = new InterviewBook();
        book.setDemandId(demandId);
        book.setResumeId(resumeId);
        book.setProcessId(0L);
        book.setSlotId(0L);
        book.setInterviewRound(interviewRound);
        book.setInterviewType(interviewType);
        book.setMeetingCode(meetingCode);
        book.setMeetingPwd(meetingPwd);
        book.setMeetingUrl(meetingUrl);
        book.setAddress(body.get("address") != null ? String.valueOf(body.get("address")) : "");
        book.setBookTime(bookTime);
        book.setInviteJson(buildInviteJson(body, meetingUrl));
        book.setCreatedAt(LocalDateTime.now());
        book.setCreatedBy(userId);
        book.setUpdatedAt(LocalDateTime.now());
        book.setIsDeleted(0);
        bookRepository.save(book);

        log.info("面试预约已创建: id={}, demand={}, time={}", book.getId(), demandId, bookTime);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("created", true);
        m.put("id", "INT" + String.format("%04d", book.getId()));
        m.put("bookId", book.getId());
        m.put("meetingUrl", meetingUrl);
        return m;
    }

    private LocalDateTime parseBookTime(Map<String, Object> body) {
        String date = body.get("date") != null ? String.valueOf(body.get("date")) : "";
        String time = body.get("time") != null ? String.valueOf(body.get("time")) : "";
        if (!date.isBlank() && !time.isBlank()) {
            try {
                return LocalDateTime.parse(date + " " + time, FULL_DT);
            } catch (Exception ignored) {
                // fall through
            }
            try {
                int year = LocalDateTime.now().getYear();
                return LocalDateTime.parse(year + "-" + date + " " + time, FULL_DT);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return LocalDateTime.now();
    }

    private int parseRound(Map<String, Object> body) {
        String round = body.get("round") != null ? String.valueOf(body.get("round")) : "初试(1轮)";
        return (round.contains("复试") || round.contains("终面")) ? 2 : 1;
    }

    private int parseInterviewType(Map<String, Object> body) {
        String method = body.get("method") != null ? String.valueOf(body.get("method")) : "";
        if (method.isBlank() && body.get("mode") != null) {
            method = String.valueOf(body.get("mode"));
        }
        Integer code = METHOD_CODES.get(method);
        if (code != null) {
            return code;
        }
        if (body.get("modeId") != null) {
            try {
                int modeId = ((Number) body.get("modeId")).intValue();
                if (modeId >= 1 && modeId <= 4) {
                    return modeId;
                }
            } catch (Exception ignored) {
            }
        }
        return 1;
    }

    private long[] resolveLinks(Map<String, Object> body) {
        long resumeId = 0;
        long demandId = 0;
        try {
            if (body.get("resumeId") instanceof Number n && n.longValue() > 0) {
                resumeId = n.longValue();
            } else if (body.get("candidateId") instanceof Number n2 && n2.longValue() > 0) {
                resumeId = latestResumeId(n2.longValue());
            } else {
                String candidateNo = body.get("candidateNo") != null
                        ? String.valueOf(body.get("candidateNo")).trim() : "";
                if (candidateNo.isBlank() && body.get("candidate") != null) {
                    candidateNo = String.valueOf(body.get("candidate")).trim();
                }
                if (!candidateNo.isBlank()) {
                    Candidate c = candidateRepository.findAll(
                                    (root, query, cb) -> cb.and(
                                            cb.equal(root.get("candidateNo"), candidateNo),
                                            cb.equal(root.get("isDeleted"), 0)))
                            .stream().findFirst().orElse(null);
                    if (c != null) {
                        resumeId = latestResumeId(c.getId());
                    }
                }
            }
            if (body.get("demandId") instanceof Number n3 && n3.longValue() > 0) {
                demandId = n3.longValue();
            } else {
                String demandNo = body.get("demandNo") != null
                        ? String.valueOf(body.get("demandNo")).trim() : "";
                if (!demandNo.isBlank()) {
                    RecruitDemand d = demandRepository.findAll(
                                    (root, query, cb) -> cb.and(
                                            cb.equal(root.get("demandNo"), demandNo),
                                            cb.equal(root.get("isDeleted"), 0)))
                            .stream().findFirst().orElse(null);
                    if (d != null) {
                        demandId = d.getId();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("resolve links failed: {}", e.getMessage());
        }
        return new long[]{resumeId, demandId};
    }

    private long latestResumeId(Long candidateId) {
        return resumeRepository.findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(candidateId, 0)
                .stream().findFirst().map(Resume::getId).orElse(0L);
    }

    private String buildInviteJson(Map<String, Object> body, String meetingUrl) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("meeting_url", meetingUrl);
        snapshot.put("interviewer", body.get("interviewer") != null ? body.get("interviewer") : "");
        snapshot.put("interviewer_id", body.get("interviewerId") != null ? body.get("interviewerId") : "");
        snapshot.put("position", body.get("position") != null ? body.get("position") : "");
        snapshot.put("round", body.get("round") != null ? body.get("round") : "");
        snapshot.put("notified", false);
        snapshot.put("channel", "feishu");
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildScoreJson(Object rawScoreJson, int total) {
        Map<String, Object> score = new LinkedHashMap<>();
        if (rawScoreJson instanceof Map<?, ?> map) {
            map.forEach((k, v) -> score.put(String.valueOf(k), v));
        }
        score.putIfAbsent("total", total);
        try {
            return objectMapper.writeValueAsString(score);
        } catch (Exception e) {
            return "{\"total\":" + total + "}";
        }
    }

    private Predicate buildStatusPredicate(Root<InterviewBook> root,
                                           jakarta.persistence.criteria.CriteriaQuery<?> query,
                                           jakarta.persistence.criteria.CriteriaBuilder cb,
                                           String status) {
        Subquery<Long> activeRecords = query.subquery(Long.class);
        Root<InterviewRecord> recRoot = activeRecords.from(InterviewRecord.class);
        activeRecords.select(recRoot.get("bookId"));
        activeRecords.where(cb.equal(recRoot.get("isDeleted"), 0));
        Predicate noRecord = cb.not(cb.in(root.get("id")).value(activeRecords));

        switch (status) {
            case "pending":
                return noRecord;
            case "scheduled":
                return cb.and(noRecord, cb.greaterThan(root.get("bookTime"), LocalDateTime.now()));
            case "evaluating":
                return buildResultSubquery(root, query, cb, List.of(0, 3));
            case "offer":
                return buildResultSubquery(root, query, cb, List.of(1));
            case "done":
                return buildResultSubquery(root, query, cb, List.of(2));
            default:
                return cb.conjunction();
        }
    }

    private Predicate buildResultSubquery(Root<InterviewBook> root,
                                          jakarta.persistence.criteria.CriteriaQuery<?> query,
                                          jakarta.persistence.criteria.CriteriaBuilder cb,
                                          List<Integer> results) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<InterviewRecord> r = sub.from(InterviewRecord.class);
        sub.select(r.get("bookId"));
        sub.where(cb.and(cb.equal(r.get("isDeleted"), 0), r.get("interviewResult").in(results)));
        return cb.in(root.get("id")).value(sub);
    }

    private Map<String, Object> toBookMap(InterviewBook book) {
        String[] candidate = resolveCandidate(book.getResumeId());
        String candidateName = candidate[0];
        String candidateNo = candidate[1];
        String demandPos = resolveDemandPosition(book.getDemandId());

        String[] display = deriveDisplayStatus(book);
        JsonNode invite = parseJson(book.getInviteJson());
        String interviewer = "待分配";
        if (invite != null) {
            interviewer = invite.has("interviewer") && !invite.get("interviewer").asText().isBlank()
                    ? invite.get("interviewer").asText()
                    : (invite.has("interviewer_name") ? invite.get("interviewer_name").asText() : "待分配");
        }
        int roundNum = book.getInterviewRound() != null ? book.getInterviewRound() : 1;
        int type = book.getInterviewType() != null ? book.getInterviewType() : 1;
        String result = null;
        if (recordRepository.findFirstByBookIdAndIsDeleted(book.getId(), 0).isPresent()) {
            int code = recordRepository.findFirstByBookIdAndIsDeleted(book.getId(), 0)
                    .map(InterviewRecord::getInterviewResult).orElse(0);
            result = Map.of(1, "pass", 2, "reject", 3, "hold").get(code);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "INT" + String.format("%04d", book.getId()));
        m.put("name", candidateName);
        m.put("candidateId", candidateNo);
        m.put("resumeId", book.getResumeId());
        m.put("demandId", book.getDemandId());
        m.put("position", demandPos);
        m.put("round", (roundNum == 1 ? "初试" : "复试") + "(" + roundNum + "轮)");
        m.put("interviewer", interviewer);
        m.put("date", book.getBookTime() != null ? book.getBookTime().format(DateTimeFormatter.ofPattern("MM-dd")) : "待定");
        m.put("time", book.getBookTime() != null ? book.getBookTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "待定");
        m.put("method", METHOD_LABELS.getOrDefault(type, "待定"));
        m.put("meetingUrl", book.getMeetingUrl() != null ? book.getMeetingUrl() : "");
        m.put("meetingCode", book.getMeetingCode() != null ? book.getMeetingCode() : "");
        m.put("meetingPwd", book.getMeetingPwd() != null ? book.getMeetingPwd() : "");
        m.put("status", display[0]);
        m.put("statusLabel", display[1]);
        m.put("emailSent", invite != null && invite.has("email_sent") && invite.get("email_sent").asBoolean());
        m.put("createdBy", "系统");
        m.put("isMine", false);
        m.put("result", result);
        return m;
    }

    private String[] deriveDisplayStatus(InterviewBook book) {
        InterviewRecord record = recordRepository.findFirstByBookIdAndIsDeleted(book.getId(), 0).orElse(null);
        if (record != null) {
            int code = record.getInterviewResult() != null ? record.getInterviewResult() : 0;
            if (code == 1) return new String[]{"offer", "待录用"};
            if (code == 2) return new String[]{"done", "已淘汰"};
            return new String[]{"evaluating", "待评价"};
        }
        if (book.getBookTime() != null && book.getBookTime().isAfter(LocalDateTime.now())) {
            return new String[]{"scheduled", "待面试"};
        }
        return new String[]{"pending", "待安排"};
    }

    private String[] resolveCandidate(Long resumeId) {
        if (resumeId == null || resumeId <= 0) {
            return new String[]{"候选人#" + resumeId, ""};
        }
        Resume resume = resumeRepository.findById(resumeId).filter(r -> r.getIsDeleted() == null || r.getIsDeleted() == 0)
                .orElse(null);
        if (resume == null) {
            return new String[]{"候选人#" + resumeId, ""};
        }
        Candidate c = candidateRepository.findById(resume.getCandidateId())
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElse(null);
        if (c != null) {
            return new String[]{c.getCandidateName(), c.getCandidateNo() != null ? c.getCandidateNo() : ""};
        }
        return new String[]{"候选人#" + resumeId, ""};
    }

    private String resolveDemandPosition(Long demandId) {
        if (demandId == null || demandId <= 0) {
            return "岗位#" + demandId;
        }
        return demandRepository.findById(demandId)
                .filter(d -> d.getIsDeleted() == null || d.getIsDeleted() == 0)
                .map(RecruitDemand::getPositionName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("岗位#" + demandId);
    }

    private InterviewBook getActiveBook(Long id) {
        return bookRepository.findById(id)
                .filter(b -> b.getIsDeleted() == null || b.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("面试预约不存在"));
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String randomDigits(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
