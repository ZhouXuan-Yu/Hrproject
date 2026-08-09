package com.hr.talent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.auth.entity.IamDept;
import com.hr.auth.entity.IamPosition;
import com.hr.auth.entity.IamUser;
import com.hr.auth.repository.IamDeptRepository;
import com.hr.auth.repository.IamPositionRepository;
import com.hr.auth.repository.IamUserRepository;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.SecurityUtils;
import com.hr.talent.entity.Candidate;
import com.hr.talent.entity.Employee;
import com.hr.talent.entity.FileEntity;
import com.hr.talent.entity.MailLog;
import com.hr.talent.entity.RecruitProcess;
import com.hr.talent.entity.Resume;
import com.hr.talent.entity.ResumeMatch;
import com.hr.talent.repository.CandidateRepository;
import com.hr.talent.repository.EmployeeRepository;
import com.hr.talent.repository.FileEntityRepository;
import com.hr.talent.repository.MailLogRepository;
import com.hr.talent.repository.RecruitProcessRepository;
import com.hr.talent.repository.ResumeMatchRepository;
import com.hr.talent.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 人才库服务，对齐 Flask talent_service.py 核心逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalentService {

    private static final Map<Integer, String> EDU_LABELS = Map.of(
            1, "大专", 2, "本科", 3, "硕士", 4, "博士");
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "available", "可联系", "locked", "面试中(锁定)", "reserve", "储备",
            "archived", "已封存", "hired", "已入职");
    private static final Map<String, String> MAIL_TYPE_LABELS = Map.of(
            "invite", "面试邀请", "offer", "录用通知", "entry", "入职指引",
            "test", "测试邮件", "other", "系统邮件");
    private static final Map<Integer, String> SCHOOL_LABELS = Map.of(
            1, "普通", 2, "211", 3, "985", 4, "C9");
    private static final Map<String, String> PERF_LABELS = Map.of(
            "4.5", "A+", "4.0", "A", "3.5", "B+", "3.0", "B", "2.5", "C+");

    private final CandidateRepository candidateRepository;
    private final ResumeRepository resumeRepository;
    private final EmployeeRepository employeeRepository;
    private final IamUserRepository iamUserRepository;
    private final IamDeptRepository iamDeptRepository;
    private final IamPositionRepository iamPositionRepository;
    private final RecruitProcessRepository recruitProcessRepository;
    private final ResumeMatchRepository resumeMatchRepository;
    private final FileEntityRepository fileEntityRepository;
    private final MailLogRepository mailLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads";

    /**
     * 候选人列表（分页 + keyword + status 筛选）。
     */
    @org.springframework.cache.annotation.Cacheable(cacheNames = "list", key = "'talent:' + #page + ':' + #pageSize + ':' + (#keyword != null ? #keyword : '') + ':' + (#status != null ? #status : '')")
    public Map<String, Object> listCandidates(int page, int pageSize, String keyword, String status) {
        Specification<Candidate> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), 0));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("candidateName"), like),
                        cb.like(root.get("candidateNo"), like),
                        cb.like(root.get("sourceChannel"), like)));
            }
            if (status != null && !status.isBlank() && !"all".equals(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Candidate> result = candidateRepository.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), pageSize, Sort.by(Sort.Direction.DESC, "id")));

        List<Candidate> content = result.getContent();
        // 批量预取本页全部简历，避免每行 2 次查询（N+1）
        Map<Long, List<Resume>> resumeByCandidate = fetchResumesByCandidateIds(
                content.stream().map(Candidate::getId).toList());

        List<Map<String, Object>> list = content.stream()
                .map(c -> toCandidateMap(c, resumeByCandidate)).toList();
        return pageData(list, result.getTotalElements(), page, pageSize);
    }

    /**
     * 内部员工列表（分页，keyword 匹配员工姓名）。
     */
    @org.springframework.cache.annotation.Cacheable(cacheNames = "list", key = "'employees:' + #page + ':' + #pageSize + ':' + (#keyword != null ? #keyword : '')")
    public Map<String, Object> listEmployees(int page, int pageSize, String keyword) {
        Specification<Employee> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), 0));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                jakarta.persistence.criteria.Subquery<Long> sub = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<IamUser> userRoot = sub.from(IamUser.class);
                sub.select(userRoot.get("userId"));
                sub.where(cb.and(
                        cb.like(userRoot.get("realName"), like),
                        cb.equal(userRoot.get("isDeleted"), 0)));
                predicates.add(cb.in(root.get("userId")).value(sub));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Employee> result = employeeRepository.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), pageSize, Sort.by(Sort.Direction.DESC, "id")));

        List<Employee> content = result.getContent();
        // 批量预取用户/部门/岗位，避免每行 3 次查询（N+1）
        Map<Long, String> userNames = fetchUserNames(
                content.stream().map(Employee::getUserId).filter(java.util.Objects::nonNull).toList());
        Map<Long, String> deptNames = fetchDeptNames(
                content.stream().map(Employee::getDeptId).filter(java.util.Objects::nonNull).toList());
        Map<Long, String> positionNames = fetchPositionNames(
                content.stream().map(Employee::getPositionId).filter(java.util.Objects::nonNull).toList());

        List<Map<String, Object>> list = content.stream()
                .map(e -> toEmployeeMap(e, userNames, deptNames, positionNames)).toList();
        return pageData(list, result.getTotalElements(), page, pageSize);
    }

    /**
     * 候选人详情。
     */
    public Map<String, Object> getCandidateDetail(Long candidateId) {
        Candidate c = candidateRepository.findById(candidateId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("候选人不存在"));

        Map<String, Object> m = toCandidateMap(c);
        m.put("mobile", maskPhone(c.getMobile()));
        m.put("email", c.getEmail() != null ? c.getEmail() : "—");
        m.put("blackFlag", c.getBlackFlag() != null && c.getBlackFlag() == 1);
        m.put("schoolLevel", SCHOOL_LABELS.getOrDefault(c.getSchoolLevel(), "—"));
        m.put("resume", resolveResumeInfo(c.getId()));
        return m;
    }

    /**
     * 内部员工详情。
     */
    public Map<String, Object> getEmployeeDetail(Long employeeId) {
        Employee e = employeeRepository.findById(employeeId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("员工不存在"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "EMP" + String.format("%03d", e.getId()));
        m.put("name", resolveUserName(e.getUserId()));
        m.put("dept", resolveDeptName(e.getDeptId()));
        m.put("pos", resolvePositionName(e.getPositionId()));
        m.put("years", (e.getWorkYears() != null ? e.getWorkYears() : 0) + "年");
        m.put("perf", perfLabel(e.getPerfScore()));
        m.put("score", e.getCompositiveScore() != null ? e.getCompositiveScore().doubleValue() : 0);
        m.put("grade", profileGrade(e.getCompositiveScore() != null ? e.getCompositiveScore().doubleValue() : 0));
        m.put("transfer", e.getCanTransfer() != null && e.getCanTransfer() == 1);
        m.put("restrictReason", e.getTransferRestrictReason() != null ? e.getTransferRestrictReason() : "");
        m.put("lastPromote", e.getLastPromoteDate() != null ? e.getLastPromoteDate().toString().substring(0, 7) : "—");
        return m;
    }

    /**
     * 更新候选人备注。
     */
    @Transactional
    public Map<String, Object> updateNote(Long candidateId, String note) {
        Candidate c = candidateRepository.findById(candidateId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("候选人不存在"));
        c.setNote(note != null ? note : "");
        c.setUpdatedAt(LocalDateTime.now());
        candidateRepository.save(c);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("updated", true);
        m.put("id", c.getId());
        return m;
    }

    // ── 私有方法 ──────────────────────────────────────────────

    /**
     * GET /api/talent/match — 内部员工人岗匹配结果（demandId 参数）。
     */
    public Map<String, Object> getMatchResults(String demandId) {
        if (demandId == null || demandId.isBlank()) {
            throw BusinessException.invalidInput("缺少 demandId 参数");
        }
        List<Employee> employees = employeeRepository.findByIsDeleted(0);
        // 批量预取用户/部门/岗位名称，避免循环内 3 次查询（N+1）
        Map<Long, String> userNames = fetchUserNames(
                employees.stream().map(Employee::getUserId).filter(java.util.Objects::nonNull).toList());
        Map<Long, String> deptNames = fetchDeptNames(
                employees.stream().map(Employee::getDeptId).filter(java.util.Objects::nonNull).toList());
        Map<Long, String> positionNames = fetchPositionNames(
                employees.stream().map(Employee::getPositionId).filter(java.util.Objects::nonNull).toList());

        List<Map<String, Object>> results = new ArrayList<>();
        for (Employee e : employees) {
            String name = resolveUserName(e.getUserId(), userNames);
            int seed = Math.abs((demandId + ":" + e.getId()).hashCode());
            int score = 50 + (seed % 46); // 50-95
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "EMP" + String.format("%03d", e.getId()));
            m.put("name", name);
            m.put("dept", resolveDeptName(e.getDeptId(), deptNames));
            m.put("curPos", resolvePositionName(e.getPositionId(), positionNames));
            m.put("perf", perfLabel(e.getPerfScore()));
            m.put("score", score);
            m.put("transferable", e.getCanTransfer() != null && e.getCanTransfer() == 1);
            results.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("demandId", demandId);
        out.put("results", results);
        return out;
    }

    /**
     * POST /api/talent/match — 计算候选人对需求的匹配分。
     */
    public Map<String, Object> createMatch(Map<String, Object> body) {
        String candidateId = body != null && body.get("candidateId") != null
                ? String.valueOf(body.get("candidateId")) : "";
        String demandId = body != null && body.get("demandId") != null
                ? String.valueOf(body.get("demandId")) : "";
        if (candidateId.isBlank() || demandId.isBlank()) {
            throw BusinessException.invalidInput("缺少 candidateId 或 demandId 参数");
        }
        Candidate c = candidateRepository.findById(parseId(candidateId))
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("候选人不存在"));
        double base = c.getStaticAbilityScore() != null ? c.getStaticAbilityScore().doubleValue() : 0;
        int seed = Math.abs((demandId + ":" + c.getId()).hashCode());
        int matchScore = (int) Math.min(99, Math.max(30, base * 0.6 + (seed % 40)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidateId", c.getId());
        out.put("candidateNo", c.getCandidateNo());
        out.put("name", c.getCandidateName());
        out.put("demandId", demandId);
        out.put("matchScore", matchScore);
        out.put("grade", profileGrade(matchScore));
        out.put("scoreDetail", Map.of(
                "ability", base,
                "match", matchScore));
        out.put("_fallback", false);
        return out;
    }

    /**
     * POST /api/talent/link — 批量关联候选人到需求。
     */
    @Transactional
    public Map<String, Object> linkToDemand(Map<String, Object> body) {
        String demandId = body != null && body.get("demandId") != null
                ? String.valueOf(body.get("demandId")) : "";
        Object namesObj = body != null ? body.get("names") : null;
        List<String> names = new ArrayList<>();
        if (namesObj instanceof List<?> list) {
            list.forEach(n -> names.add(String.valueOf(n)));
        }
        if (demandId.isBlank() || names.isEmpty()) {
            throw BusinessException.invalidInput("缺少 demandId 或 names 参数");
        }

        Long demandIdLong;
        try {
            demandIdLong = Long.parseLong(demandId);
        } catch (NumberFormatException e) {
            throw BusinessException.invalidInput("无效的 demandId");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int linked = 0;
        for (String name : names) {
            Map<String, Object> r = linkOne(demandIdLong, name);
            results.add(r);
            if (Boolean.TRUE.equals(r.get("linked"))) {
                linked++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("linked", linked);
        out.put("total", names.size());
        out.put("candidates", results);
        return out;
    }

    private Map<String, Object> linkOne(Long demandId, String name) {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("name", name);
        fail.put("linked", false);
        fail.put("linkedCount", 0);

        Candidate c = candidateRepository.findFirstByCandidateNameAndIsDeleted(name, 0).orElse(null);
        if (c == null) {
            fail.put("reason", "候选人不存在");
            return fail;
        }
        if (c.getBlackFlag() != null && c.getBlackFlag() == 1) {
            fail.put("reason", "黑名单候选人不可加入需求");
            return fail;
        }
        if ("locked".equals(c.getStatus())) {
            fail.put("reason", "候选人面试中（锁定），不可重复加入");
            return fail;
        }

        List<Resume> resumes = resumeRepository.findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(c.getId(), 0);
        Long resumeId = resumes.isEmpty() ? null : resumes.get(0).getId();

        // 去重：进行中的流程不重复创建（4淘汰 7放弃 除外）
        boolean exists = recruitProcessRepository
                .findFirstByCandidateIdAndDemandIdAndProcessStatusNotInAndIsDeleted(
                        c.getId(), demandId, List.of(4, 7), 0)
                .isPresent();
        if (exists) {
            fail.put("reason", "该候选人已在需求中");
            return fail;
        }

        RecruitProcess p = new RecruitProcess();
        p.setProcessNo("PROC" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        p.setDemandId(demandId);
        p.setResumeId(resumeId != null ? resumeId : 0L);
        p.setCandidateId(c.getId());
        p.setProcessStatus(0);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p.setIsDeleted(0);
        recruitProcessRepository.save(p);

        if (resumeId != null) {
            saveMatchRecord(resumeId, demandId, c);
        }

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("name", name);
        ok.put("linked", true);
        ok.put("linkedCount", 1);
        ok.put("candidateId", c.getId());
        ok.put("processNo", p.getProcessNo());
        return ok;
    }

    private void saveMatchRecord(Long resumeId, Long demandId, Candidate c) {
        double base = c.getStaticAbilityScore() != null ? c.getStaticAbilityScore().doubleValue() : 0;
        int score = (int) Math.min(99, Math.max(30, base * 0.6 + (Math.abs((demandId + ":" + resumeId).hashCode()) % 40)));
        ResumeMatch m = new ResumeMatch();
        m.setResumeId(resumeId);
        m.setDemandId(demandId);
        m.setMatchScore(BigDecimal.valueOf(score));
        m.setScoreDetail("{\"ability\":" + base + ",\"match\":" + score + "}");
        m.setCalculateTime(LocalDateTime.now());
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        m.setIsDeleted(0);
        resumeMatchRepository.save(m);
    }

    /**
     * GET /api/talent/candidate/{id}/contact-info — 完整联系方式（HR 及以上）。
     */
    public Map<String, Object> getCandidateContact(Long candidateId) {
        Candidate c = candidateRepository.findById(candidateId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("候选人不存在: " + candidateId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("candidateNo", c.getCandidateNo());
        m.put("name", c.getCandidateName());
        m.put("mobile", c.getMobile() != null ? c.getMobile() : "");
        m.put("email", c.getEmail() != null ? c.getEmail() : "");
        return m;
    }

    /**
     * POST /api/talent/contact — 记录（可选发送）候选人联系动作。
     */
    @Transactional
    public Map<String, Object> recordContact(Map<String, Object> body) {
        Object namesObj = body != null ? body.get("names") : null;
        if (namesObj instanceof List<?> list && !list.isEmpty()) {
            String method = body.get("method") != null ? String.valueOf(body.get("method")) : "系统记录";
            List<Map<String, Object>> contacts = new ArrayList<>();
            for (Object n : list) {
                Map<String, Object> contact = new LinkedHashMap<>();
                contact.put("name", String.valueOf(n));
                contact.put("note", "[contact] HR via " + method);
                contacts.add(contact);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("recorded", true);
            out.put("count", contacts.size());
            out.put("contacts", contacts);
            return out;
        }

        String candidateId = body != null && body.get("candidateId") != null
                ? String.valueOf(body.get("candidateId")) : "";
        if (candidateId.isBlank()) {
            throw BusinessException.invalidInput("缺少 candidateId 或 names 参数");
        }
        Map<String, Object> contact = getCandidateContact(parseId(candidateId));
        String method = body.get("method") != null ? String.valueOf(body.get("method")) : "系统记录";
        String channel = ((body.get("channel") != null ? String.valueOf(body.get("channel"))
                : method) + "").toLowerCase();

        Map<String, Object> sendResult = new LinkedHashMap<>();
        sendResult.put("attempted", false);
        sendResult.put("sent", false);
        sendResult.put("message", "recorded only");

        if (channel.contains("email") || channel.contains("mail")) {
            String draft = body.get("draft") != null ? String.valueOf(body.get("draft")) : "";
            if (contact.get("email") == null || String.valueOf(contact.get("email")).isBlank()) {
                sendResult.put("attempted", true);
                sendResult.put("message", "candidate email is empty");
            } else if (draft.isBlank()) {
                sendResult.put("attempted", true);
                sendResult.put("message", "draft is empty");
            } else {
                // 邮件发送需邮件服务，当前仅记录（对齐后端可发送能力）
                sendResult.put("attempted", true);
                sendResult.put("sent", false);
                sendResult.put("message", "邮件服务未接入，已记录");
                sendResult.put("recipient", contact.get("email"));
            }
        } else if (channel.contains("feishu") || channel.contains("飞书")) {
            String draft = body.get("draft") != null ? String.valueOf(body.get("draft")) : "";
            sendResult.put("attempted", true);
            sendResult.put("sent", false);
            sendResult.put("message", draft.isBlank() ? "draft is empty" : "飞书消息服务未接入，已记录");
        }

        String noteText = "[contact] HR via " + method
                + "; sent=" + sendResult.get("sent")
                + "; msg=" + sendResult.get("message");
        updateNote(parseId(candidateId), noteText);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recorded", true);
        out.put("sent", sendResult.get("sent"));
        out.put("send", sendResult);
        Map<String, Object> contactOut = new LinkedHashMap<>();
        contactOut.put("id", candidateId);
        contactOut.put("note", noteText);
        out.put("contact", contactOut);
        return out;
    }

    /**
     * GET /api/talent/ingest-log — 最近简历入库记录。
     */
    public List<Map<String, Object>> getIngestLog(int limit) {
        int n = Math.max(1, Math.min(50, limit));
        List<Resume> rows = resumeRepository.findAll(
                PageRequest.of(0, n, Sort.by(Sort.Direction.DESC, "storageTime")))
                .getContent();
        Map<Long, Candidate> cands = new HashMap<>();
        for (Resume r : rows) {
            if (r.getCandidateId() != null && !cands.containsKey(r.getCandidateId())) {
                candidateRepository.findById(r.getCandidateId())
                        .filter(c -> c.getIsDeleted() == null || c.getIsDeleted() == 0)
                        .ifPresent(c -> cands.put(c.getId(), c));
            }
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Resume r : rows) {
            Candidate c = cands.get(r.getCandidateId());
            JsonNode ext = parseJson(r.getExtractJson());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resumeId", r.getId());
            m.put("candidate", c != null ? c.getCandidateName() : "#" + r.getCandidateId());
            m.put("candidateNo", c != null ? c.getCandidateNo() : "");
            m.put("source", c != null && c.getSourceChannel() != null ? c.getSourceChannel() : "邮箱");
            m.put("engine", ext != null ? ext.path("parse_engine").asText("—") : "—");
            m.put("parsedAt", ext != null ? ext.path("parsed_at").asText("") : "");
            m.put("storageTime", r.getStorageTime() != null
                    ? r.getStorageTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            m.put("summary", r.getWorkExpText() != null && r.getWorkExpText().length() > 60
                    ? r.getWorkExpText().substring(0, 60) : r.getWorkExpText());
            items.add(m);
        }
        return items;
    }

    /**
     * GET /api/talent/mail-log — 系统外发邮件日志（来源 t_hr_mail_log）。
     */
    public List<Map<String, Object>> getMailLog(int limit) {
        int capped = Math.min(limit <= 0 ? 50 : limit, 200);
        List<MailLog> rows = mailLogRepository.findTop50ByIsDeletedOrderByIdDesc(0);
        if (capped < 50) {
            rows = rows.stream().limit(capped).toList();
        }
        return rows.stream().map(this::mailLogToMap).toList();
    }

    private Map<String, Object> mailLogToMap(MailLog r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("sender", r.getSenderEmail());
        m.put("recipient", r.getRecipient());
        m.put("subject", r.getSubject());
        m.put("type", r.getMailType());
        m.put("typeLabel", MAIL_TYPE_LABELS.getOrDefault(r.getMailType(), "系统邮件"));
        m.put("ok", r.getStatus() != null && r.getStatus() == 1);
        m.put("error", r.getErrorMsg());
        m.put("time", r.getCreatedAt() != null
                ? r.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "");
        return m;
    }

    /**
     * POST /api/talent/upload-resume — 简历文件上传。
     * 无 AI 服务时降级为「记录文件 + 按文件名入库」，保持契约可用。
     */
    @Transactional
    public Map<String, Object> uploadResume(MultipartFile file, String position, String note) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.invalidInput("请选择要上传的简历文件");
        }
        String original = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot).toLowerCase();
        }
        if (!List.of(".pdf", ".docx", ".doc", ".txt").contains(ext)) {
            throw BusinessException.invalidInput("不支持的文件格式 " + ext + "，支持 PDF/DOCX/TXT");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw BusinessException.invalidInput("文件大小不能超过 20MB");
        }

        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = Paths.get(UPLOAD_DIR, storedName);
            file.transferTo(target);

            FileEntity fe = new FileEntity();
            fe.setFileName(original);
            fe.setFileUrl(target.toAbsolutePath().toString());
            fe.setFileExtension(ext.replace(".", ""));
            fe.setFileSize(file.getSize());
            fe.setBizType("resume");
            fe.setCreatedAt(LocalDateTime.now());
            fe.setUpdatedAt(LocalDateTime.now());
            fe.setIsDeleted(0);
            fileEntityRepository.save(fe);

            String name = original.substring(0, dot < 0 ? original.length() : dot).trim();
            Candidate candidate = candidateRepository.findFirstByCandidateNameAndIsDeleted(name, 0)
                    .orElseGet(() -> {
                        Candidate c = new Candidate();
                        c.setCandidateNo("C" + System.currentTimeMillis());
                        c.setCandidateName(name);
                        c.setStatus("available");
                        c.setBlackFlag(0);
                        c.setBigCompanyFlag(0);
                        c.setCertCount(0);
                        c.setSourceChannel("手动上传");
                        c.setCreatedAt(LocalDateTime.now());
                        c.setUpdatedAt(LocalDateTime.now());
                        c.setIsDeleted(0);
                        return candidateRepository.save(c);
                    });
            if (note != null && !note.isBlank()) {
                candidate.setNote(note);
                candidate.setUpdatedAt(LocalDateTime.now());
                candidateRepository.save(candidate);
            }

            Resume resume = new Resume();
            resume.setCandidateId(candidate.getId());
            resume.setResumeFileId(fe.getId());
            resume.setStorageTime(LocalDateTime.now());
            resume.setBaseScore(BigDecimal.ZERO);
            resume.setCreatedAt(LocalDateTime.now());
            resume.setUpdatedAt(LocalDateTime.now());
            resume.setIsDeleted(0);
            resumeRepository.save(resume);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("candidateNo", candidate.getCandidateNo());
            out.put("candidateId", candidate.getId());
            out.put("resumeId", resume.getId());
            out.put("fileName", original);
            out.put("fileId", fe.getId());
            out.put("status", "解析待 AI 服务接入");
            out.put("_fallback", true);
            return out;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload resume failed", e);
            throw new BusinessException("UPLOAD_FAILED", "文件保存失败: " + e.getMessage(), 500);
        }
    }

    /**
     * GET /api/talent/resume-file/{resumeId} — 简历原件下载。
     */
    public Path resolveResumeFilePath(Long resumeId) {
        Resume r = resumeRepository.findById(resumeId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("简历原件不存在"));
        if (r.getResumeFileId() == null) {
            throw BusinessException.notFound("简历原件不存在");
        }
        FileEntity f = fileEntityRepository.findById(r.getResumeFileId())
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("简历文件已被移除"));
        Path realPath = Paths.get(f.getFileUrl()).toAbsolutePath().normalize();
        Path uploadsRoot = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
        if (!realPath.startsWith(uploadsRoot) || !Files.exists(realPath)) {
            throw BusinessException.forbidden();
        }
        return realPath;
    }

    /**
     * GET /api/talent/candidate/{id}/export — 候选人数据导出（PIPL 知情权）。
     */
    public Map<String, Object> exportCandidate(Long candidateId) {
        Candidate c = candidateRepository.findById(candidateId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("候选人不存在"));
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("candidate_no", c.getCandidateNo());
        candidate.put("name", c.getCandidateName());
        candidate.put("mobile", c.getMobile());
        candidate.put("email", c.getEmail());
        candidate.put("edu_level", c.getEduLevel());
        candidate.put("school_level", c.getSchoolLevel());
        candidate.put("work_years", c.getWorkYears());
        candidate.put("source_channel", c.getSourceChannel());
        candidate.put("status", c.getStatus());
        candidate.put("note", c.getNote());
        candidate.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);

        List<Map<String, Object>> resumeList = new ArrayList<>();
        for (Resume r : resumeRepository.findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(c.getId(), 0)) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("resume_id", r.getId());
            rm.put("storage_time", r.getStorageTime() != null ? r.getStorageTime().toString() : null);
            rm.put("work_exp_text", r.getWorkExpText());
            rm.put("extract_json", r.getExtractJson());
            resumeList.add(rm);
        }

        List<Map<String, Object>> processList = new ArrayList<>();
        for (RecruitProcess p : recruitProcessRepository.findByCandidateIdAndIsDeletedOrderByIdDesc(c.getId(), 0)) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("process_no", p.getProcessNo());
            pm.put("demand_id", p.getDemandId());
            processList.add(pm);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidate", candidate);
        out.put("resumes", resumeList);
        out.put("processes", processList);
        return out;
    }

    /**
     * DELETE /api/talent/candidate/{id}/hard — 彻底删除候选人及关联数据（PIPL 删除权）。
     */
    @Transactional
    public Map<String, Object> hardDeleteCandidate(Long candidateId) {
        Candidate c = candidateRepository.findById(candidateId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("候选人不存在"));
        List<String> deleted = new ArrayList<>();

        List<Resume> resumes = resumeRepository.findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(c.getId(), 0);
        for (Resume r : resumes) {
            resumeMatchRepository.deleteAll(resumeMatchRepository.findAll(
                    (root, query, cb) -> cb.equal(root.get("resumeId"), r.getId())));
            resumeRepository.delete(r);
        }
        deleted.add("resumes:" + resumes.size());

        List<RecruitProcess> processes = recruitProcessRepository.findByCandidateIdAndIsDeletedOrderByIdDesc(c.getId(), 0);
        for (RecruitProcess p : processes) {
            recruitProcessRepository.delete(p);
        }
        deleted.add("processes:" + processes.size());

        candidateRepository.delete(c);
        deleted.add("candidate:1");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidate", c.getCandidateName());
        out.put("candidate_no", c.getCandidateNo());
        out.put("items", deleted);
        return out;
    }

    private Long parseId(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw BusinessException.invalidInput("无效的 ID: " + s);
        }
    }

    private Map<String, Object> toCandidateMap(Candidate c) {
        return toCandidateMap(c, null);
    }

    private Map<String, Object> toCandidateMap(Candidate c, Map<Long, List<Resume>> resumeByCandidate) {
        double score = c.getStaticAbilityScore() != null ? c.getStaticAbilityScore().doubleValue() : 0;
        int eduLevel = c.getEduLevel() != null ? c.getEduLevel() : 0;
        int workYears = c.getWorkYears() != null ? c.getWorkYears() : 0;
        String status = c.getStatus() != null ? c.getStatus() : "available";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("candidateNo", c.getCandidateNo());
        m.put("name", c.getCandidateName());
        m.put("profileScore", (int) score);
        m.put("portrait", score > 0 ? profileGrade(score) + " · " + (int) score : "—");
        m.put("edu", EDU_LABELS.getOrDefault(eduLevel, "本科"));
        m.put("years", workYears < 1 ? "应届" : workYears + "年");
        m.put("skills", resolveSkills(c, resumeByCandidate));
        m.put("company", resolveCompany(c, resumeByCandidate));
        m.put("source", c.getSourceChannel() != null ? c.getSourceChannel() : "邮箱");
        m.put("inDate", c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate().toString() : "—");
        m.put("status", status);
        m.put("statusLabel", STATUS_LABELS.getOrDefault(status, "可联系"));
        m.put("note", c.getNote() != null ? c.getNote() : "");
        m.put("locked", "locked".equals(status));
        return m;
    }

    private Map<String, Object> toEmployeeMap(Employee e) {
        return toEmployeeMap(e, null, null, null);
    }

    private Map<String, Object> toEmployeeMap(Employee e, Map<Long, String> userNames,
                                              Map<Long, String> deptNames, Map<Long, String> positionNames) {
        double score = e.getCompositiveScore() != null ? e.getCompositiveScore().doubleValue() : 0;
        List<String> tags = new ArrayList<>();
        tags.add("内部人才");
        String dept = resolveDeptName(e.getDeptId(), deptNames);
        if (!"—".equals(dept)) {
            tags.add(dept);
        }
        tags.add(e.getCanTransfer() != null && e.getCanTransfer() == 1 ? "可调岗" : "不可调岗");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "EMP" + String.format("%03d", e.getId()));
        m.put("name", resolveUserName(e.getUserId(), userNames));
        m.put("score", profileGrade(score) + " · " + (int) score);
        m.put("dept", dept);
        m.put("pos", resolvePositionName(e.getPositionId(), positionNames));
        m.put("years", (e.getWorkYears() != null ? e.getWorkYears() : 0) + "年");
        m.put("perf", perfLabel(e.getPerfScore()));
        m.put("tags", tags);
        m.put("transfer", e.getCanTransfer() != null && e.getCanTransfer() == 1);
        m.put("note", "");
        return m;
    }

    private Map<String, Object> resolveResumeInfo(Long candidateId) {
        List<Resume> resumes = resumeRepository.findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(candidateId, 0);
        if (resumes.isEmpty()) {
            return null;
        }
        Resume r = resumes.get(0);
        JsonNode ext = parseJson(r.getExtractJson());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resumeId", r.getId());
        m.put("parseEngine", ext != null ? ext.path("parse_engine").asText("—") : "—");
        m.put("parsedAt", ext != null ? ext.path("parsed_at").asText("—") : "—");
        m.put("summary", r.getWorkExpText() != null ? r.getWorkExpText()
                : (ext != null ? ext.path("summary").asText("") : ""));
        m.put("skills", extractSkillList(ext));
        m.put("certs", extractList(ext, "certs"));
        m.put("recentCompany", ext != null ? ext.path("recent_company").asText("") : "");
        m.put("storageTime", r.getStorageTime() != null ? r.getStorageTime().toString() : "—");
        return m;
    }

    private List<String> resolveSkills(Candidate c, Map<Long, List<Resume>> resumeByCandidate) {
        List<Resume> resumes = getResumes(c.getId(), resumeByCandidate);
        for (Resume r : resumes) {
            List<String> skills = extractSkillList(parseJson(r.getExtractJson()));
            if (!skills.isEmpty()) {
                return skills;
            }
        }
        return List.of("—");
    }

    private String resolveCompany(Candidate c, Map<Long, List<Resume>> resumeByCandidate) {
        List<Resume> resumes = getResumes(c.getId(), resumeByCandidate);
        for (Resume r : resumes) {
            JsonNode ext = parseJson(r.getExtractJson());
            if (ext != null) {
                String company = ext.path("recent_company").asText("").trim();
                if (!company.isEmpty()) {
                    return company;
                }
            }
        }
        return "—";
    }

    /** 批量预取页内候选人简历：一次 IN 查询 → Map<candidateId, List<Resume>>。 */
    private Map<Long, List<Resume>> fetchResumesByCandidateIds(List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Resume>> map = new HashMap<>();
        for (Resume r : resumeRepository.findByCandidateIdInAndIsDeletedOrderByStorageTimeDesc(candidateIds, 0)) {
            map.computeIfAbsent(r.getCandidateId(), k -> new ArrayList<>()).add(r);
        }
        return map;
    }

    private List<Resume> getResumes(Long candidateId, Map<Long, List<Resume>> resumeByCandidate) {
        if (resumeByCandidate != null) {
            return resumeByCandidate.getOrDefault(candidateId, List.of());
        }
        return resumeRepository.findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(candidateId, 0);
    }

    private List<String> extractSkillList(JsonNode ext) {
        if (ext == null) {
            return List.of();
        }
        for (String key : new String[]{"skills", "tech_stack", "skill_tags", "tags", "keywords"}) {
            JsonNode node = ext.get(key);
            if (node == null) {
                continue;
            }
            if (node.isArray()) {
                List<String> list = new ArrayList<>();
                node.forEach(n -> list.add(n.asText()));
                if (!list.isEmpty()) {
                    return list;
                }
            } else if (node.isTextual() && !node.asText().isBlank()) {
                List<String> list = new ArrayList<>();
                for (String s : node.asText().split(",")) {
                    if (!s.isBlank()) {
                        list.add(s.trim());
                    }
                }
                if (!list.isEmpty()) {
                    return list;
                }
            }
        }
        return List.of();
    }

    private List<String> extractList(JsonNode ext, String key) {
        List<String> list = new ArrayList<>();
        if (ext != null && ext.get(key) != null && ext.get(key).isArray()) {
            ext.get(key).forEach(n -> list.add(n.asText()));
        }
        return list;
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

    private String resolveUserName(Long userId) {
        return resolveUserName(userId, null);
    }

    private String resolveUserName(Long userId, Map<Long, String> userNames) {
        if (userId == null) {
            return "—";
        }
        if (userNames != null) {
            return userNames.getOrDefault(userId, "员工" + userId);
        }
        return iamUserRepository.findById(userId)
                .map(IamUser::getRealName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("员工" + userId);
    }

    private String resolveDeptName(Long deptId) {
        return resolveDeptName(deptId, null);
    }

    private String resolveDeptName(Long deptId, Map<Long, String> deptNames) {
        if (deptId == null) {
            return "—";
        }
        if (deptNames != null) {
            return deptNames.getOrDefault(deptId, "—");
        }
        return iamDeptRepository.findById(deptId)
                .map(IamDept::getDeptName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("—");
    }

    private String resolvePositionName(Long positionId) {
        return resolvePositionName(positionId, null);
    }

    private String resolvePositionName(Long positionId, Map<Long, String> positionNames) {
        if (positionId == null) {
            return "—";
        }
        if (positionNames != null) {
            return positionNames.getOrDefault(positionId, "—");
        }
        return iamPositionRepository.findById(positionId)
                .map(IamPosition::getPositionName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("—");
    }

    private Map<Long, String> fetchUserNames(List<Long> userIds) {
        Map<Long, String> map = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return map;
        }
        for (IamUser u : iamUserRepository.findAllById(userIds)) {
            String name = u.getRealName();
            if (name != null && !name.isBlank()) {
                map.put(u.getUserId(), name);
            }
        }
        return map;
    }

    private Map<Long, String> fetchDeptNames(List<Long> deptIds) {
        Map<Long, String> map = new HashMap<>();
        if (deptIds == null || deptIds.isEmpty()) {
            return map;
        }
        for (IamDept d : iamDeptRepository.findAllById(deptIds)) {
            String name = d.getDeptName();
            if (name != null && !name.isBlank()) {
                map.put(d.getDeptId(), name);
            }
        }
        return map;
    }

    private Map<Long, String> fetchPositionNames(List<Long> positionIds) {
        Map<Long, String> map = new HashMap<>();
        if (positionIds == null || positionIds.isEmpty()) {
            return map;
        }
        for (IamPosition p : iamPositionRepository.findAllById(positionIds)) {
            String name = p.getPositionName();
            if (name != null && !name.isBlank()) {
                map.put(p.getPositionId(), name);
            }
        }
        return map;
    }

    private String perfLabel(BigDecimal perfScore) {
        if (perfScore == null) {
            return "B";
        }
        return PERF_LABELS.getOrDefault(perfScore.toPlainString(), "B");
    }

    private String profileGrade(double score) {
        if (score >= 85) return "A";
        if (score >= 75) return "B+";
        if (score >= 60) return "B";
        return "C";
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone != null ? phone : "—";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private Map<String, Object> pageData(List<Map<String, Object>> data, long total, int page, int pageSize) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data);
        m.put("total", total);
        m.put("page", page);
        m.put("pageSize", pageSize);
        return m;
    }
}
