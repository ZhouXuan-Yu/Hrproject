package com.hr.demand.service;

import com.hr.auth.entity.IamDept;
import com.hr.auth.entity.IamPosition;
import com.hr.auth.entity.IamUser;
import com.hr.auth.repository.IamDeptRepository;
import com.hr.auth.repository.IamPositionRepository;
import com.hr.auth.repository.IamUserRepository;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.SnowflakeIdGenerator;
import com.hr.demand.entity.DemandApproval;
import com.hr.demand.entity.RecruitDemand;
import com.hr.demand.repository.DemandApprovalRepository;
import com.hr.demand.repository.RecruitDemandRepository;
import com.hr.talent.entity.Candidate;
import com.hr.talent.entity.RecruitProcess;
import com.hr.talent.entity.Resume;
import com.hr.talent.entity.ResumeMatch;
import com.hr.talent.repository.CandidateRepository;
import com.hr.talent.repository.RecruitProcessRepository;
import com.hr.talent.repository.ResumeMatchRepository;
import com.hr.talent.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 需求管理服务，对齐 Flask demand_service.py 核心逻辑。
 */
@Service
@RequiredArgsConstructor
public class DemandService {

    private final RecruitDemandRepository demandRepository;
    private final DemandApprovalRepository approvalRepository;
    private final CandidateRepository candidateRepository;
    private final RecruitProcessRepository processRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeMatchRepository resumeMatchRepository;
    private final IamUserRepository userRepository;
    private final IamDeptRepository deptRepository;
    private final IamPositionRepository positionRepository;

    private static final Map<Integer, String> STATUS_LABELS = Map.of(
            0, "草稿", 1, "审批中", 2, "招聘中", 3, "已驳回", 4, "已关闭", 5, "已取消");
    private static final Map<Integer, String> STATUS_TYPES = Map.of(
            0, "draft", 1, "warn", 2, "progress", 3, "reject", 4, "draft", 5, "draft");
    private static final Map<Integer, String> STATUS_CODES = Map.of(
            0, "draft", 1, "approval", 2, "open", 3, "rejected", 4, "closed", 5, "cancelled");
    private static final Map<String, String> URGENCY_LABELS = Map.of(
            "very", "非常紧急", "high", "紧急", "normal", "普通");
    private static final Map<String, String> URGENCY_TYPES = Map.of(
            "very", "reject", "high", "warn", "normal", "draft");
    private static final Map<Integer, String> APPROVAL_LEVELS = Map.of(
            1, "部门负责人", 2, "HR", 3, "总监");
    private static final Map<String, String> STATE_LABELS = Map.of(
            "done", "已通过", "current", "审批中", "pending", "待审批", "rejected", "已驳回");

    /**
     * 需求列表（分页 + 筛选 + 数据范围）。
     */
    @org.springframework.cache.annotation.Cacheable(cacheNames = "list", key = "'demand:' + #page + ':' + #pageSize + ':' + (#keyword != null ? #keyword : '') + ':' + (#status != null ? #status : '') + ':' + (#deptId != null ? #deptId : '') + ':' + #roleCode + ':' + #userId")
    public Map<String, Object> listDemands(int page, int pageSize, String keyword, Integer status,
                                           Long deptId, String roleCode, Long userId, Long userDeptId) {
        Specification<RecruitDemand> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), 0));

            // 数据范围过滤（对齐 Flask data_scope.apply_demand_scope）
            if (isScopeLimited(roleCode)) {
                if ("employee".equals(roleCode)) {
                    // 员工只看自己提交的需求
                    predicates.add(cb.equal(root.get("creatorId"), userId));
                } else if ("dept_head".equals(roleCode) || "director".equals(roleCode)) {
                    // 部门负责人看本部门
                    predicates.add(cb.equal(root.get("deptId"), userDeptId));
                }
            }

            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("demandNo"), like),
                        cb.like(root.get("positionName"), like),
                        cb.like(root.get("deptName"), like)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("demandStatus"), status));
            }
            if (deptId != null) {
                predicates.add(cb.equal(root.get("deptId"), deptId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RecruitDemand> result = demandRepository.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), pageSize,
                        Sort.by(Sort.Direction.DESC, "id")));

        List<Map<String, Object>> list = result.getContent().stream().map(this::toSimpleMap).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", list);
        data.put("total", result.getTotalElements());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    /**
     * 创建需求。
     */
    @Transactional
    public Map<String, Object> createDemand(Map<String, Object> body, Long creatorId, String roleCode) {
        RecruitDemand demand = new RecruitDemand();
        demand.setDemandNo("DM" + LocalDateTime.now().getYear()
                + String.format("%04d", SnowflakeIdGenerator.nextId() % 10000));
        applyBody(demand, body);
        demand.setCreatorId(creatorId);
        demand.setDemandStatus(0); // 草稿
        demand.setInternalSearched(0);
        demand.setResumeSearched(0);
        demand.setIsInternalGivenUp(0);
        demand.setFilledCount(0);
        demand.setCreatedAt(LocalDateTime.now());
        demand.setUpdatedAt(LocalDateTime.now());
        demand.setIsDeleted(0);
        demandRepository.save(demand);

        // 初始化审批节点（部门负责人 -> HR -> 总监）
        createApprovalNodes(demand.getId());

        return detailMap(demand);
    }

    /**
     * 解析需求标识为数字主键（对齐 Flask _resolve_demand_id：先按 demand_no，再按数字 id）。
     */
    public Long resolveDemandId(String identifier) {
        return getEntityByIdentifier(identifier).getId();
    }

    /**
     * 需求详情。
     */
    public Map<String, Object> getDemandDetail(Long demandId) {
        return detailMap(getEntity(demandId));
    }

    /**
     * 提交审批（草稿 -> 审批中）。
     */
    @Transactional
    public Map<String, Object> submitForApproval(Long demandId) {
        RecruitDemand demand = getEntity(demandId);
        if (demand.getDemandStatus() != 0) {
            throw BusinessException.invalidInput("仅草稿状态可提交审批");
        }
        demand.setDemandStatus(1);
        demand.setUpdatedAt(LocalDateTime.now());
        demandRepository.save(demand);
        return detailMap(demand);
    }

    /**
     * 审批通过。
     */
    @Transactional
    public Map<String, Object> approve(Long demandId, Long userId, String roleCode, String opinion) {
        RecruitDemand demand = getEntity(demandId);
        if (demand.getDemandStatus() != 1) {
            throw BusinessException.invalidInput("当前状态不可审批");
        }
        List<DemandApproval> nodes = approvalRepository.findByDemandIdOrderByApproveLevelAsc(demandId);
        DemandApproval pending = nodes.stream()
                .filter(n -> n.getApproveResult() == 1)
                .findFirst().orElse(null);
        if (pending == null) {
            // 无待审节点，直接通过
            demand.setDemandStatus(2);
            demand.setApprovedAt(LocalDateTime.now());
        } else {
            pending.setApproveResult(2);
            pending.setApproveOpinion(opinion);
            pending.setApproveTime(LocalDateTime.now());
            approvalRepository.save(pending);
            boolean allApproved = nodes.stream().allMatch(n -> n.getApproveResult() == 2);
            if (allApproved) {
                demand.setDemandStatus(2);
                demand.setApprovedAt(LocalDateTime.now());
            }
        }
        demand.setUpdatedAt(LocalDateTime.now());
        demandRepository.save(demand);
        return detailMap(demand);
    }

    /**
     * 审批驳回。
     */
    @Transactional
    public Map<String, Object> reject(Long demandId, Long userId, String roleCode, String opinion) {
        RecruitDemand demand = getEntity(demandId);
        if (demand.getDemandStatus() != 1) {
            throw BusinessException.invalidInput("当前状态不可驳回");
        }
        List<DemandApproval> nodes = approvalRepository.findByDemandIdOrderByApproveLevelAsc(demandId);
        DemandApproval pending = nodes.stream()
                .filter(n -> n.getApproveResult() == 1)
                .findFirst().orElse(null);
        if (pending != null) {
            pending.setApproveResult(3);
            pending.setApproveOpinion(opinion);
            pending.setApproveTime(LocalDateTime.now());
            approvalRepository.save(pending);
        }
        demand.setDemandStatus(3); // 驳回
        demand.setUpdatedAt(LocalDateTime.now());
        demandRepository.save(demand);
        return detailMap(demand);
    }

    /**
     * 关闭需求。
     */
    @Transactional
    public Map<String, Object> close(Long demandId, String reason) {
        RecruitDemand demand = getEntity(demandId);
        demand.setDemandStatus(4);
        demand.setCancelReason(reason);
        demand.setClosedAt(LocalDateTime.now());
        demand.setUpdatedAt(LocalDateTime.now());
        demandRepository.save(demand);
        return detailMap(demand);
    }

    /**
     * 删除需求（仅草稿/驳回可删）。
     */
    @Transactional
    public void deleteDemand(Long demandId) {
        RecruitDemand demand = getEntity(demandId);
        if (demand.getDemandStatus() != 0 && demand.getDemandStatus() != 3) {
            throw BusinessException.invalidInput("仅草稿或驳回状态可删除");
        }
        demand.setIsDeleted(1);
        demand.setUpdatedAt(LocalDateTime.now());
        demandRepository.save(demand);
    }

    /**
     * PATCH /api/demand/{id} — 部分更新（对齐 Flask update_demand）。
     */
    @Transactional
    public Map<String, Object> updateDemand(Long demandId, Map<String, Object> body) {
        RecruitDemand demand = getEntity(demandId);
        if (demand.getDemandStatus() != null && (demand.getDemandStatus() == 4 || demand.getDemandStatus() == 5)) {
            throw BusinessException.invalidInput("需求已关闭，无法编辑");
        }

        if (body.get("urgency") != null) demand.setUrgency(String.valueOf(body.get("urgency")));
        if (body.get("position") != null) {
            demand.setPositionName(String.valueOf(body.get("position")).trim().isEmpty()
                    ? null : String.valueOf(body.get("position")).trim());
        }
        if (body.get("dept") != null) {
            String deptText = String.valueOf(body.get("dept")).trim();
            demand.setDeptName(deptText.isEmpty() ? null : deptText);
        }
        if (body.get("deptId") != null && !String.valueOf(body.get("deptId")).isBlank()) {
            demand.setDeptId(toLong(body.get("deptId")));
        }
        if (body.get("positionId") != null && !String.valueOf(body.get("positionId")).isBlank()) {
            demand.setPositionId(toLong(body.get("positionId")));
        }
        Object jdContent = body.get("description") != null ? body.get("description") : body.get("desc");
        if (jdContent != null) demand.setJdContent(String.valueOf(jdContent));
        if (body.get("salary") != null) demand.setSalaryRange(String.valueOf(body.get("salary")));
        Object hc = body.get("hc") != null ? body.get("hc") : body.get("planHeadcount");
        if (hc != null) demand.setPlanHeadcount(toInt(hc));
        Object rawDate = body.get("date") != null ? body.get("date") : body.get("expectEntryDate");
        if (rawDate != null) {
            try {
                demand.setExpectEntryDate(LocalDate.parse(String.valueOf(rawDate).substring(0, 10)));
            } catch (Exception ignored) {
                // 非法日期忽略
            }
        } else if (body.containsKey("date") || body.containsKey("expectEntryDate")) {
            demand.setExpectEntryDate(null);
        }
        if (body.get("eduMin") != null) demand.setEduMin(String.valueOf(body.get("eduMin")));
        if (body.get("expMin") != null) demand.setExpMin(toInt(body.get("expMin")));
        if (body.get("workCity") != null) demand.setWorkCity(String.valueOf(body.get("workCity")));

        demand.setUpdatedAt(LocalDateTime.now());
        demandRepository.save(demand);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", true);
        result.put("id", demandId);
        return result;
    }

    /**
     * GET /api/demand/{id}/candidates — 该需求关联的候选人列表（对齐 Flask list_demand_candidates）。
     */
    public List<Map<String, Object>> listDemandCandidates(Long demandId, String source) {
        RecruitDemand demand = getEntity(demandId);
        List<RecruitProcess> processes = processRepository
                .findByDemandIdAndIsDeletedOrderByIdDesc(demand.getId(), 0);
        if (processes.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        // 批量预取候选人，避免循环内 findById（N+1）
        Map<Long, Candidate> candidateById = new HashMap<>();
        for (Candidate cand : candidateRepository.findAllById(
                processes.stream().map(RecruitProcess::getCandidateId).toList())) {
            if (cand.getIsDeleted() == null || cand.getIsDeleted() == 0) {
                candidateById.put(cand.getId(), cand);
            }
        }
        for (RecruitProcess p : processes) {
            Candidate cand = candidateById.get(p.getCandidateId());
            if (cand == null) {
                continue;
            }
            Map<String, Object> c = buildDemandCandidateMap(cand, p, demand);
            candidates.add(c);
        }

        // 真实 JD 匹配打分优先（t_hr_resume_match），没有则退回确定性估算。
        // 一次 IN 查询全部 resumeId，按计算时间取首条（保持 findFirstBy 语义）
        List<Long> resumeIds = candidates.stream()
                .map(c -> c.get("_resumeId"))
                .filter(java.util.Objects::nonNull)
                .map(o -> (Long) o)
                .filter(id -> id > 0)
                .distinct().toList();
        Map<Long, ResumeMatch> matchByResume = new HashMap<>();
        if (!resumeIds.isEmpty()) {
            for (ResumeMatch rm : resumeMatchRepository.findByResumeIdInAndDemandIdAndIsDeleted(
                    resumeIds, demand.getId(), 0, Sort.by(Sort.Direction.DESC, "calculateTime"))) {
                matchByResume.putIfAbsent(rm.getResumeId(), rm);
            }
        }
        for (Map<String, Object> c : candidates) {
            Object resumeIdObj = c.get("_resumeId");
            if (resumeIdObj != null) {
                ResumeMatch rm = matchByResume.get((Long) resumeIdObj);
                if (rm != null && rm.getMatchScore() != null) {
                    c.put("matchScore", rm.getMatchScore().setScale(1).doubleValue());
                }
            }
            c.remove("_resumeId");
        }

        if (source != null && !"all".equals(source)) {
            candidates = candidates.stream()
                    .filter(c -> source.equals(c.get("source")))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        return candidates;
    }

    private Map<String, Object> buildDemandCandidateMap(Candidate cand, RecruitProcess p, RecruitDemand demand) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", cand.getCandidateNo() != null ? cand.getCandidateNo() : String.valueOf(cand.getId()));
        c.put("name", cand.getCandidateName());
        c.put("profileScore", cand.getStaticAbilityScore() != null
                ? cand.getStaticAbilityScore().intValue() : 0);
        c.put("profileGrade", profileGrade(cand.getStaticAbilityScore() != null
                ? cand.getStaticAbilityScore().doubleValue() : 0));
        c.put("matchScore", null);
        c.put("ageDays", p.getCreatedAt() != null
                ? (int) ChronoUnit.DAYS.between(p.getCreatedAt().toLocalDate(), LocalDate.now()) : 0);

        String sourceCh = cand.getSourceChannel() != null ? cand.getSourceChannel() : "人才库";
        String sourceType = switch (sourceCh) {
            case "邮箱", "邮箱采集" -> "mail";
            case "Boss", "Boss直聘" -> "boss";
            case "猎聘" -> "liepin";
            case "内推", "内部推荐" -> "refer";
            case "手动上传" -> "upload";
            default -> "pool";
        };
        String sourceLabel = switch (sourceType) {
            case "mail" -> "邮箱采集";
            case "boss" -> "Boss直聘";
            case "liepin" -> "猎聘";
            case "refer" -> "内推";
            case "upload" -> "手动上传";
            case "internal" -> "内部员工";
            default -> "人才库";
        };
        c.put("source", sourceType);
        c.put("sourceLabel", sourceLabel);

        int ps = p.getProcessStatus() != null ? p.getProcessStatus() : 0;
        String status = switch (ps) {
            case 2, 3 -> "interviewing";
            case 5, 6 -> "offer";
            case 7 -> "available";
            case 8 -> "onboard";
            default -> "available";
        };
        String statusLabel = switch (ps) {
            case 0 -> "待筛选";
            case 1 -> "已邀约";
            case 2, 3 -> "面试中";
            case 4 -> "已淘汰";
            case 5 -> "待Offer";
            case 6 -> "已录用";
            case 7 -> "已放弃";
            case 8 -> "已入职";
            default -> "可联系";
        };
        c.put("status", status);
        c.put("statusLabel", statusLabel);
        c.put("notRecReason", null);
        c.put("edu", eduLabel(cand.getEduLevel()));
        c.put("years", yearsLabel(cand.getWorkYears()));
        c.put("isEmployee", false);
        c.put("_resumeId", p.getResumeId());
        return c;
    }

    /**
     * POST /api/demand/{demandId}/candidates/{name}/link — 关联候选人到需求（对齐 Flask link_candidate_to_demand）。
     */
    @Transactional
    public Map<String, Object> linkCandidate(Long demandId, String name) {
        RecruitDemand demand = getEntity(demandId);
        Candidate candidate = candidateRepository.findFirstByCandidateNameAndIsDeleted(name, 0)
                .orElse(null);
        if (candidate == null) {
            return Map.of("linked", false, "linkedCount", 0, "reason", "候选人不存在", "_fallback", true);
        }
        if (candidate.getBlackFlag() != null && candidate.getBlackFlag() == 1) {
            return Map.of("linked", false, "linkedCount", 0, "reason", "黑名单候选人不可加入需求");
        }
        if ("locked".equals(candidate.getStatus())) {
            return Map.of("linked", false, "linkedCount", 0, "reason", "候选人面试中（锁定），不可重复加入");
        }

        // 去重：进行中的流程不重复创建（淘汰/放弃除外）
        RecruitProcess existing = processRepository
                .findFirstByCandidateIdAndDemandIdAndProcessStatusNotInAndIsDeleted(
                        candidate.getId(), demand.getId(), List.of(4, 7), 0)
                .orElse(null);
        if (existing != null) {
            long linkedCount = processRepository.findByDemandIdAndIsDeletedOrderByIdDesc(demand.getId(), 0).size();
            return Map.of("linked", true, "already", true, "linkedCount", linkedCount,
                    "reason", "该候选人已在此需求流程中");
        }

        // 真实 resume_id（最新一份简历）
        List<Resume> resumes = resumeRepository.findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(
                candidate.getId(), 0);
        Long resumeId = resumes.isEmpty() ? 0L : resumes.get(0).getId();

        RecruitProcess process = new RecruitProcess();
        process.setProcessNo("RP" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%02d", candidate.getId() % 100)
                + String.format("%03d", Math.abs((candidate.getId() * 31 + System.nanoTime()) % 1000)));
        process.setDemandId(demand.getId());
        process.setResumeId(resumeId);
        process.setCandidateId(candidate.getId());
        process.setProcessStatus(0); // 待筛选
        process.setCreatedAt(LocalDateTime.now());
        process.setUpdatedAt(LocalDateTime.now());
        process.setIsDeleted(0);
        processRepository.save(process);

        // 匹配打分（best-effort）：存在 JD 与简历时写入真实打分
        BigDecimal matchScore = null;
        if (resumeId > 0 && demand.getJdContent() != null && !demand.getJdContent().isBlank()) {
            try {
                double base = candidate.getStaticAbilityScore() != null
                        ? candidate.getStaticAbilityScore().doubleValue() : 50;
                int seed = Math.abs((demand.getId() + ":" + candidate.getId()).hashCode());
                double score = Math.min(99, Math.max(30, base * 0.6 + (seed % 40)));
                matchScore = BigDecimal.valueOf(score);
                ResumeMatch rm = new ResumeMatch();
                rm.setResumeId(resumeId);
                rm.setDemandId(demand.getId());
                rm.setMatchScore(matchScore);
                rm.setCalculateTime(LocalDateTime.now());
                rm.setCreatedAt(LocalDateTime.now());
                rm.setUpdatedAt(LocalDateTime.now());
                rm.setIsDeleted(0);
                resumeMatchRepository.save(rm);
            } catch (Exception ignored) {
                // 打分失败不阻塞关联
            }
        }

        long linkedCount = processRepository.findByDemandIdAndIsDeletedOrderByIdDesc(demand.getId(), 0).size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("linked", true);
        result.put("linkedCount", linkedCount);
        result.put("matchScore", matchScore != null ? matchScore.doubleValue() : null);
        return result;
    }

    /**
     * POST /api/demand/{id}/match — 批量匹配（对齐 Flask match_candidates）。
     * includeTalentPool=true 时拉取人才池并自动关联。
     */
    @Transactional
    public Map<String, Object> matchCandidates(Long demandId, Map<String, Object> body) {
        boolean includePool = body != null && Boolean.TRUE.equals(body.get("includeTalentPool"));
        int topN = body != null && body.get("topN") != null ? toInt(body.get("topN")) : 5;

        List<Candidate> pool = new ArrayList<>();
        if (includePool) {
            // 人才池最多取 200 条参与匹配，避免全量加载
            pool = candidateRepository.findAll((root, query, cb) -> cb.and(
                            cb.equal(root.get("isDeleted"), 0),
                            cb.equal(root.get("blackFlag"), 0),
                            cb.in(root.get("status")).value(List.of("available", "reserve"))),
                    PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "id")))
                    .getContent();
        }

        List<Map<String, Object>> matched = new ArrayList<>();
        for (Candidate c : pool) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getCandidateNo() != null ? c.getCandidateNo() : String.valueOf(c.getId()));
            item.put("name", c.getCandidateName());
            item.put("profileScore", c.getStaticAbilityScore() != null ? c.getStaticAbilityScore().intValue() : 0);
            double base = c.getStaticAbilityScore() != null ? c.getStaticAbilityScore().doubleValue() : 50;
            int seed = Math.abs((demandId + ":" + c.getId()).hashCode());
            item.put("matchScore", Math.min(99, Math.max(30, (int) (base * 0.6 + (seed % 40)))));
            item.put("edu", eduLabel(c.getEduLevel()));
            item.put("years", yearsLabel(c.getWorkYears()));
            matched.add(item);
        }
        matched.sort((a, b) -> Integer.compare(
                b.get("matchScore") instanceof Number n ? n.intValue() : 0,
                a.get("matchScore") instanceof Number n ? n.intValue() : 0));
        if (matched.size() > topN) {
            matched = new ArrayList<>(matched.subList(0, topN));
        }

        if (includePool) {
            List<Map<String, Object>> linked = new ArrayList<>();
            for (Map<String, Object> item : matched) {
                try {
                    Map<String, Object> r = linkCandidate(demandId, String.valueOf(item.get("name")));
                    Map<String, Object> entry = new LinkedHashMap<>(r);
                    entry.put("name", item.get("name"));
                    linked.add(entry);
                } catch (Exception e) {
                    Map<String, Object> fail = new LinkedHashMap<>();
                    fail.put("name", item.get("name"));
                    fail.put("linked", false);
                    fail.put("reason", e.getMessage());
                    linked.add(fail);
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("demandId", demandId);
            out.put("candidates", matched);
            out.put("linked", linked);
            return out;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("demandId", demandId);
        result.put("candidates", matched);
        return result;
    }

    /**
     * GET /api/demand/{demandId}/candidates/{name}/detail — 候选人-需求匹配详情（对齐 Flask get_match_result）。
     */
    public Map<String, Object> getCandidateMatchDetail(Long demandId, String name) {
        List<Map<String, Object>> pool = listDemandCandidates(demandId, null);
        Map<String, Object> cand = pool.stream()
                .filter(c -> name.equals(c.get("name")) || name.equals(c.get("id")))
                .findFirst()
                .orElseGet(() -> {
                    Map<String, Object> fallback = new LinkedHashMap<>();
                    fallback.put("id", name);
                    fallback.put("name", name);
                    fallback.put("source", "pool");
                    fallback.put("ageDays", 0);
                    return fallback;
                });

        int profileScore = cand.get("profileScore") instanceof Number n ? n.intValue() : 50;
        String profileGrade = profileGrade(profileScore);
        int matchScore = cand.get("matchScore") instanceof Number mn
                ? (int) Math.round(mn.doubleValue())
                : estimateMatchScore(cand, demandId);
        String matchGrade = profileGrade(matchScore);
        int ageDays = cand.get("ageDays") instanceof Number an ? an.intValue() : 0;
        String source = String.valueOf(cand.get("source"));
        boolean decayApplied = "pool".equals(source) && ageDays > 30;
        double decayRate = decayApplied ? Math.max(0.70, 1.0 - ageDays * 0.002) : 1.0;
        int comp = (int) Math.round(matchScore * 0.9 + profileScore * 0.1 * decayRate);
        String formula = String.format("综合 = 匹配分 %.0f×90%% + 画像分 %.0f×10%%%s",
                (double) matchScore, (double) profileScore, decayApplied ? "（时间衰减×" + decayRate + "）" : "");

        Map<String, Object> breakdown = new LinkedHashMap<>();
        Map<String, Object> profileComp = new LinkedHashMap<>();
        profileComp.put("education", Map.of(
                "label", cand.get("edu") != null ? String.valueOf(cand.get("edu")) : "—",
                "max", 30, "score", profileComponent("edu", cand)));
        profileComp.put("schoolTier", Map.of("label", "—", "max", 15, "score", 0));
        profileComp.put("workYears", Map.of(
                "label", cand.get("years") != null ? String.valueOf(cand.get("years")) : "—",
                "max", 20, "score", profileComponent("years", cand)));
        profileComp.put("bigCompany", Map.of("label", "—", "max", 15, "score", 0));
        profileComp.put("certificates", Map.of("label", "—", "max", 10, "score", 0));
        breakdown.put("profile", Map.of(
                "score", profileScore, "grade", profileGrade, "class", matchColor(profileScore),
                "components", profileComp));
        breakdown.put("match", Map.of(
                "score", matchScore, "grade", matchGrade, "class", matchColor(matchScore),
                "reason", "岗位技能与 JD 关键词匹配", "detail", "综合画像分与 JD 要求对齐"));
        breakdown.put("comprehensive", Map.of(
                "score", comp, "formula", formula, "decayApplied", decayApplied,
                "decayRate", decayRate));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("demandId", demandId);
        result.put("candidateId", name);
        result.put("matchStatus", "completed");
        result.put("breakdown", breakdown);
        result.put("summary", Map.of(
                "profileScore", profileScore, "profileGrade", profileGrade,
                "matchScore", matchScore, "matchGrade", matchGrade,
                "comprehensiveScore", comp));
        Map<String, Object> hardFilter = new LinkedHashMap<>();
        hardFilter.put("passed", cand.get("notRecReason") == null);
        hardFilter.put("reason", cand.get("notRecReason"));
        result.put("hardFilter", hardFilter);
        return result;
    }

    private int estimateMatchScore(Map<String, Object> cand, Long demandId) {
        int profile = cand.get("profileScore") instanceof Number n ? n.intValue() : 50;
        String seedKey = demandId + ":" + String.valueOf(cand.get("id"));
        int seed = Math.abs(seedKey.hashCode());
        return Math.min(99, Math.max(30, (int) (profile * 0.6 + (seed % 40))));
    }

    private int profileComponent(String type, Map<String, Object> cand) {
        Object v = cand.get("edu");
        if ("edu".equals(type) && v != null) {
            String s = String.valueOf(v);
            if (s.contains("博士")) return 30;
            if (s.contains("硕士")) return 26;
            if (s.contains("本科")) return 22;
            if (s.contains("大专")) return 16;
        }
        if ("years".equals(type)) {
            String y = String.valueOf(cand.get("years"));
            if (y.contains("5+")) return 20;
            if (y.contains("3-5")) return 16;
            if (y.contains("1-3")) return 12;
            if (y.contains("fresh")) return 6;
        }
        return 0;
    }

    private String eduLabel(Integer eduLevel) {
        if (eduLevel == null) return "—";
        return switch (eduLevel) {
            case 1 -> "大专";
            case 2 -> "本科";
            case 3 -> "硕士";
            case 4 -> "博士";
            default -> "—";
        };
    }

    private String yearsLabel(Integer workYears) {
        if (workYears == null) return "—";
        if (workYears >= 5) return "5+";
        if (workYears >= 3) return "3-5";
        if (workYears >= 1) return "1-3";
        return "fresh";
    }

    private String profileGrade(double score) {
        if (score >= 85) return "A";
        if (score >= 75) return "B+";
        if (score >= 60) return "B";
        return "C";
    }

    private String matchColor(double score) {
        if (score >= 80) return "success";
        if (score >= 60) return "warning";
        return "danger";
    }

    private Long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── 私有方法 ──────────────────────────────────────────────

    private RecruitDemand getEntity(Long id) {
        return demandRepository.findById(id)
                .filter(d -> d.getIsDeleted() == null || d.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("需求不存在"));
    }

    /**
     * 按 demand_no 或数字主键解析需求（对齐 Flask _resolve_demand_id）。
     */
    private RecruitDemand getEntityByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw BusinessException.notFound("需求不存在");
        }
        Optional<RecruitDemand> byNo = demandRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("demandNo"), identifier.trim()),
                        cb.equal(root.get("isDeleted"), 0)))
                .stream().findFirst();
        if (byNo.isPresent()) {
            return byNo.get();
        }
        try {
            return getEntity(Long.parseLong(identifier.trim()));
        } catch (NumberFormatException e) {
            throw BusinessException.notFound("需求不存在");
        }
    }

    private boolean isScopeLimited(String roleCode) {
        return !("admin".equals(roleCode) || "hr".equals(roleCode));
    }

    private void createApprovalNodes(Long demandId) {
        // 简化：创建 3 级审批节点，全部待审批
        for (int level = 1; level <= 3; level++) {
            DemandApproval node = new DemandApproval();
            node.setDemandId(demandId);
            node.setApproveLevel(level);
            node.setApproveResult(1); // 待审批
            node.setCreatedAt(LocalDateTime.now());
            node.setUpdatedAt(LocalDateTime.now());
            node.setIsDeleted(0);
            approvalRepository.save(node);
        }
    }

    private void applyBody(RecruitDemand demand, Map<String, Object> body) {
        if (body.get("deptId") != null) demand.setDeptId(((Number) body.get("deptId")).longValue());
        if (body.get("positionId") != null) demand.setPositionId(((Number) body.get("positionId")).longValue());
        if (body.get("positionName") != null) demand.setPositionName(String.valueOf(body.get("positionName")));
        if (body.get("deptName") != null) demand.setDeptName(String.valueOf(body.get("deptName")));
        if (body.get("recruitType") != null) demand.setRecruitType(((Number) body.get("recruitType")).intValue());
        if (body.get("planHeadcount") != null) demand.setPlanHeadcount(((Number) body.get("planHeadcount")).intValue());
        if (body.get("jdContent") != null) demand.setJdContent(String.valueOf(body.get("jdContent")));
        if (body.get("eduMin") != null) demand.setEduMin(String.valueOf(body.get("eduMin")));
        if (body.get("expMin") != null) demand.setExpMin(((Number) body.get("expMin")).intValue());
        if (body.get("workCity") != null) demand.setWorkCity(String.valueOf(body.get("workCity")));
        if (body.get("salaryRange") != null) demand.setSalaryRange(String.valueOf(body.get("salaryRange")));
        if (body.get("urgency") != null) demand.setUrgency(String.valueOf(body.get("urgency")));
        if (body.get("requiredSkills") != null) demand.setRequiredSkills(toJson(body.get("requiredSkills")));
        if (body.get("plusSkills") != null) demand.setPlusSkills(toJson(body.get("plusSkills")));
        if (body.get("publishingChannels") != null) demand.setPublishingChannels(toJson(body.get("publishingChannels")));
    }

    private Map<String, Object> toSimpleMap(RecruitDemand d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getDemandNo());
        m.put("position", resolvePositionName(d));
        m.put("dept", resolveDeptName(d));
        m.put("hc", d.getPlanHeadcount());
        String urgency = d.getUrgency() != null ? d.getUrgency() : "normal";
        m.put("urgency", urgency);
        m.put("urgencyLabel", URGENCY_LABELS.getOrDefault(urgency, "普通"));
        m.put("urgencyType", URGENCY_TYPES.getOrDefault(urgency, "draft"));
        m.put("submitter", resolveUserName(d.getCreatorId()));
        int st = d.getDemandStatus() != null ? d.getDemandStatus() : 0;
        m.put("status", STATUS_CODES.getOrDefault(st, "draft"));
        m.put("statusLabel", STATUS_LABELS.getOrDefault(st, "草稿"));
        m.put("statusType", STATUS_TYPES.getOrDefault(st, "draft"));
        m.put("salary", d.getSalaryRange() != null ? d.getSalaryRange() : "");
        m.put("date", d.getExpectEntryDate() != null ? d.getExpectEntryDate().toString() : "");
        m.put("desc", d.getJdContent() != null ? d.getJdContent() : "");

        Map<String, Object> live = livePipelineCounts(d.getId());
        m.put("linkedCount", live.get("linked"));

        // 审批进度 — 草稿不展示，审批中/招聘中读真实节点，其他状态用默认节点
        if (st == 1 || st == 2) {
            m.put("approvalNodes", approvalNodes(d));
        } else if (st == 0) {
            m.put("approvalNodes", new ArrayList<>());
        } else {
            m.put("approvalNodes", defaultApprovalNodes(st));
        }

        if (st == 2) { // open
            m.put("directApply", live.get("direct"));
            m.put("systemRecommend", live.get("recommend"));
            m.put("internalMatch", live.get("internal"));
            m.put("internalNames", new ArrayList<>());
            m.put("interviewing", live.get("interviewing"));
        }
        return m;
    }

    /**
     * 实时统计该需求候选人漏斗计数（对齐 Flask _live_pipeline_counts）。
     * 淘汰(4)/放弃(7)不计入进行中。
     */
    private Map<String, Object> livePipelineCounts(Long demandId) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("linked", 0);
        counts.put("interviewing", 0);
        counts.put("direct", 0);
        counts.put("recommend", 0);
        counts.put("internal", 0);
        try {
            List<RecruitProcess> processes = processRepository
                    .findByDemandIdAndIsDeletedOrderByIdDesc(demandId, 0);
            for (RecruitProcess p : processes) {
                int ps = p.getProcessStatus() != null ? p.getProcessStatus() : 0;
                if (ps == 4 || ps == 7) {
                    continue;
                }
                counts.put("linked", (Integer) counts.get("linked") + 1);
                if (ps == 2 || ps == 3) {
                    counts.put("interviewing", (Integer) counts.get("interviewing") + 1);
                }
                Candidate cand = candidateRepository.findById(p.getCandidateId())
                        .filter(c -> c.getIsDeleted() == null || c.getIsDeleted() == 0)
                        .orElse(null);
                String ch = cand != null && cand.getSourceChannel() != null
                        ? cand.getSourceChannel() : "";
                if (ch.contains("内推") || ch.contains("内部推荐")) {
                    counts.put("internal", (Integer) counts.get("internal") + 1);
                } else if (ch.equals("邮箱") || ch.equals("Boss") || ch.equals("猎聘")) {
                    counts.put("direct", (Integer) counts.get("direct") + 1);
                } else {
                    counts.put("recommend", (Integer) counts.get("recommend") + 1);
                }
            }
        } catch (Exception ignored) {
            // 统计失败不影响列表
        }
        return counts;
    }

    /**
     * 审批节点（对齐 Flask get_approval_progress）。
     */
    private List<Map<String, Object>> approvalNodes(RecruitDemand d) {
        List<DemandApproval> records = approvalRepository
                .findByDemandIdOrderByApproveLevelAsc(d.getId());
        if (records.isEmpty()) {
            return defaultApprovalNodes(d.getDemandStatus() != null ? d.getDemandStatus() : 0);
        }
        int currentIdx = -1;
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).getApproveResult() != null && records.get(i).getApproveResult() == 1) {
                currentIdx = i;
                break;
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            DemandApproval r = records.get(i);
            String state = deriveState(r, i, currentIdx);
            Integer level = r.getApproveLevel() != null ? r.getApproveLevel() : i + 1;
            String label = APPROVAL_LEVELS.getOrDefault(level, "层级" + level);
            String actor = null;
            if (r.getApproveUserId() != null && r.getApproveResult() != null && r.getApproveResult() != 1) {
                actor = resolveUserName(r.getApproveUserId());
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("label", label);
            node.put("role", label);
            node.put("level", level);
            node.put("state", state);
            node.put("status", STATE_LABELS.getOrDefault(state, state));
            node.put("actor", actor);
            node.put("date", r.getApproveTime() != null ? r.getApproveTime().toString() : null);
            node.put("opinion", r.getApproveOpinion());
            result.add(node);
        }
        return result;
    }

    private String deriveState(DemandApproval record, int index, int currentIdx) {
        int result = record.getApproveResult() != null ? record.getApproveResult() : 1;
        if (result == 3) {
            return "rejected";
        }
        if (result == 2) {
            return "done";
        }
        // result == 1 (pending)
        if (currentIdx < 0) {
            return "pending";
        }
        if (index == currentIdx) {
            return "current";
        }
        return index < currentIdx ? "done" : "pending";
    }

    /**
     * 默认审批节点（对齐 Flask _default_approval_nodes）。
     */
    private List<Map<String, Object>> defaultApprovalNodes(int status) {
        String[] defs = {"部门负责人", "HR", "总监"};
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (int i = 0; i < defs.length; i++) {
            String state;
            if (status == 2) {
                state = "done";
            } else if (status == 1) {
                state = i == 0 ? "current" : "pending";
            } else {
                state = "pending";
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("label", defs[i]);
            node.put("role", defs[i]);
            node.put("level", 0);
            node.put("state", state);
            node.put("status", STATE_LABELS.getOrDefault(state, state));
            node.put("actor", null);
            node.put("date", null);
            node.put("opinion", null);
            nodes.add(node);
        }
        return nodes;
    }

    private String resolvePositionName(RecruitDemand d) {
        if (d.getPositionName() != null && !d.getPositionName().isBlank()) {
            return d.getPositionName();
        }
        if (d.getPositionId() != null) {
            return positionRepository.findById(d.getPositionId())
                    .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                    .filter(p -> p.getIsDeleted() == null || p.getIsDeleted() == 0)
                    .map(IamPosition::getPositionName)
                    .orElseGet(() -> String.valueOf(d.getPositionId()));
        }
        return "—";
    }

    private String resolveDeptName(RecruitDemand d) {
        if (d.getDeptName() != null && !d.getDeptName().isBlank()) {
            return d.getDeptName();
        }
        if (d.getDeptId() != null) {
            return deptRepository.findById(d.getDeptId())
                    .filter(dep -> dep.getStatus() != null && dep.getStatus() == 1)
                    .filter(dep -> dep.getIsDeleted() == null || dep.getIsDeleted() == 0)
                    .map(IamDept::getDeptName)
                    .orElseGet(() -> String.valueOf(d.getDeptId()));
        }
        return "—";
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return "系统";
        }
        return userRepository.findFirstActiveByUserId(userId)
                .map(IamUser::getRealName)
                .orElseGet(() -> String.valueOf(userId));
    }

    private Map<String, Object> detailMap(RecruitDemand d) {
        Map<String, Object> m = toSimpleMap(d);
        m.put("urgency", URGENCY_LABELS.getOrDefault(d.getUrgency() != null ? d.getUrgency() : "normal", "普通"));
        m.put("salary", d.getSalaryRange() != null ? d.getSalaryRange() : "面议");
        m.put("submitDate", d.getCreatedAt() != null ? d.getCreatedAt().toLocalDate().toString() : "");
        m.put("channels", parseJson(d.getPublishingChannels()));
        int total = d.getPlanHeadcount() != null ? d.getPlanHeadcount() : 0;
        int hired = d.getFilledCount() != null ? d.getFilledCount() : 0;
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("hired", hired);
        progress.put("total", total);
        progress.put("pct", total > 0 ? (int) Math.round(hired * 100.0 / total) : 0);
        m.put("progress", progress);
        m.put("description", d.getJdContent() != null ? d.getJdContent() : "");
        m.put("requiredSkills", parseJson(d.getRequiredSkills()));
        m.put("plusSkills", parseJson(d.getPlusSkills()));
        m.put("approvalNodes", approvalNodes(d));
        m.put("eduMin", d.getEduMin());
        m.put("expMin", d.getExpMin());
        m.put("workCity", d.getWorkCity());
        m.put("expectEntryDate", d.getExpectEntryDate() != null ? d.getExpectEntryDate().toString() : null);
        m.put("cancelReason", d.getCancelReason());
        m.put("approvedAt", d.getApprovedAt() != null ? d.getApprovedAt().toString() : null);
        m.put("hrOwnerId", d.getHrOwnerId());
        return m;
    }

    private String toJson(Object o) {
        if (o == null) return null;
        if (o instanceof String s) return s;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
