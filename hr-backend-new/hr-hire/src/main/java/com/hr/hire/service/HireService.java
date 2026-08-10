package com.hr.hire.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.common.exception.BusinessException;
import com.hr.hire.entity.Entry;
import com.hr.hire.entity.HireEvent;
import com.hr.hire.entity.Offer;
import com.hr.hire.repository.EntryRepository;
import com.hr.hire.repository.HireEventRepository;
import com.hr.hire.repository.OfferRemindLogRepository;
import com.hr.hire.repository.OfferRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 录用管理服务，对齐 Flask hire_service.py 核心逻辑（Offer 状态机 + 入职单）。
 *
 * Offer 状态机：draft(0) -> sent(1) -> accepted(2) / rejected(3) / expired(4)
 * 跨表联动（需求名额、流程状态、候选人释放）均为 best-effort 原生 SQL，不依赖其他业务模块。
 */
@Service
@RequiredArgsConstructor
public class HireService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int OFFER_EXPIRE_DAYS = 3;
    private static final String OFFER_NO_PREFIX = "OF";
    private static final String ENTRY_NO_PREFIX = "EN";
    private static final String EVENT_NO_PREFIX = "HE";

    /** 状态标签：0草稿 1已发送 2已接受 3已拒绝 4已过期 */
    private static final Map<Integer, String> OFFER_STATUS_LABELS = Map.of(
            0, "草稿", 1, "已发送", 2, "已接受", 3, "已拒绝", 4, "已过期");

    /** 允许的状态流转：当前状态 -> 可达状态 */
    private static final Map<Integer, List<Integer>> OFFER_TRANSITIONS = Map.of(
            0, List.of(1),
            1, List.of(2, 3, 4),
            2, List.of(),
            3, List.of(),
            4, List.of());

    private final OfferRepository offerRepository;
    private final EntryRepository entryRepository;
    private final HireEventRepository hireEventRepository;
    private final OfferRemindLogRepository remindLogRepository;

    private static final int OFFER_REMINDER_INTERVAL_HOURS = 24;

    @PersistenceContext
    private EntityManager entityManager;

    // ── Offer 查询 ────────────────────────────────────────────

    public Map<String, Object> listOffers(int page, int pageSize) {
        Specification<Offer> spec = (root, query, cb) ->
                cb.equal(root.get("isDeleted"), 0);
        Page<Offer> result = offerRepository.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), pageSize, Sort.by(Sort.Direction.DESC, "id")));

        List<Map<String, Object>> list = result.getContent().stream().map(this::toMap).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", list);
        data.put("total", result.getTotalElements());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    public Map<String, Object> getOffer(String offerNo) {
        return toMap(getOfferEntity(offerNo));
    }

    // ── Offer 状态机 ──────────────────────────────────────────

    @Transactional
    public Map<String, Object> createOffer(Map<String, Object> body) {
        Long resumeId = num(body.get("resumeId"));
        Long demandId = num(body.get("demandId"));

        // 重复校验：同简历/同候选人 + 同需求已有进行中 Offer
        String dupNo = findDuplicateOfferNo(resumeId, demandId);
        if (dupNo != null) {
            throw BusinessException.invalidInput(
                    "该候选人在本需求已有进行中的Offer（" + dupNo + "），请勿重复创建");
        }

        LocalDateTime now = LocalDateTime.now();
        Offer offer = new Offer();
        offer.setOfferNo(bizNo(OFFER_NO_PREFIX));
        offer.setResumeId(resumeId != null ? resumeId : 0L);
        offer.setProcessId(numOrZero(body.get("processId")));
        offer.setDemandId(demandId != null ? demandId : 0L);
        offer.setLastInterviewId(num(body.get("lastInterviewId")));
        offer.setOfferContent(str(body.get("offerContent")));
        offer.setSalaryJson(toJson(body.get("salaryJson")));
        offer.setValidDeadline(parseDeadline(body.get("validDeadline"), now));
        offer.setOfferStatus(0); // 草稿
        offer.setSendUserId(numOrZero(body.get("sendUserId")));
        offer.setSendTime(now);
        offer.setCreatedAt(now);
        offer.setUpdatedAt(now);
        offer.setIsDeleted(0);
        offerRepository.save(offer);

        // 创建录用事件（外部 Offer 录用）
        HireEvent event = new HireEvent();
        event.setEventNo(bizNo(EVENT_NO_PREFIX));
        event.setProcessId(num(body.get("processId")));
        event.setOfferId(offer.getId());
        event.setHireType(1);
        event.setEventStatus(0);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        event.setIsDeleted(0);
        hireEventRepository.save(event);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", offer.getOfferNo());
        result.put("created", true);
        result.put("eventId", event.getEventNo());
        return result;
    }

    @Transactional
    public Map<String, Object> sendOffer(String offerNo) {
        Offer offer = getOfferEntity(offerNo);
        validateTransition(offer, 1);

        // 生成 Offer 正文（为空时）
        if (offer.getOfferContent() == null || offer.getOfferContent().isBlank()) {
            offer.setOfferContent(generateOfferLetter(offer));
        }

        LocalDateTime now = LocalDateTime.now();
        offer.setOfferStatus(1);
        offer.setSendTime(now);
        offer.setUpdatedAt(now);
        offerRepository.save(offer);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", true);
        result.put("id", offer.getOfferNo());
        result.put("sendTime", fmt(now));
        result.put("offerContent", offer.getOfferContent());
        result.put("emailSent", false);
        result.put("emailMsg", "未尝试");
        return result;
    }

    @Transactional
    public Map<String, Object> acceptOffer(String offerNo) {
        Offer offer = getOfferEntity(offerNo);
        validateTransition(offer, 2);
        offer.setOfferStatus(2);
        offer.setUpdatedAt(LocalDateTime.now());
        offerRepository.save(offer);

        createEntryFromOffer(offer);
        updateProcessStatus(offer, 6); // accepted

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("id", offer.getOfferNo());
        return result;
    }

    @Transactional
    public Map<String, Object> rejectOffer(String offerNo, String reason) {
        Offer offer = getOfferEntity(offerNo);
        validateTransition(offer, 3);
        offer.setOfferStatus(3);
        offer.setUpdatedAt(LocalDateTime.now());
        offerRepository.save(offer);

        reopenDemandPosition(offer);
        updateProcessStatus(offer, 7); // giveup
        releaseCandidate(offer);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rejected", true);
        result.put("id", offer.getOfferNo());
        return result;
    }

    @Transactional
    public Map<String, Object> withdrawOffer(String offerNo, String reason) {
        Offer offer = getOfferEntity(offerNo);
        if (offer.getOfferStatus() != 0 && offer.getOfferStatus() != 1) {
            throw BusinessException.invalidInput("Offer状态为\"" + label(offer.getOfferStatus())
                    + "\"，无法撤回");
        }
        int oldStatus = offer.getOfferStatus();
        offer.setOfferStatus(3);
        offer.setIsDeleted(1); // 软删
        offer.setUpdatedAt(LocalDateTime.now());
        offerRepository.save(offer);

        // 仅已发送的 Offer 曾占用名额
        if (oldStatus == 1) {
            reopenDemandPosition(offer);
        }
        releaseCandidate(offer);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("withdrawn", true);
        result.put("id", offer.getOfferNo());
        return result;
    }

    /**
     * 更新 Offer 状态（legacy 端点）。校验状态值 + 状态机，status=2/3 时联动业务。
     */
    @Transactional
    public Map<String, Object> updateOfferStatus(String offerNo, Map<String, Object> body) {
        Offer offer = getOfferEntity(offerNo);
        Long statusVal = num(body.get("status"));
        Integer newStatus = statusVal != null ? statusVal.intValue() : null;
        if (newStatus == null || newStatus < 0 || newStatus > 4) {
            throw BusinessException.invalidInput("无效的状态值: " + newStatus);
        }
        validateTransition(offer, newStatus);
        offer.setOfferStatus(newStatus);
        offer.setUpdatedAt(LocalDateTime.now());
        offerRepository.save(offer);

        if (newStatus == 2) {
            createEntryFromOffer(offer);
            updateProcessStatus(offer, 6);
        } else if (newStatus == 3) {
            reopenDemandPosition(offer);
            updateProcessStatus(offer, 7);
            releaseCandidate(offer);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", true);
        result.put("status", newStatus);
        return result;
    }

    /**
     * 超过确认截止时间（默认 3 天）的已发送 Offer 置为过期。
     */
    @Transactional
    public Map<String, Object> expireOffers() {
        LocalDateTime now = LocalDateTime.now();
        List<Offer> sentOffers = offerRepository.findByOfferStatusAndIsDeleted(1, 0);

        List<String> expired = new ArrayList<>();
        for (Offer offer : sentOffers) {
            LocalDateTime base = offer.getSendTime() != null ? offer.getSendTime() : offer.getCreatedAt();
            if (base == null) {
                continue;
            }
            if (!base.plusDays(OFFER_EXPIRE_DAYS).isAfter(now)) {
                offer.setOfferStatus(4);
                offer.setUpdatedAt(now);
                offerRepository.save(offer);
                reopenDemandPosition(offer);
                updateProcessStatus(offer, 4); // 淘汰
                releaseCandidate(offer);
                expired.add(offer.getOfferNo());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expiredCount", expired.size());
        result.put("expired", expired);
        return result;
    }

    /**
     * POST /api/hire/offers/followup — 倒计时提醒 + 超时淘汰。
     * 对齐 Flask hire_service.offer_followup()。
     */
    @Transactional
    public Map<String, Object> offerFollowup() {
        LocalDateTime now = LocalDateTime.now();
        List<Offer> sentOffers = offerRepository.findByOfferStatusAndIsDeleted(1, 0);

        List<Map<String, Object>> reminded = new ArrayList<>();
        List<String> expiredList = new ArrayList<>();

        for (Offer offer : sentOffers) {
            LocalDateTime sendTime = offer.getSendTime() != null ? offer.getSendTime() : offer.getCreatedAt();
            if (sendTime == null) {
                continue;
            }
            LocalDateTime deadline = sendTime.plusDays(OFFER_EXPIRE_DAYS);

            // 已过期 → 跳过（expireOffers 处理）
            if (!deadline.isAfter(now)) {
                continue;
            }

            // 计算剩余天数
            int daysLeft = Math.max(1, (int) java.time.Duration.between(now, deadline).toDays() + 1);

            // 去重：24h 内不重复发送提醒
            if (daysLeft > 0) {
                var lastRemind = remindLogRepository
                        .findFirstByOfferIdAndRemindTypeOrderByIdDesc(offer.getId(), "countdown");
                if (lastRemind.isPresent()) {
                    LocalDateTime lastSent = lastRemind.get().getCreatedAt();
                    if (lastSent != null && lastSent.plusHours(OFFER_REMINDER_INTERVAL_HOURS).isAfter(now)) {
                        continue; // 24h 内已发送，跳过
                    }
                }
            }

            // 记录提醒（best-effort 邮件发送）
            OfferRemindLog log = new OfferRemindLog();
            log.setOfferId(offer.getId());
            log.setOfferNo(offer.getOfferNo());
            log.setRemindType("countdown");
            log.setDaysLeft(daysLeft);
            log.setSendOk(1);
            log.setSendMsg("倒计时提醒已记录（邮件发送待 SMTP 配置）");
            log.setCreatedAt(now);
            log.setIsDeleted(0);
            remindLogRepository.save(log);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("offerNo", offer.getOfferNo());
            r.put("daysLeft", daysLeft);
            r.put("deadline", deadline.toString());
            reminded.add(r);
        }

        // 自动过期
        Map<String, Object> expireResult = expireOffers();
        @SuppressWarnings("unchecked")
        List<String> expired = (List<String>) expireResult.get("expired");
        if (expired != null) {
            expiredList.addAll(expired);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reminded", reminded);
        result.put("expired", expiredList);
        result.put("deadlineDays", OFFER_EXPIRE_DAYS);
        return result;
    }

    // ── 入职单 ────────────────────────────────────────────────

    public Map<String, Object> listEntries(int page, int pageSize) {
        Specification<Entry> spec = (root, query, cb) ->
                cb.equal(root.get("isDeleted"), 0);
        Page<Entry> result = entryRepository.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), pageSize, Sort.by(Sort.Direction.DESC, "id")));

        List<Map<String, Object>> list = result.getContent().stream().map(this::entryMap).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", list);
        data.put("total", result.getTotalElements());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    public Map<String, Object> getEntry(String entryNo) {
        return entryMap(getEntryEntity(entryNo));
    }

    @Transactional
    public Map<String, Object> createEntry(Map<String, Object> body) {
        LocalDateTime now = LocalDateTime.now();
        Entry entry = new Entry();
        entry.setEntryNo(bizNo(ENTRY_NO_PREFIX));
        entry.setEventId(numOrZero(body.get("eventId")));
        entry.setResumeId(numOrZero(body.get("resumeId")));
        entry.setDeptId(numOrZero(body.get("deptId")));
        entry.setPositionId(numOrZero(body.get("positionId")));
        entry.setEntryDate(parseDate(body.get("entryDate"), LocalDate.now()));
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        entry.setIsDeleted(0);
        entryRepository.save(entry);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entry.getEntryNo());
        result.put("created", true);
        return result;
    }

    // ── 私有：Offer 联动 ──────────────────────────────────────

    private Offer getOfferEntity(String offerNo) {
        return offerRepository.findByOfferNoAndIsDeleted(offerNo, 0)
                .orElseThrow(() -> BusinessException.notFound("Offer " + offerNo + " 不存在"));
    }

    private Entry getEntryEntity(String entryNo) {
        return entryRepository.findByEntryNoAndIsDeleted(entryNo, 0)
                .orElseThrow(() -> BusinessException.notFound("入职单 " + entryNo + " 不存在"));
    }

    private void validateTransition(Offer offer, int newStatus) {
        List<Integer> allowed = OFFER_TRANSITIONS.getOrDefault(offer.getOfferStatus(), List.of());
        if (!allowed.contains(newStatus)) {
            throw BusinessException.invalidInput("Offer状态\"" + label(offer.getOfferStatus())
                    + "\"(" + offer.getOfferStatus() + ")不允许切换到\""
                    + label(newStatus) + "\"(" + newStatus + ")");
        }
    }

    private String label(Integer status) {
        return OFFER_STATUS_LABELS.getOrDefault(status, "未知");
    }

    /**
     * 查重：同 resume+demand 或同候选人（不同简历）+demand 的进行中 Offer。
     * 返回已存在的 Offer 编号，无重复返回 null。
     */
    private String findDuplicateOfferNo(Long resumeId, Long demandId) {
        if (resumeId == null || demandId == null) {
            return null;
        }
        List<Offer> sameResume = offerRepository.findByResumeIdAndDemandIdAndOfferStatusInAndIsDeleted(
                resumeId, demandId, List.of(0, 1), 0);
        if (!sameResume.isEmpty()) {
            return sameResume.get(0).getOfferNo();
        }
        try {
            @SuppressWarnings("unchecked")
            List<String> rows = entityManager.createNativeQuery(
                    "SELECT o.offer_no FROM t_hr_offer o " +
                    "JOIN t_hr_resume r ON r.id = o.resume_id " +
                    "WHERE r.candidate_id = (SELECT candidate_id FROM t_hr_resume WHERE id = :resumeId AND is_deleted = 0) " +
                    "AND o.demand_id = :demandId AND o.offer_status IN (0, 1) " +
                    "AND o.is_deleted = 0 AND r.is_deleted = 0 LIMIT 1")
                    .setParameter("resumeId", resumeId)
                    .setParameter("demandId", demandId)
                    .getResultList();
            return rows.isEmpty() ? null : String.valueOf(rows.get(0));
        } catch (Exception e) {
            return null; // 关联表查询失败时放行，主查重已覆盖
        }
    }

    /**
     * Offer 接受后自动生成入职单，并更新录用事件状态。
     */
    private void createEntryFromOffer(Offer offer) {
        HireEvent event = hireEventRepository
                .findFirstByOfferIdAndIsDeletedOrderByIdDesc(offer.getId(), 0)
                .orElse(null);
        if (event == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        long[] deptPosition = resolveDemandDeptPosition(offer.getDemandId());

        Entry entry = new Entry();
        entry.setEntryNo(bizNo(ENTRY_NO_PREFIX));
        entry.setEventId(event.getId());
        entry.setResumeId(offer.getResumeId());
        entry.setDeptId(deptPosition[0]);
        entry.setPositionId(deptPosition[1]);
        entry.setEntryDate(today);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());
        entry.setIsDeleted(0);
        entryRepository.save(entry);

        event.setEventStatus(1); // 已生成入职单
        event.setUpdatedAt(LocalDateTime.now());
        hireEventRepository.save(event);
    }

    private long[] resolveDemandDeptPosition(Long demandId) {
        try {
            Object row = entityManager.createNativeQuery(
                    "SELECT dept_id, position_id FROM t_hr_recruit_demand " +
                    "WHERE id = :demandId AND is_deleted = 0")
                    .setParameter("demandId", demandId)
                    .getSingleResult();
            Object[] cols = (Object[]) row;
            long deptId = cols[0] != null ? ((Number) cols[0]).longValue() : 1L;
            long positionId = cols[1] != null ? ((Number) cols[1]).longValue() : 1L;
            return new long[]{deptId, positionId};
        } catch (Exception e) {
            return new long[]{1L, 1L};
        }
    }

    private String generateOfferLetter(Offer offer) {
        String candidateName = "候选人";
        String deptName = "部门";
        String positionName = "岗位";

        try {
            Object name = entityManager.createNativeQuery(
                    "SELECT c.candidate_name FROM t_hr_candidate c " +
                    "JOIN t_hr_resume r ON r.candidate_id = c.id " +
                    "WHERE r.id = :resumeId AND r.is_deleted = 0 AND c.is_deleted = 0 LIMIT 1")
                    .setParameter("resumeId", offer.getResumeId())
                    .getSingleResult();
            if (name != null) {
                candidateName = String.valueOf(name);
            }
        } catch (Exception ignored) {
        }
        try {
            Object row = entityManager.createNativeQuery(
                    "SELECT dept_id, position_id FROM t_hr_recruit_demand " +
                    "WHERE id = :demandId AND is_deleted = 0")
                    .setParameter("demandId", offer.getDemandId())
                    .getSingleResult();
            Object[] cols = (Object[]) row;
            deptName = "部门#" + (cols[0] != null ? cols[0] : "?");
            positionName = "岗位#" + (cols[1] != null ? cols[1] : "?");
        } catch (Exception ignored) {
        }
        return "【Offer Letter】\n\n"
                + "尊敬的 " + candidateName + " 先生/女士：\n\n"
                + "感谢您参加我司面试。经过综合评估，我们非常荣幸地邀请您加入 " + deptName
                + "，担任 " + positionName + " 一职。\n\n"
                + "具体薪资待遇以附件/系统记录为准。如您接受此Offer，请在有效期内确认。\n\n"
                + "期待与您共事！\n"
                + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /** Offer 拒绝/过期时释放需求名额（filled_count - 1），best-effort。 */
    private void reopenDemandPosition(Offer offer) {
        try {
            entityManager.createNativeQuery(
                    "UPDATE t_hr_recruit_demand SET filled_count = filled_count - 1 " +
                    "WHERE id = :demandId AND is_deleted = 0 AND filled_count > 0")
                    .setParameter("demandId", offer.getDemandId())
                    .executeUpdate();
        } catch (Exception ignored) {
        }
    }

    /** 更新招聘流程状态，best-effort。 */
    private void updateProcessStatus(Offer offer, int processStatus) {
        if (offer.getProcessId() == null || offer.getProcessId() == 0) {
            return;
        }
        try {
            entityManager.createNativeQuery(
                    "UPDATE t_hr_recruit_process SET process_status = :status " +
                    "WHERE id = :processId AND is_deleted = 0")
                    .setParameter("status", processStatus)
                    .setParameter("processId", offer.getProcessId())
                    .executeUpdate();
        } catch (Exception ignored) {
        }
    }

    /** Offer 终止后释放候选人锁回人才库，best-effort。 */
    private void releaseCandidate(Offer offer) {
        try {
            entityManager.createNativeQuery(
                    "UPDATE t_hr_candidate SET status = 'available' " +
                    "WHERE status = 'locked' AND id = " +
                    "(SELECT candidate_id FROM t_hr_resume WHERE id = :resumeId AND is_deleted = 0 LIMIT 1)")
                    .setParameter("resumeId", offer.getResumeId())
                    .executeUpdate();
        } catch (Exception ignored) {
        }
    }

    // ── 私有：序列化 ──────────────────────────────────────────

    private Map<String, Object> toMap(Offer o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getOfferNo());
        m.put("resumeId", o.getResumeId());
        m.put("processId", o.getProcessId());
        m.put("demandId", o.getDemandId());
        m.put("lastInterviewId", o.getLastInterviewId());
        m.put("offerContent", o.getOfferContent());
        m.put("salaryJson", parseJson(o.getSalaryJson()));
        m.put("validDeadline", o.getValidDeadline() != null ? fmt(o.getValidDeadline()) : null);
        m.put("status", o.getOfferStatus());
        m.put("statusLabel", label(o.getOfferStatus()));
        m.put("sendUserId", o.getSendUserId());
        m.put("sendTime", o.getSendTime() != null ? fmt(o.getSendTime()) : null);
        return m;
    }

    private Map<String, Object> entryMap(Entry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getEntryNo());
        m.put("eventId", e.getEventId());
        m.put("resumeId", e.getResumeId());
        m.put("deptId", e.getDeptId());
        m.put("positionId", e.getPositionId());
        m.put("entryDate", e.getEntryDate() != null ? e.getEntryDate().toString() : null);
        return m;
    }

    // ── 私有：工具 ────────────────────────────────────────────

    private static String bizNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private static String fmt(LocalDateTime t) {
        return t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static Long num(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long numOrZero(Object o) {
        Long v = num(o);
        return v != null ? v : 0L;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String toJson(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    /** 前端 date 输入传 'YYYY-MM-DD'，转当日 23:59:59；缺省为 now+7 天。 */
    private static LocalDateTime parseDeadline(Object o, LocalDateTime now) {
        if (o == null || String.valueOf(o).isBlank()) {
            return now.plusDays(7);
        }
        String s = String.valueOf(o).trim();
        try {
            return LocalDate.parse(s.substring(0, 10)).atTime(23, 59, 59);
        } catch (Exception e) {
            throw BusinessException.invalidInput("Offer有效期格式不正确（应为 YYYY-MM-DD）");
        }
    }

    private static LocalDate parseDate(Object o, LocalDate fallback) {
        if (o == null || String.valueOf(o).isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(String.valueOf(o).trim().substring(0, 10));
        } catch (Exception e) {
            return fallback;
        }
    }
}
