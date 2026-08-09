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
import com.hr.talent.entity.RecruitProcess;
import com.hr.talent.entity.Resume;
import com.hr.talent.entity.ResumeMatch;
import com.hr.talent.repository.CandidateRepository;
import com.hr.talent.repository.EmployeeRepository;
import com.hr.talent.repository.FileEntityRepository;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads";

    /**
     * 候选人列表（分页 + keyword + status 筛选）。
     */
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

        List<Map<String, Object>> list = result.getContent().stream().map(this::toCandidateMap).toList();
        return pageData(list, result.getTotalElements(), page, pageSize);
    }

    /**
     * 内部员工列表（分页，keyword 匹配员工姓名）。
     */
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

        List<Map<String, Object>> list = result.getContent().stream().map(this::toEmployeeMap).toList();
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

    private Map<String, Object> toCandidateMap(Candidate c) {
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
        m.put("skills", resolveSkills(c));
        m.put("company", resolveCompany(c));
        m.put("source", c.getSourceChannel() != null ? c.getSourceChannel() : "邮箱");
        m.put("inDate", c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate().toString() : "—");
        m.put("status", status);
        m.put("statusLabel", STATUS_LABELS.getOrDefault(status, "可联系"));
        m.put("note", c.getNote() != null ? c.getNote() : "");
        m.put("locked", "locked".equals(status));
        return m;
    }

    private Map<String, Object> toEmployeeMap(Employee e) {
        double score = e.getCompositiveScore() != null ? e.getCompositiveScore().doubleValue() : 0;
        List<String> tags = new ArrayList<>();
        tags.add("内部人才");
        String dept = resolveDeptName(e.getDeptId());
        if (!"—".equals(dept)) {
            tags.add(dept);
        }
        tags.add(e.getCanTransfer() != null && e.getCanTransfer() == 1 ? "可调岗" : "不可调岗");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "EMP" + String.format("%03d", e.getId()));
        m.put("name", resolveUserName(e.getUserId()));
        m.put("score", profileGrade(score) + " · " + (int) score);
        m.put("dept", dept);
        m.put("pos", resolvePositionName(e.getPositionId()));
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

    private List<String> resolveSkills(Candidate c) {
        List<Resume> resumes = resumeRepository.findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(c.getId(), 0);
        for (Resume r : resumes) {
            List<String> skills = extractSkillList(parseJson(r.getExtractJson()));
            if (!skills.isEmpty()) {
                return skills;
            }
        }
        return List.of("—");
    }

    private String resolveCompany(Candidate c) {
        List<Resume> resumes = resumeRepository.findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(c.getId(), 0);
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
        if (userId == null) {
            return "—";
        }
        return iamUserRepository.findById(userId)
                .map(IamUser::getRealName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("员工" + userId);
    }

    private String resolveDeptName(Long deptId) {
        if (deptId == null) {
            return "—";
        }
        return iamDeptRepository.findById(deptId)
                .map(IamDept::getDeptName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("—");
    }

    private String resolvePositionName(Long positionId) {
        if (positionId == null) {
            return "—";
        }
        return iamPositionRepository.findById(positionId)
                .map(IamPosition::getPositionName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("—");
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
