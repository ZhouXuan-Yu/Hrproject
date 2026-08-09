package com.hr.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.LockUtil;
import com.hr.common.util.Sha256Util;
import com.hr.demand.entity.RecruitDemand;
import com.hr.demand.repository.RecruitDemandRepository;
import com.hr.hire.entity.Offer;
import com.hr.hire.entity.HireEvent;
import com.hr.hire.repository.OfferRepository;
import com.hr.hire.repository.HireEventRepository;
import com.hr.hire.service.HireService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
    private final OfferRepository offerRepository;
    private final HireEventRepository hireEventRepository;
    private final HireService hireService;
    private final LockUtil lockUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    /**
     * 面试列表（分页 + 状态筛选）。
     * 状态为派生状态：pending/scheduled/evaluating/offer/done。
     */
    @org.springframework.cache.annotation.Cacheable(cacheNames = "list", key = "'interview:' + #page + ':' + #pageSize + ':' + (#status != null ? #status : '')")
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

        List<Map<String, Object>> list = toBookMaps(result.getContent());
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
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "list", allEntries = true)
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
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "list", allEntries = true)
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
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "list", allEntries = true)
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

    /**
     * GET /api/interview/alerts — 面试预警（超期未评价 / 今日面试 / 待发 Offer）。
     */
    @org.springframework.cache.annotation.Cacheable(cacheNames = "list", key = "'alerts'")
    public List<Map<String, Object>> getAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 批量预取：全部有效 book + 全部 record，一次查询替代循环内 N+1
        List<InterviewBook> allBooks = bookRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("isDeleted"), 0));
        Map<Long, InterviewRecord> recordByBook = new HashMap<>();
        for (InterviewRecord r : recordRepository.findByBookIdInAndIsDeleted(
                allBooks.stream().map(InterviewBook::getId).toList(), 0)) {
            recordByBook.putIfAbsent(r.getBookId(), r);
        }
        Map<Long, Resume> resumeMap = new HashMap<>();
        for (Resume r : resumeRepository.findAllById(allBooks.stream().map(InterviewBook::getResumeId)
                .filter(id -> id != null && id > 0).distinct().toList())) {
            if (r.getIsDeleted() == null || r.getIsDeleted() == 0) {
                resumeMap.put(r.getId(), r);
            }
        }
        Map<Long, Candidate> candidateMap = new HashMap<>();
        for (Candidate c : candidateRepository.findAllById(
                resumeMap.values().stream().map(Resume::getCandidateId).filter(java.util.Objects::nonNull).toList())) {
            if (c.getIsDeleted() == null || c.getIsDeleted() == 0) {
                candidateMap.put(c.getId(), c);
            }
        }

        // 1) 超 2 天未评价
        for (InterviewBook book : allBooks) {
            if (book.getBookTime() == null || !book.getBookTime().isBefore(now)) {
                continue;
            }
            long daysAgo = java.time.Duration.between(book.getBookTime(), now).toDays();
            boolean evaluated = recordByBook.containsKey(book.getId());
            if (!evaluated && daysAgo > 2) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("text", resolveCandidate(book.getResumeId(), resumeMap, candidateMap)[0]
                        + " · 面试超" + daysAgo + "天未评价");
                a.put("type", "reject");
                a.put("action", "去评价");
                a.put("actionMsg", "填写面试评价");
                alerts.add(a);
            }
        }

        // 2) 今日面试
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1).minusSeconds(1);
        for (InterviewBook book : allBooks) {
            if (book.getBookTime() == null || book.getBookTime().isBefore(todayStart)
                    || book.getBookTime().isAfter(todayEnd)) {
                continue;
            }
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("text", resolveCandidate(book.getResumeId(), resumeMap, candidateMap)[0] + " · "
                    + book.getBookTime().format(DateTimeFormatter.ofPattern("HH:mm")) + " 面试");
            a.put("type", "warn");
            a.put("action", "查看");
            a.put("actionMsg", "");
            alerts.add(a);
        }

        // 3) 已通过但未发 Offer
        if (alerts.size() < 8) {
            List<InterviewRecord> passedRecords = recordRepository.findAll(
                    (root, query, cb) -> cb.and(
                            cb.equal(root.get("isDeleted"), 0),
                            cb.equal(root.get("interviewResult"), 1)));
            if (!passedRecords.isEmpty()) {
                Set<Long> offeredInterviewIds = new HashSet<>();
                for (Offer o : offerRepository.findByLastInterviewIdInAndIsDeleted(
                        passedRecords.stream().map(InterviewRecord::getId).toList(), 0)) {
                    offeredInterviewIds.add(o.getLastInterviewId());
                }
                for (InterviewRecord rec : passedRecords) {
                    if (offeredInterviewIds.contains(rec.getId())) {
                        continue;
                    }
                    InterviewBook book = allBooks.stream()
                            .filter(b -> b.getId().equals(rec.getBookId())).findFirst().orElse(null);
                    if (book == null) {
                        continue;
                    }
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("text", resolveCandidate(book.getResumeId(), resumeMap, candidateMap)[0] + " · 待发放Offer");
                    a.put("type", "offer");
                    a.put("action", "去发放");
                    a.put("actionMsg", "填写 Offer 信息");
                    alerts.add(a);
                }
            }
        }

        if (alerts.size() > 10) {
            return alerts.subList(0, 10);
        }
        return alerts;
    }

    /**
     * POST /api/interview/schedule — 批量安排面试（支持单条或数组）。
     */
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "list", allEntries = true)
    public Map<String, Object> scheduleInterviews(Map<String, Object> body) {
        Object itemsObj = body != null ? body.get("items") : null;
        List<Map<String, Object>> items = new ArrayList<>();
        if (itemsObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    m.forEach((k, v) -> item.put(String.valueOf(k), v));
                    items.add(item);
                }
            }
        } else if (body != null && !body.isEmpty()) {
            items.add(body);
        }
        if (items.isEmpty()) {
            throw BusinessException.invalidInput("缺少面试安排数据");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> item : items) {
            results.add(doCreate(item, null));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", true);
        out.put("count", results.size());
        out.put("results", results);
        return out;
    }

    /**
     * GET /api/interview/{id} — 单个面试详情。
     */
    public Map<String, Object> getInterviewDetail(Long bookId) {
        InterviewBook book = getActiveBook(bookId);
        return toBookMap(book);
    }

    /**
     * POST /api/interview/{id}/complete — 标记面试完成（scheduled -> evaluating）。
     */
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "list", allEntries = true)
    public Map<String, Object> completeInterview(Long bookId, Map<String, Object> body) {
        InterviewBook book = getActiveBook(bookId);
        if (recordRepository.findFirstByBookIdAndIsDeleted(book.getId(), 0).isPresent()) {
            throw new BusinessException("ALREADY_COMPLETED", "面试预约 " + bookId + " 已完成，请勿重复操作", 400);
        }

        InterviewRecord record = new InterviewRecord();
        record.setBookId(book.getId());
        record.setProcessId(book.getProcessId() != null ? book.getProcessId() : 0L);
        record.setInterviewerIds(body != null && body.get("interviewerIds") != null
                ? String.valueOf(body.get("interviewerIds")) : "[]");
        record.setSubmitInterviewerId(body != null && body.get("interviewerId") != null
                ? ((Number) body.get("interviewerId")).longValue() : 0L);
        record.setIsArrive(body != null && body.get("isArrive") != null
                ? ((Number) body.get("isArrive")).intValue() : 1);
        record.setInterviewResult(0);
        record.setEndTime(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setIsDeleted(0);
        recordRepository.save(record);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("completed", true);
        out.put("recordId", record.getId());
        return out;
    }

    /**
     * POST /api/interview/{id}/offer — 面试通过后发放 Offer。
     */
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "list", allEntries = true)
    public Map<String, Object> sendOffer(Long bookId, Map<String, Object> body) {
        InterviewBook book = getActiveBook(bookId);
        InterviewRecord record = recordRepository.findFirstByBookIdAndIsDeleted(book.getId(), 0)
                .orElseThrow(() -> BusinessException.invalidInput("面试预约尚未完成面试"));
        if (record.getInterviewResult() == null || record.getInterviewResult() != 1) {
            throw new BusinessException("INVALID_STATE", "面试预约 " + bookId + " 尚未通过评价", 400);
        }

        Map<String, Object> offerData = new LinkedHashMap<>();
        offerData.put("resumeId", book.getResumeId());
        offerData.put("processId", book.getProcessId());
        offerData.put("demandId", book.getDemandId());
        offerData.put("lastInterviewId", record.getId());
        offerData.put("offerContent", body != null ? body.get("offer_content") : null);
        offerData.put("salaryJson", body != null ? body.get("salary_json") : null);
        offerData.put("validDeadline", body != null ? body.get("valid_deadline") : null);
        offerData.put("sendUserId", body != null ? body.get("send_user_id") : null);

        String offerNo = null;
        try {
            Map<String, Object> created = hireService.createOffer(offerData);
            offerNo = String.valueOf(created.get("id"));
            hireService.sendOffer(offerNo);
        } catch (BusinessException e) {
            if (!"INVALID_INPUT".equals(e.getCode()) || !String.valueOf(e.getMessage()).contains("进行中")) {
                throw e;
            }
            // 幂等复用：草稿 Offer 更新后重发
            Offer existing = offerRepository.findAll(
                            (root, query, cb) -> cb.and(
                                    cb.equal(root.get("resumeId"), book.getResumeId()),
                                    cb.equal(root.get("demandId"), book.getDemandId()),
                                    cb.equal(root.get("isDeleted"), 0)))
                    .stream().findFirst().orElseThrow(() -> e);
            if (existing.getOfferStatus() != null && existing.getOfferStatus() != 0) {
                throw new BusinessException("DUPLICATE_OFFER", "该候选人已存在进行中的 Offer，请勿重复发送", 400);
            }
            if (offerData.get("offerContent") != null) {
                existing.setOfferContent(String.valueOf(offerData.get("offerContent")));
                existing.setUpdatedAt(LocalDateTime.now());
                offerRepository.save(existing);
            }
            offerNo = existing.getOfferNo();
            hireService.sendOffer(offerNo);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sent", true);
        out.put("offerNo", offerNo);
        return out;
    }

    /**
     * POST /api/interview/{id}/onboard — 确认候选人入职。
     */
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "list", allEntries = true)
    public Map<String, Object> confirmOnboard(Long bookId, Map<String, Object> body) {
        InterviewBook book = getActiveBook(bookId);
        InterviewRecord record = recordRepository.findFirstByBookIdAndIsDeleted(book.getId(), 0)
                .orElseThrow(() -> BusinessException.invalidInput("面试预约尚未完成面试"));
        if (record.getInterviewResult() == null || record.getInterviewResult() != 1) {
            throw new BusinessException("INVALID_STATE", "候选人尚未通过面试，无法确认入职", 400);
        }

        // 联动人才库：候选人状态置为 hired
        if (book.getResumeId() != null && book.getResumeId() > 0) {
            resumeRepository.findById(book.getResumeId())
                    .filter(r -> r.getIsDeleted() == null || r.getIsDeleted() == 0)
                    .map(Resume::getCandidateId)
                    .flatMap(candidateRepository::findById)
                    .filter(c -> c.getIsDeleted() == null || c.getIsDeleted() == 0)
                    .ifPresent(c -> {
                        c.setStatus("hired");
                        c.setUpdatedAt(LocalDateTime.now());
                        candidateRepository.save(c);
                    });
        }

        // 联动录用事件状态：找到该面试记录对应的 Offer，再将事件置为已入职
        offerRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("lastInterviewId"), record.getId()))
                .stream().filter(o -> o.getIsDeleted() == null || o.getIsDeleted() == 0)
                .findFirst()
                .flatMap(o -> hireEventRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("offerId"), o.getId()))
                        .stream().findFirst())
                .ifPresent(event -> {
                    event.setEventStatus(1);
                    event.setUpdatedAt(LocalDateTime.now());
                    hireEventRepository.save(event);
                });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("onboarded", true);
        out.put("bookId", book.getId());
        return out;
    }

    /**
     * GET /api/interview/calendar — 面试日历（month 或 week_start 视图）。
     */
    @org.springframework.cache.annotation.Cacheable(cacheNames = "list", key = "'calendar:' + (#weekStart != null ? #weekStart : '') + ':' + (#month != null ? #month : '')")
    public Map<String, Object> getCalendar(String weekStart, String month) {
        boolean monthView = month != null && !month.isBlank();
        LocalDateTime start;
        LocalDateTime rangeStart;
        LocalDateTime rangeEnd;

        if (monthView) {
            try {
                start = LocalDate.parse(month, DateTimeFormatter.ofPattern("yyyy-MM")).atStartOfDay();
            } catch (Exception e) {
                start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            }
            rangeStart = start.withDayOfMonth(1);
            rangeEnd = start.plusMonths(1).minusSeconds(1);
        } else {
            try {
                start = LocalDate.parse(weekStart, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            } catch (Exception e) {
                start = LocalDateTime.now();
            }
            rangeStart = start.minusDays(start.getDayOfWeek().getValue() - 1L);
            rangeEnd = rangeStart.plusDays(7).minusSeconds(1);
        }

        List<InterviewBook> rangeBooks = bookRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("isDeleted"), 0),
                        cb.greaterThanOrEqualTo(root.get("bookTime"), rangeStart),
                        cb.lessThanOrEqualTo(root.get("bookTime"), rangeEnd)),
                Sort.by(Sort.Direction.ASC, "bookTime"));
        Map<Long, Map<String, Object>> bookMaps = toBookMaps(rangeBooks).stream()
                .collect(java.util.stream.Collectors.toMap(
                        m -> Long.parseLong(String.valueOf(m.get("id")).replace("INT", "")),
                        m -> m));

        List<Map<String, Object>> events = new ArrayList<>();
        for (InterviewBook book : rangeBooks) {
            Map<String, Object> m = bookMaps.get(book.getId());
            if (m == null) {
                continue;
            }
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", m.get("id"));
            e.put("title", m.get("name"));
            e.put("position", m.get("position"));
            e.put("start", book.getBookTime() != null
                    ? book.getBookTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            e.put("end", book.getBookTime() != null
                    ? book.getBookTime().plusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            e.put("status", m.get("status"));
            e.put("statusLabel", m.get("statusLabel"));
            e.put("round", m.get("round"));
            e.put("interviewer", m.get("interviewer"));
            e.put("method", m.get("method"));
            e.put("meetingUrl", m.get("meetingUrl"));
            e.put("meetingCode", m.get("meetingCode"));
            e.put("meetingPwd", m.get("meetingPwd"));
            e.put("emailSent", m.get("emailSent"));
            events.add(e);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", rangeStart.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        out.put("monthStart", rangeStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
        out.put("monthEnd", rangeEnd.format(DateTimeFormatter.ISO_LOCAL_DATE));
        out.put("weekStart", rangeStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
        out.put("weekEnd", rangeEnd.format(DateTimeFormatter.ISO_LOCAL_DATE));
        out.put("events", events);
        return out;
    }

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
                    String searchNo = candidateNo;
                    Candidate c = candidateRepository.findAll(
                                    (root, query, cb) -> cb.and(
                                            cb.equal(root.get("candidateNo"), searchNo),
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
        return toBookMap(book, null, null, null, null);
    }

    /**
     * 批量转换面试预约 → Map：一次批量预取简历/候选人/需求/评价记录，
     * 避免每行 5-6 条 SQL（N+1）。
     */
    private List<Map<String, Object>> toBookMaps(List<InterviewBook> books) {
        if (books == null || books.isEmpty()) {
            return List.of();
        }
        List<Long> resumeIds = books.stream().map(InterviewBook::getResumeId)
                .filter(id -> id != null && id > 0).distinct().toList();
        List<Long> demandIds = books.stream().map(InterviewBook::getDemandId)
                .filter(id -> id != null && id > 0).distinct().toList();
        List<Long> bookIds = books.stream().map(InterviewBook::getId)
                .filter(java.util.Objects::nonNull).distinct().toList();

        Map<Long, Resume> resumeMap = new HashMap<>();
        for (Resume r : resumeRepository.findAllById(resumeIds)) {
            if (r.getIsDeleted() == null || r.getIsDeleted() == 0) {
                resumeMap.put(r.getId(), r);
            }
        }
        Map<Long, Candidate> candidateMap = new HashMap<>();
        for (Candidate c : candidateRepository.findAllById(
                resumeMap.values().stream().map(Resume::getCandidateId).filter(java.util.Objects::nonNull).toList())) {
            if (c.getIsDeleted() == null || c.getIsDeleted() == 0) {
                candidateMap.put(c.getId(), c);
            }
        }
        Map<Long, String> demandPosMap = new HashMap<>();
        for (RecruitDemand d : demandRepository.findAllById(demandIds)) {
            boolean active = d.getIsDeleted() == null || d.getIsDeleted() == 0;
            if (active && d.getPositionName() != null && !d.getPositionName().isBlank()) {
                demandPosMap.put(d.getId(), d.getPositionName());
            }
        }
        Map<Long, InterviewRecord> recordMap = new HashMap<>();
        for (InterviewRecord r : recordRepository.findByBookIdInAndIsDeleted(bookIds, 0)) {
            recordMap.putIfAbsent(r.getBookId(), r); // 取首条，保持 findFirstBy 语义
        }

        return books.stream().map(b -> toBookMap(b, resumeMap, candidateMap, demandPosMap, recordMap)).toList();
    }

    private Map<String, Object> toBookMap(InterviewBook book, Map<Long, Resume> resumeMap,
                                          Map<Long, Candidate> candidateMap, Map<Long, String> demandPosMap,
                                          Map<Long, InterviewRecord> recordMap) {
        String[] candidate = resolveCandidate(book.getResumeId(), resumeMap, candidateMap);
        String candidateName = candidate[0];
        String candidateNo = candidate[1];
        String demandPos = resolveDemandPosition(book.getDemandId(), demandPosMap);

        String[] display = deriveDisplayStatus(book, recordMap);
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
        InterviewRecord rec = recordMap != null ? recordMap.get(book.getId()) : null;
        if (rec == null && recordMap == null) {
            rec = recordRepository.findFirstByBookIdAndIsDeleted(book.getId(), 0).orElse(null);
        }
        if (rec != null) {
            result = Map.of(1, "pass", 2, "reject", 3, "hold").get(rec.getInterviewResult());
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
        return deriveDisplayStatus(book, null);
    }

    private String[] deriveDisplayStatus(InterviewBook book, Map<Long, InterviewRecord> recordMap) {
        InterviewRecord record = recordMap != null ? recordMap.get(book.getId())
                : recordRepository.findFirstByBookIdAndIsDeleted(book.getId(), 0).orElse(null);
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
        return resolveCandidate(resumeId, null, null);
    }

    private String[] resolveCandidate(Long resumeId, Map<Long, Resume> resumeMap, Map<Long, Candidate> candidateMap) {
        if (resumeId == null || resumeId <= 0) {
            return new String[]{"候选人#" + resumeId, ""};
        }
        Resume resume = resumeMap != null ? resumeMap.get(resumeId)
                : resumeRepository.findById(resumeId).filter(r -> r.getIsDeleted() == null || r.getIsDeleted() == 0)
                        .orElse(null);
        if (resume == null) {
            return new String[]{"候选人#" + resumeId, ""};
        }
        Candidate c = candidateMap != null ? candidateMap.get(resume.getCandidateId())
                : candidateRepository.findById(resume.getCandidateId())
                        .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                        .orElse(null);
        if (c != null) {
            return new String[]{c.getCandidateName(), c.getCandidateNo() != null ? c.getCandidateNo() : ""};
        }
        return new String[]{"候选人#" + resumeId, ""};
    }

    private String resolveDemandPosition(Long demandId) {
        return resolveDemandPosition(demandId, null);
    }

    private String resolveDemandPosition(Long demandId, Map<Long, String> demandPosMap) {
        if (demandId == null || demandId <= 0) {
            return "岗位#" + demandId;
        }
        if (demandPosMap != null) {
            return demandPosMap.getOrDefault(demandId, "岗位#" + demandId);
        }
        return demandRepository.findById(demandId)
                .filter(d -> d.getIsDeleted() == null || d.getIsDeleted() == 0)
                .map(RecruitDemand::getPositionName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("岗位#" + demandId);
    }

    /**
     * 解析预约标识为数字主键（对齐 Flask _normalize_book_id：'INT0043' -> 43）。
     */
    public Long normalizeBookId(String bookId) {
        String s = String.valueOf(bookId).trim();
        if (s.toUpperCase().startsWith("INT")) {
            s = s.substring(3);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw BusinessException.invalidInput("无效的面试预约ID: " + bookId);
        }
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
