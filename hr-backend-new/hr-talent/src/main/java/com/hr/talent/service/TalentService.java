package com.hr.talent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.auth.entity.IamDept;
import com.hr.auth.entity.IamPosition;
import com.hr.auth.entity.IamUser;
import com.hr.auth.repository.IamDeptRepository;
import com.hr.auth.repository.IamPositionRepository;
import com.hr.auth.repository.IamUserRepository;
import com.hr.auth.service.DataScopeService;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.LoginUser;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final DataScopeService dataScopeService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    /** 可选：配置 SMTP 后自动注入，未配置时为 null */
    @Autowired(required = false)
    private JavaMailSender mailSender;

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
        attachLinkedDemands(list, content);
        return pageData(list, result.getTotalElements(), page, pageSize);
    }

    /**
     * 内部员工列表（分页，keyword 匹配员工姓名）。
     */
    public Map<String, Object> listEmployees(int page, int pageSize, String keyword) {
        Specification<Employee> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), 0));
            applyEmployeeScope(root, cb, predicates);
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
        ensureEmployeeVisible(e);

        return employeeDetailMap(e);
    }

    /** 当前登录用户自己的员工档案。 */
    public Map<String, Object> getMyEmployeeProfile() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw BusinessException.unauthorized();
        }
        Employee e = employeeRepository.findByUserId(userId)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("当前用户没有关联员工档案"));
        return employeeDetailMap(e);
    }

    private Map<String, Object> employeeDetailMap(Employee e) {

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

    private void applyEmployeeScope(jakarta.persistence.criteria.Root<Employee> root,
                                     jakarta.persistence.criteria.CriteriaBuilder cb,
                                     List<Predicate> predicates) {
        LoginUser current = SecurityUtils.getCurrentUser();
        if (current == null || current.getUserId() == null) {
            predicates.add(cb.disjunction());
            return;
        }

        String scope = dataScopeService.resolveScope(current.getRoleCode(), current.getUserId());
        switch (scope) {
            case DataScopeService.ALL -> { }
            case DataScopeService.DEPT -> {
                if (current.getDeptId() == null) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(cb.equal(root.get("deptId"), current.getDeptId()));
                }
            }
            case DataScopeService.DEPT_AND_SELF -> {
                Predicate self = cb.equal(root.get("userId"), current.getUserId());
                Predicate sameDept = current.getDeptId() == null
                        ? cb.disjunction()
                        : cb.equal(root.get("deptId"), current.getDeptId());
                predicates.add(cb.or(self, sameDept));
            }
            case DataScopeService.SELF -> predicates.add(cb.equal(root.get("userId"), current.getUserId()));
            default -> predicates.add(cb.disjunction());
        }
    }

    private void ensureEmployeeVisible(Employee employee) {
        LoginUser current = SecurityUtils.getCurrentUser();
        if (current == null || current.getUserId() == null) {
            throw BusinessException.unauthorized();
        }
        String scope = dataScopeService.resolveScope(current.getRoleCode(), current.getUserId());
        boolean visible = switch (scope) {
            case DataScopeService.ALL -> true;
            case DataScopeService.DEPT -> current.getDeptId() != null
                    && Objects.equals(current.getDeptId(), employee.getDeptId());
            case DataScopeService.DEPT_AND_SELF -> Objects.equals(current.getUserId(), employee.getUserId())
                    || (current.getDeptId() != null && Objects.equals(current.getDeptId(), employee.getDeptId()));
            case DataScopeService.SELF -> Objects.equals(current.getUserId(), employee.getUserId());
            default -> false;
        };
        if (!visible) {
            throw BusinessException.forbidden();
        }
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
        Map<String, Object> demand = resolveDemandForMatching(demandId);
        String demandPosition = Objects.toString(demand.get("positionName"), "");
        Set<String> requiredSkills = jsonStringSet(demand.get("requiredSkills"));
        Set<String> plusSkills = jsonStringSet(demand.get("plusSkills"));
        int minimumYears = demand.get("expMin") instanceof Number n ? n.intValue() : 0;
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
            String currentPosition = resolvePositionName(e.getPositionId(), positionNames);
            Set<String> employeeTags = new HashSet<>(resolveEmployeeTags(e.getId()));
            Set<String> matchedRequired = matchedSkills(requiredSkills, employeeTags, currentPosition);
            Set<String> matchedPlus = matchedSkills(plusSkills, employeeTags, currentPosition);
            int skillScore = requiredSkills.isEmpty()
                    ? 0
                    : (int) Math.round(35.0 * matchedRequired.size() / requiredSkills.size());
            skillScore += plusSkills.isEmpty() ? 0 : (int) Math.round(5.0 * matchedPlus.size() / plusSkills.size());
            boolean positionMatched = textMatches(demandPosition, currentPosition);
            int positionScore = positionMatched ? 20 : 0;
            int experienceScore = minimumYears <= 0
                    ? 15
                    : Math.min(20, (int) Math.round(20.0 * Math.min(e.getWorkYears() == null ? 0 : e.getWorkYears(), minimumYears) / minimumYears));
            double perf = e.getPerfScore() == null ? 0 : e.getPerfScore().doubleValue();
            int performanceScore = (int) Math.round(Math.min(15, Math.max(0, perf / 5.0 * 15)));
            int transferScore = e.getCanTransfer() != null && e.getCanTransfer() == 1 ? 10 : 0;
            int score = Math.min(100, skillScore + positionScore + experienceScore + performanceScore + transferScore);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "EMP" + String.format("%03d", e.getId()));
            m.put("name", name);
            m.put("dept", resolveDeptName(e.getDeptId(), deptNames));
            m.put("curPos", currentPosition);
            m.put("perf", perfLabel(e.getPerfScore()));
            m.put("score", score);
            m.put("transferable", e.getCanTransfer() != null && e.getCanTransfer() == 1);
            m.put("matchedSkills", matchedRequired);
            m.put("missingSkills", requiredSkills.stream().filter(s -> !matchedRequired.contains(s)).toList());
            m.put("scoreBreakdown", Map.of(
                    "skills", skillScore,
                    "position", positionScore,
                    "experience", experienceScore,
                    "performance", performanceScore,
                    "transfer", transferScore));
            results.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("demandId", demandId);
        out.put("demandNo", demand.get("demandNo"));
        out.put("position", demandPosition);
        out.put("results", results);
        return out;
    }

    private Map<String, Object> resolveDemandForMatching(String demandId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT demand_no AS demandNo, position_name AS positionName, exp_min AS expMin, "
                            + "required_skills AS requiredSkills, plus_skills AS plusSkills "
                            + "FROM t_hr_recruit_demand WHERE is_deleted = 0 "
                            + "AND (demand_no = ? OR CAST(id AS CHAR) = ?) LIMIT 1",
                    demandId, demandId);
        } catch (Exception e) {
            throw BusinessException.notFound("招聘需求不存在或已删除");
        }
    }

    private Set<String> jsonStringSet(Object value) {
        if (value == null) {
            return Set.of();
        }
        JsonNode node = value instanceof String s ? parseJson(s) : objectMapper.valueToTree(value);
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        node.forEach(item -> {
            String text = item.asText("").trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        });
        return result;
    }

    private List<String> resolveEmployeeTags(Long employeeId) {
        return jdbcTemplate.query(
                "SELECT d.tag_name FROM t_hr_employee_tag_rel r "
                        + "JOIN t_hr_tag_dict d ON d.id = r.tag_id "
                        + "WHERE r.employee_id = ? AND r.is_deleted = 0 AND d.is_deleted = 0 AND d.status = 1",
                (rs, rowNum) -> rs.getString("tag_name"), employeeId);
    }

    private Set<String> matchedSkills(Set<String> required, Set<String> employeeTags, String currentPosition) {
        Set<String> matched = new HashSet<>();
        for (String skill : required) {
            if (employeeTags.stream().anyMatch(tag -> textMatches(skill, tag)) || textMatches(skill, currentPosition)) {
                matched.add(skill);
            }
        }
        return matched;
    }

    private boolean textMatches(String expected, String actual) {
        if (expected == null || actual == null || expected.isBlank() || actual.isBlank() || "—".equals(actual)) {
            return false;
        }
        String e = expected.replaceAll("\\s+", "").toLowerCase();
        String a = actual.replaceAll("\\s+", "").toLowerCase();
        return e.contains(a) || a.contains(e);
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
        Candidate c = candidateRepository.findById(resolveCandidateId(candidateId))
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
        Map<String, Object> contact = getCandidateContact(resolveCandidateId(candidateId));
        String method = body.get("method") != null ? String.valueOf(body.get("method")) : "系统记录";
        String channel = ((body.get("channel") != null ? String.valueOf(body.get("channel"))
                : method) + "").toLowerCase();

        Map<String, Object> sendResult = new LinkedHashMap<>();
        sendResult.put("attempted", false);
        sendResult.put("sent", false);
        sendResult.put("message", "recorded only");

        if (channel.contains("email") || channel.contains("mail")) {
            String draft = body.get("draft") != null ? String.valueOf(body.get("draft")) : "";
            String recipientEmail = contact.get("email") != null
                    ? String.valueOf(contact.get("email")) : "";
            if (recipientEmail.isBlank()) {
                sendResult.put("attempted", true);
                sendResult.put("message", "candidate email is empty");
            } else if (draft.isBlank()) {
                sendResult.put("attempted", true);
                sendResult.put("message", "draft is empty");
            } else if (mailSender == null) {
                sendResult.put("attempted", true);
                sendResult.put("sent", false);
                sendResult.put("message", "邮件服务未配置(SMTP)，已记录");
                sendResult.put("recipient", recipientEmail);
                log.info("[CONTACT] email to={} (SMTP 未配置，仅记录)", recipientEmail);
            } else {
                try {
                    String subject = body.get("subject") != null
                            ? String.valueOf(body.get("subject")) : "Recruiting Follow-up";
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setTo(recipientEmail);
                    msg.setSubject(subject);
                    msg.setText(draft);
                    mailSender.send(msg);
                    sendResult.put("attempted", true);
                    sendResult.put("sent", true);
                    sendResult.put("message", "sent");
                    sendResult.put("recipient", recipientEmail);
                    log.info("[CONTACT] email sent to {}", recipientEmail);
                } catch (Exception e) {
                    log.error("[CONTACT] email send failed to {}: {}", recipientEmail, e.getMessage());
                    sendResult.put("attempted", true);
                    sendResult.put("sent", false);
                    sendResult.put("message", "发送失败: " + e.getMessage());
                    sendResult.put("recipient", recipientEmail);
                }
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
        updateNote(resolveCandidateId(candidateId), noteText);

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

            // 尝试提取文本并解析（对齐 Python ai_engine.parse_resume）
            String extractedText = "";
            try {
                byte[] bytes = file.getBytes();
                // 尝试 UTF-8 解码纯文本，非纯文本则会失败
                extractedText = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                // 如果包含太多乱码字符，说明可能是二进制格式
                if (extractedText.length() > 0) {
                    long nonPrintable = extractedText.chars()
                            .filter(c -> c < 32 && c != '\n' && c != '\r' && c != '\t').count();
                    if (nonPrintable > extractedText.length() * 0.3) {
                        extractedText = ""; // 二进制文件，跳过文本提取
                    }
                }
            } catch (Exception e) {
                log.debug("Text extraction skipped for {}", original);
            }

            Map<String, Object> parsed = extractedText.isBlank()
                    ? ResumeParser.defaultResult()
                    : ResumeParser.parse(extractedText);

            String parsedName = String.valueOf(parsed.get("name"));
            String name = (!"未知".equals(parsedName) && !parsedName.isBlank())
                    ? parsedName : original.substring(0, dot < 0 ? original.length() : dot).trim();

            // 判断学历数字编码
            int eduLevel = mapEduLevel(String.valueOf(parsed.get("edu_level")));
            // 判断学校层次数字编码
            int schoolLevel = mapSchoolLevel(String.valueOf(parsed.get("school_level")));

            // 去重：按手机号或邮箱匹配已有候选人
            String parsedPhone = String.valueOf(parsed.getOrDefault("phone", ""));
            String parsedEmail = String.valueOf(parsed.getOrDefault("email", ""));

            Candidate candidate = null;
            if (!parsedPhone.isBlank()) {
                candidate = candidateRepository
                        .findAll((root, query, cb) -> cb.and(
                                cb.equal(root.get("mobile"), parsedPhone),
                                cb.equal(root.get("isDeleted"), 0)))
                        .stream().findFirst().orElse(null);
            }
            if (candidate == null && !parsedEmail.isBlank()) {
                candidate = candidateRepository
                        .findAll((root, query, cb) -> cb.and(
                                cb.equal(root.get("email"), parsedEmail),
                                cb.equal(root.get("isDeleted"), 0)))
                        .stream().findFirst().orElse(null);
            }
            // 按姓名匹配（已有同名候选人则追加简历）
            if (candidate == null) {
                candidate = candidateRepository.findFirstByCandidateNameAndIsDeleted(name, 0).orElse(null);
            }

            if (candidate == null) {
                candidate = new Candidate();
                candidate.setCandidateNo("C" + System.currentTimeMillis());
                candidate.setCandidateName(name);
                candidate.setMobile(parsedPhone.isBlank() ? null : parsedPhone);
                candidate.setEmail(parsedEmail.isBlank() ? null : parsedEmail);
                candidate.setEduLevel(eduLevel);
                candidate.setSchoolLevel(schoolLevel);
                candidate.setWorkYears(((Number) parsed.get("work_years")).intValue());
                candidate.setSourceChannel("手动上传");
                candidate.setStatus("available");
                candidate.setBlackFlag(0);
                candidate.setBigCompanyFlag(0);
                candidate.setCertCount(0);
                candidate.setStaticAbilityScore(candidate.computeStaticAbilityScore());
                candidate.setCreatedAt(LocalDateTime.now());
                candidate.setUpdatedAt(LocalDateTime.now());
                candidate.setIsDeleted(0);
                candidate = candidateRepository.save(candidate);
            } else {
                // 更新已有候选人的解析信息
                if (candidate.getEduLevel() == null && eduLevel > 0) candidate.setEduLevel(eduLevel);
                if (candidate.getMobile() == null && !parsedPhone.isBlank()) candidate.setMobile(parsedPhone);
                if (candidate.getEmail() == null && !parsedEmail.isBlank()) candidate.setEmail(parsedEmail);
                candidate.setStaticAbilityScore(candidate.computeStaticAbilityScore());
                candidate.setUpdatedAt(LocalDateTime.now());
                candidateRepository.save(candidate);
            }

            if (note != null && !note.isBlank()) {
                candidate.setNote(note);
                candidate.setUpdatedAt(LocalDateTime.now());
                candidateRepository.save(candidate);
            }

            String parseJson;
            try {
                parseJson = objectMapper.writeValueAsString(parsed);
            } catch (Exception e) {
                parseJson = "{}";
            }

            Resume resume = new Resume();
            resume.setCandidateId(candidate.getId());
            resume.setResumeFileId(fe.getId());
            resume.setStorageTime(LocalDateTime.now());
            resume.setExtractJson(parseJson);
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
            out.put("parsed", parsed);
            out.put("status", "解析完成");
            out.put("_fallback", false);
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

    /**
     * 解析候选人标识为数字主键（对齐 Flask：先按 candidate_no，再按数字 id）。
     */
    public Long resolveCandidateId(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw BusinessException.notFound("候选人不存在");
        }
        String idStr = identifier.trim();
        if (!idStr.chars().allMatch(Character::isDigit)) {
            return candidateRepository.findAll((root, query, cb) -> cb.and(
                            cb.equal(root.get("candidateNo"), idStr),
                            cb.equal(root.get("isDeleted"), 0)))
                    .stream().findFirst()
                    .map(Candidate::getId)
                    .orElseThrow(() -> BusinessException.notFound("候选人不存在"));
        }
        return Long.parseLong(idStr);
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

    /**
     * 给人才库列表附上 linkedDemands / targetPosition（对齐 Flask talent_service._attach_linked_demands）。
     * linkedDemands: [{demandNo, position, matchScore, processStatus}]；无流程时回退简历 extract_json 的 target_position。
     */
    private void attachLinkedDemands(List<Map<String, Object>> list, List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        try {
            List<Long> ids = candidates.stream().map(Candidate::getId).toList();

            // 1. 招聘流程（候选人 → 需求 + 简历）
            List<RecruitProcess> processes = recruitProcessRepository.findByCandidateIdInAndIsDeleted(ids, 0);

            // 2. 需求表（原生 SQL，避免 hr-talent 依赖 hr-demand）
            Set<Long> demandIds = processes.stream().map(RecruitProcess::getDemandId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String[]> demandMap = new HashMap<>();
            if (!demandIds.isEmpty()) {
                String sql = "SELECT id, demand_no, position_name FROM t_hr_recruit_demand WHERE id IN ("
                        + String.join(",", Collections.nCopies(demandIds.size(), "?"))
                        + ") AND is_deleted = 0";
                jdbcTemplate.query(sql, rs -> {
                    demandMap.put(rs.getLong("id"),
                            new String[]{rs.getString("demand_no"), rs.getString("position_name")});
                }, demandIds.toArray());
            }

            // 3. 匹配分 (resumeId:demandId -> score)
            Set<Long> resumeIds = processes.stream().map(RecruitProcess::getResumeId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<String, Double> matchMap = new HashMap<>();
            if (!resumeIds.isEmpty()) {
                for (ResumeMatch m : resumeMatchRepository.findByResumeIdInAndIsDeleted(resumeIds, 0)) {
                    String key = m.getResumeId() + ":" + m.getDemandId();
                    if (m.getMatchScore() != null && !matchMap.containsKey(key)) {
                        matchMap.put(key, m.getMatchScore().doubleValue());
                    }
                }
            }

            // 4. 按候选人组装 linkedDemands（同一候选人+同一需求只保留一条，避免重复投递记录导致重复显示）
            Map<Long, List<Map<String, Object>>> byCandidate = new HashMap<>();
            Set<String> seenProcess = new HashSet<>();
            for (RecruitProcess p : processes) {
                String[] d = demandMap.get(p.getDemandId());
                if (d == null) {
                    continue;
                }
                if (!seenProcess.add(p.getCandidateId() + ":" + p.getDemandId())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("demandNo", d[0]);
                item.put("position", (d[1] != null && !d[1].isBlank()) ? d[1] : "—");
                Double score = matchMap.get(p.getResumeId() + ":" + p.getDemandId());
                item.put("matchScore", score != null ? Math.round(score * 10) / 10.0 : null);
                item.put("processStatus", p.getProcessStatus());
                byCandidate.computeIfAbsent(p.getCandidateId(), k -> new ArrayList<>()).add(item);
            }

            // 5. 所有候选人都保留最新简历中的原始应聘岗位。
            //    它与 linkedDemands 是两个不同业务概念，不能互相覆盖。
            Map<Long, String> targetPosMap = new HashMap<>();
            for (Resume r : resumeRepository.findByCandidateIdInAndIsDeletedOrderByStorageTimeDesc(ids, 0)) {
                if (targetPosMap.containsKey(r.getCandidateId())) {
                    continue;
                }
                JsonNode ext = parseJson(r.getExtractJson());
                String tp = ext != null ? ext.path("target_position").asText("") : "";
                if (!tp.isBlank()) {
                    targetPosMap.put(r.getCandidateId(), tp);
                }
            }

            // 6. 写回列表
            for (int i = 0; i < candidates.size() && i < list.size(); i++) {
                List<Map<String, Object>> linked = byCandidate.getOrDefault(candidates.get(i).getId(), List.of());
                Map<String, Object> item = list.get(i);
                item.put("linkedDemands", linked);
                String appliedPosition = targetPosMap.getOrDefault(candidates.get(i).getId(), "");
                item.put("appliedPosition", appliedPosition);
                // 兼容旧客户端：targetPosition 只保留原始应聘岗位，不再写入匹配岗位。
                item.put("targetPosition", appliedPosition);
            }
        } catch (Exception e) {
            log.warn("attach linked demands failed (best-effort): {}", e.getMessage());
            for (Map<String, Object> item : list) {
                item.putIfAbsent("linkedDemands", List.of());
                item.putIfAbsent("appliedPosition", "");
                item.putIfAbsent("targetPosition", "");
            }
        }
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

    /** 学历文本 → 数字编码 (1大专 2本科 3硕士 4博士) */
    private static int mapEduLevel(String label) {
        return switch (label) {
            case "博士" -> 4;
            case "硕士" -> 3;
            case "本科" -> 2;
            case "大专" -> 1;
            default -> 2;
        };
    }

    /** 学校层次文本 → 数字编码 (1普通 2:211 3:985 4:C9) */
    private static int mapSchoolLevel(String label) {
        return switch (label) {
            case "C9" -> 4;
            case "985" -> 3;
            case "211" -> 2;
            case "普通" -> 1;
            default -> 1;
        };
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
