package com.hr.demand.service;

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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final DataScopeService dataScopeService;

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
    @org.springframework.cache.annotation.Cacheable(cacheNames = "list", key = "'demand:' + #page + ':' + #pageSize + ':' + (#keyword != null ? #keyword : '') + ':' + (#status != null ? #status : '') + ':' + (#deptId != null ? #deptId : '') + ':' + #roleCode + ':' + #userId + ':' + (#userDeptId != null ? #userDeptId : '') + ':' + @dataScopeService.resolveScope(#roleCode, #userId)")
    public Map<String, Object> listDemands(int page, int pageSize, String keyword, Integer status,
                                           Long deptId, String roleCode, Long userId, Long userDeptId) {
        Specification<RecruitDemand> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), 0));

            // 数据范围过滤（五档: all/dept/dept_and_self/self/none，个人>角色>默认）
            String scope = dataScopeService.resolveScope(roleCode, userId);
            switch (scope) {
                case "dept" -> predicates.add(cb.equal(root.get("deptId"), userDeptId));
                case "dept_and_self" -> predicates.add(cb.or(
                        cb.equal(root.get("deptId"), userDeptId),
                        cb.equal(root.get("creatorId"), userId)));
                case "self" -> predicates.add(cb.equal(root.get("creatorId"), userId));
                case "none" -> predicates.add(cb.disjunction());
                default -> { /* all（全公司）或未知档位：不额外过滤 */ }
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
    @CacheEvict(cacheNames = "list", allEntries = true)
    public Map<String, Object> createDemand(Map<String, Object> body, Long creatorId, String roleCode) {
        RecruitDemand demand = new RecruitDemand();
        demand.setDemandNo("DM" + LocalDateTime.now().getYear()
                + String.format("%04d", SnowflakeIdGenerator.nextId() % 10000));
        applyBody(demand, body);
        demand.setCreatorId(creatorId);
        if (demand.getRecruitType() == null) demand.setRecruitType(1); // 默认社会招聘
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
    @Cacheable(cacheNames = "list", key = "'demand:detail:' + #demandId")
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
    @CacheEvict(cacheNames = "list", allEntries = true)
    public Map<String, Object> approve(Long demandId, Long userId, String roleCode, String opinion, Integer level) {
        RecruitDemand demand = getEntity(demandId);
        if (demand.getDemandStatus() != 1) {
            throw BusinessException.invalidInput("当前状态不可审批");
        }
        List<DemandApproval> nodes = approvalRepository.findByDemandIdOrderByApproveLevelAsc(demandId);
        ensureLevelPending(nodes, level);
        DemandApproval pending;
        if (level != null) {
            // 按指定层级查找待审节点（对齐 Flask approve 的 level 参数）
            int lv = level;
            pending = nodes.stream()
                    .filter(n -> n.getApproveResult() == 1 && n.getApproveLevel() != null && n.getApproveLevel() == lv)
                    .findFirst().orElse(null);
        } else {
            pending = nodes.stream()
                    .filter(n -> n.getApproveResult() == 1)
                    .findFirst().orElse(null);
        }
        if (pending == null) {
            // 该层级已审批或不存在待审节点，不能直接通过（避免重复点击跳过后续审批）
            throw BusinessException.invalidInput("该需求已无待审批节点，请刷新后重试");
        }
        checkApprovePermission(demand, pending, roleCode);
        pending.setApproveResult(2);
        pending.setApproveUserId(userId);
        pending.setApproveOpinion(opinion);
        pending.setApproveTime(LocalDateTime.now());
        approvalRepository.save(pending);
        boolean allApproved = nodes.stream().allMatch(n -> n.getApproveResult() == 2);
        if (allApproved) {
            demand.setDemandStatus(2);
            demand.setApprovedAt(LocalDateTime.now());
        }
        demand.setUpdatedAt(LocalDateTime.now());
        demandRepository.save(demand);
        return detailMap(demand);
    }

    /**
     * 审批驳回。
     */
    @Transactional
    @CacheEvict(cacheNames = "list", allEntries = true)
    public Map<String, Object> reject(Long demandId, Long userId, String roleCode, String opinion, Integer level) {
        RecruitDemand demand = getEntity(demandId);
        if (demand.getDemandStatus() != 1) {
            throw BusinessException.invalidInput("当前状态不可驳回");
        }
        List<DemandApproval> nodes = approvalRepository.findByDemandIdOrderByApproveLevelAsc(demandId);
        ensureLevelPending(nodes, level);
        DemandApproval pending;
        if (level != null) {
            int lv = level;
            pending = nodes.stream()
                    .filter(n -> n.getApproveResult() == 1 && n.getApproveLevel() != null && n.getApproveLevel() == lv)
                    .findFirst().orElse(null);
        } else {
            pending = nodes.stream()
                    .filter(n -> n.getApproveResult() == 1)
                    .findFirst().orElse(null);
        }
        if (pending == null) {
            throw BusinessException.invalidInput("该需求已无待审批节点，请刷新后重试");
        }
        checkApprovePermission(demand, pending, roleCode);
        pending.setApproveResult(3);
        pending.setApproveUserId(userId);
        pending.setApproveOpinion(opinion);
        pending.setApproveTime(LocalDateTime.now());
        approvalRepository.save(pending);
        demand.setDemandStatus(3); // 驳回
        demand.setUpdatedAt(LocalDateTime.now());
        demandRepository.save(demand);
        return detailMap(demand);
    }

    /**
     * 审批权限校验：admin 可代审批；其余角色必须匹配审批层级，
     * 其中部门负责人(level 1)还必须属于需求所在部门。
     */
    private void checkApprovePermission(RecruitDemand demand, DemandApproval node, String roleCode) {
        if (roleCode == null) {
            throw BusinessException.unauthorized();
        }
        if ("admin".equals(roleCode)) {
            return;
        }
        int level = node.getApproveLevel() != null ? node.getApproveLevel() : 0;
        boolean allowed = switch (level) {
            case 1 -> "dept_head".equals(roleCode) && isDemandDept(demand);
            case 2 -> "hr".equals(roleCode);
            case 3 -> "director".equals(roleCode);
            default -> false;
        };
        if (!allowed) {
            int ownLevel = roleLevel(roleCode);
            if (ownLevel > 0 && ownLevel < level) {
                // 自己的审批层级已通过，当前已流转到更高级别 → 重复点击
                throw BusinessException.invalidInput("您的审批层级已通过，当前审批已流转至下一级，请勿重复操作");
            }
            throw new BusinessException("FORBIDDEN", "当前身份无权审批该层级", 403);
        }
    }

    /** 角色 → 审批层级；非审批角色返回 -1。 */
    private int roleLevel(String roleCode) {
        return switch (roleCode) {
            case "dept_head" -> 1;
            case "hr" -> 2;
            case "director" -> 3;
            default -> -1;
        };
    }

    /** 若指定层级节点已审批（通过/驳回），抛出友好提示，避免重复操作。 */
    private void ensureLevelPending(List<DemandApproval> nodes, Integer level) {
        if (level == null) {
            return;
        }
        int lv = level;
        nodes.stream()
                .filter(n -> n.getApproveLevel() != null && n.getApproveLevel() == lv)
                .findFirst()
                .ifPresent(n -> {
                    if (n.getApproveResult() != null && n.getApproveResult() != 1) {
                        throw BusinessException.invalidInput(
                                n.getApproveResult() == 2
                                        ? "该层级已审批通过，请勿重复操作"
                                        : "该层级已驳回，无需重复操作");
                    }
                });
    }

    /** 部门负责人只能审批本部门需求：当前登录人 deptId 与需求 deptId 一致。 */
    private boolean isDemandDept(RecruitDemand demand) {
        LoginUser current = SecurityUtils.getCurrentUser();
        Long deptId = current != null ? current.getDeptId() : null;
        return demand.getDeptId() != null && deptId != null && demand.getDeptId().equals(deptId);
    }

    /**
     * 关闭需求。
     */
    @Transactional
    @CacheEvict(cacheNames = "list", allEntries = true)
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
    @CacheEvict(cacheNames = "list", allEntries = true)
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
    @CacheEvict(cacheNames = "list", allEntries = true)
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
    @Cacheable(cacheNames = "list", key = "'demand:candidates:' + #demandId + ':' + (#source != null ? #source : 'all')")
    public List<Map<String, Object>> listDemandCandidates(Long demandId, String source) {
        RecruitDemand demand = getEntity(demandId);
        List<RecruitProcess> processes = processRepository
                .findByDemandIdAndIsDeletedOrderByIdDesc(demand.getId(), 0);
        if (processes.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        Set<Long> seenCandidateIds = new HashSet<>();
        // 批量预取候选人，避免循环内 findById（N+1）
        Map<Long, Candidate> candidateById = new HashMap<>();
        for (Candidate cand : candidateRepository.findAllById(
                processes.stream().map(RecruitProcess::getCandidateId).toList())) {
            if (cand.getIsDeleted() == null || cand.getIsDeleted() == 0) {
                candidateById.put(cand.getId(), cand);
            }
        }
        for (RecruitProcess p : processes) {
            if (!seenCandidateIds.add(p.getCandidateId())) {
                continue; // 同一候选人仅保留首条（id DESC），去重
            }
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
    @CacheEvict(cacheNames = "list", key = "'demand:candidates:' + #demandId + ':' + 'all'")
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

        // 匹配打分（best-effort）：存在 JD 与简历时写入基于岗位需求的真实打分
        BigDecimal matchScore = null;
        if (resumeId > 0 && demand.getJdContent() != null && !demand.getJdContent().isBlank()) {
            try {
                double score = computeMatchScore(candidate, demand);
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
     * 解绑候选人与需求（对齐 Flask unlink_candidate_from_demand）。
     */
    @CacheEvict(cacheNames = "list", key = "'demand:candidates:' + #demandId + ':' + 'all'")
    @Transactional
    public Map<String, Object> unlinkCandidate(Long demandId, String name) {
        Candidate candidate = candidateRepository.findFirstByCandidateNameAndIsDeleted(name, 0)
                .orElseThrow(() -> BusinessException.notFound("候选人不存在"));
        List<RecruitProcess> processes = processRepository.findByDemandIdAndCandidateIdAndIsDeleted(
                demandId, candidate.getId(), 0);
        if (processes.isEmpty()) {
            throw BusinessException.notFound("该候选人与需求无关联记录");
        }
        for (RecruitProcess p : processes) {
            if (p.getProcessStatus() != null && !List.of(4, 7).contains(p.getProcessStatus())) {
                // 进行中的流程不允许直接解绑，需要先走淘汰/放弃流程
                throw BusinessException.invalidInput("该候选人存在进行中的流程（状态=" + p.getProcessStatus()
                        + "），不允许解绑。请先将流程结束时再操作");
            }
            if (p.getIsDeleted() == 0) {
                p.setIsDeleted(1);
                p.setUpdatedAt(LocalDateTime.now());
                processRepository.save(p);
            }
        }
        long linkedCount = processRepository.findByDemandIdAndIsDeletedOrderByIdDesc(demandId, 0).size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unlinked", true);
        result.put("linkedCount", linkedCount);
        return result;
    }

    /**
     * POST /api/demand/{id}/match — 批量匹配（对齐 Flask match_candidates）。
     * includeTalentPool=true 时拉取人才池并自动关联。
     */
    @CacheEvict(cacheNames = "list", key = "'demand:candidates:' + #demandId + ':' + 'all'")
    @Transactional
    public Map<String, Object> matchCandidates(Long demandId, Map<String, Object> body) {
        boolean includePool = body != null && Boolean.TRUE.equals(body.get("includeTalentPool"));
        boolean previewOnly = body != null && Boolean.TRUE.equals(body.get("previewOnly"));
        boolean applyHardFilter = body != null && Boolean.TRUE.equals(body.get("applyHardFilter"));
        int topN = body != null && body.get("topN") != null ? toInt(body.get("topN")) : 5;

        // applyHardFilter: 硬性条件预过滤（对齐 Flask match_candidates + filter_hard_requirements）
        if (applyHardFilter) {
            RecruitDemand demand = getEntity(demandId);
            List<Map<String, Object>> rawCandidates = listDemandCandidates(demandId, null);
            Map<String, Object> filterResult = applyHardFilter(rawCandidates, demand);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> passed = (List<Map<String, Object>>) filterResult.get("passed");
            List<String> passedIds = passed.stream()
                    .map(c -> {
                        Object id = c.get("id");
                        Object name = c.get("name");
                        return id != null ? String.valueOf(id) : String.valueOf(name);
                    })
                    .toList();
            // 对通过硬过滤的候选人做匹配打分
            List<Candidate> matchPool = loadCandidatesByIdsOrNames(passedIds, demandId);
            List<Map<String, Object>> matched = scoreCandidates(matchPool, demand, topN);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("demandId", demandId);
            out.put("candidates", matched);
            out.put("hardFilter", Map.of(
                    "total", filterResult.get("total"),
                    "passedCount", filterResult.get("passedCount"),
                    "filteredCount", filterResult.get("filteredCount"),
                    "filtered", filterResult.get("filtered")));
            out.put("previewOnly", true);
            return out;
        }

        // 获取需求实体用于真实匹配打分（对齐 Python batch_match_demand）
        RecruitDemand demand = getEntity(demandId);

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
            // 基于岗位要求的真实匹配打分，替代伪随机 hash
            int matchScore = computeMatchScore(c, demand);
            item.put("matchScore", matchScore);
            item.put("profileGrade", profileGrade(item.get("profileScore") instanceof Number n ? n.intValue() : 0));
            item.put("edu", eduLabel(c.getEduLevel()));
            item.put("years", yearsLabel(c.getWorkYears()));
            item.put("source", "pool");
            item.put("sourceLabel", "人才库");
            item.put("status", "available");
            item.put("statusLabel", "可联系");
            item.putAll(skillSignals(c, demand));
            matched.add(item);
        }
        matched.sort((a, b) -> Integer.compare(
                b.get("matchScore") instanceof Number n ? n.intValue() : 0,
                a.get("matchScore") instanceof Number n ? n.intValue() : 0));
        if (matched.size() > topN) {
            matched = new ArrayList<>(matched.subList(0, topN));
        }

        if (includePool && !previewOnly) {
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
            out.put("previewOnly", false);
            return out;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("demandId", demandId);
        result.put("candidates", matched);
        result.put("previewOnly", previewOnly);
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
        if (body.get("deptId") != null && !String.valueOf(body.get("deptId")).isBlank()) demand.setDeptId(toLong(body.get("deptId")));
        if (body.get("positionId") != null && !String.valueOf(body.get("positionId")).isBlank()) demand.setPositionId(toLong(body.get("positionId")));
        if (body.get("positionName") != null) demand.setPositionName(String.valueOf(body.get("positionName")));
        if (body.get("deptName") != null) demand.setDeptName(String.valueOf(body.get("deptName")));
        // 兼容前端字段名
        if (body.get("dept") != null) {
            String deptText = String.valueOf(body.get("dept")).trim();
            if (!deptText.isEmpty()) {
                demand.setDeptName(deptText);
                // 按名称反查 deptId
                if (demand.getDeptId() == null) {
                    deptRepository.findByDeptNameAndStatusAndIsDeleted(deptText, 1, 0)
                            .stream().findFirst()
                            .ifPresent(dep -> demand.setDeptId(dep.getDeptId()));
                }
            }
        }
        if (body.get("position") != null) {
            String posText = String.valueOf(body.get("position")).trim();
            if (!posText.isEmpty()) {
                demand.setPositionName(posText);
                // 按名称反查 positionId，找不到则自动创建
                if (demand.getPositionId() == null) {
                    IamPosition existing = positionRepository
                            .findByPositionNameAndStatusAndIsDeleted(posText, 1, 0)
                            .stream().findFirst().orElse(null);
                    if (existing != null) {
                        demand.setPositionId(existing.getPositionId());
                    } else {
                        IamPosition newPos = new IamPosition();
                        newPos.setPositionId(SnowflakeIdGenerator.nextId());
                        newPos.setPositionName(posText);
                        newPos.setDeptId(demand.getDeptId());
                        newPos.setStatus(1);
                        newPos.setIsDeleted(0);
                        positionRepository.save(newPos);
                        demand.setPositionId(newPos.getPositionId());
                    }
                }
            }
        }
        if (body.get("recruitType") != null) demand.setRecruitType(toInt(body.get("recruitType")));
        Object hc = body.get("hc") != null ? body.get("hc") : body.get("planHeadcount");
        if (hc != null) demand.setPlanHeadcount(toInt(hc));
        Object jd = body.get("desc") != null ? body.get("desc") : body.get("jdContent");
        if (jd != null) demand.setJdContent(String.valueOf(jd));
        Object salary = body.get("salary") != null ? body.get("salary") : body.get("salaryRange");
        if (salary != null) demand.setSalaryRange(String.valueOf(salary));
        Object rawDate = body.get("date") != null ? body.get("date") : body.get("expectEntryDate");
        if (rawDate != null) {
            try {
                demand.setExpectEntryDate(LocalDate.parse(String.valueOf(rawDate).substring(0, 10)));
            } catch (Exception ignored) { }
        }
        if (body.get("eduMin") != null) demand.setEduMin(String.valueOf(body.get("eduMin")));
        if (body.get("expMin") != null) demand.setExpMin(toInt(body.get("expMin")));
        if (body.get("workCity") != null) demand.setWorkCity(String.valueOf(body.get("workCity")));
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

    // ── Hard filter helpers (对齐 Flask filter_hard_requirements) ──

    private static final Map<String, Integer> EDU_LEVEL = Map.of(
            "大专", 1, "专科", 1,
            "本科", 2, "学士", 2,
            "硕士", 3, "硕士研究生", 3,
            "博士", 4, "博士研究生", 4);

    private static int eduLevelCode(String label) {
        if (label == null) return 0;
        return EDU_LEVEL.getOrDefault(label.trim(), 0);
    }

    /**
     * 硬性要求过滤（对齐 Flask filter_hard_requirements）。
     * 按 edu_min / exp_min 过滤候选人列表。
     */
    private Map<String, Object> applyHardFilter(List<Map<String, Object>> candidates, RecruitDemand demand) {
        String eduMinRaw = demand.getEduMin() != null ? demand.getEduMin().trim() : "";
        int expMin = demand.getExpMin() != null ? demand.getExpMin() : 0;
        int eduMinLevel = EDU_LEVEL.getOrDefault(eduMinRaw, 0);

        List<Map<String, Object>> passed = new ArrayList<>();
        List<Map<String, Object>> filtered = new ArrayList<>();

        for (Map<String, Object> cand : candidates) {
            String edu = cand.get("edu") != null ? String.valueOf(cand.get("edu")) : "";
            Object wyObj = cand.get("work_years") != null ? cand.get("work_years") : cand.get("workYears");
            int workYears = 0;
            if (wyObj instanceof Number n) {
                workYears = n.intValue();
            } else if (wyObj != null) {
                try { workYears = Integer.parseInt(String.valueOf(wyObj)); } catch (Exception ignored) {}
            }

            int candEduLevel = eduLevelCode(edu);
            List<String> reasons = new ArrayList<>();
            if (eduMinLevel > 0 && candEduLevel < eduMinLevel) {
                reasons.add("学历不符（要求" + eduMinRaw + "，实际" + edu + "）");
            }
            if (expMin > 0 && workYears < expMin) {
                reasons.add("经验不足（要求" + expMin + "年，实际" + workYears + "年）");
            }
            if (reasons.isEmpty()) {
                passed.add(cand);
            } else {
                Map<String, Object> item = new LinkedHashMap<>(cand);
                item.put("notRecReason", String.join("；", reasons));
                filtered.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed);
        result.put("filtered", filtered);
        result.put("total", candidates.size());
        result.put("passedCount", passed.size());
        result.put("filteredCount", filtered.size());
        return result;
    }

    /**
     * 按 ID/名称批量加载候选人，用于匹配打分。
     */
    private List<Candidate> loadCandidatesByIdsOrNames(List<String> ids, Long demandId) {
        List<Candidate> result = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            // 先按 candidateNo 查
            Candidate c = candidateRepository.findByCandidateNoAndIsDeleted(id, 0).orElse(null);
            if (c == null) {
                // 再按姓名查
                c = candidateRepository.findFirstByCandidateNameAndIsDeleted(id, 0).orElse(null);
            }
            if (c != null && (c.getBlackFlag() == null || c.getBlackFlag() == 0)
                    && !"locked".equals(c.getStatus())) {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * 对候选人列表打分、排序、截断 topN（对齐 Flask batch_match_demand 评分逻辑）。
     */
    private List<Map<String, Object>> scoreCandidates(List<Candidate> pool, RecruitDemand demand, int topN) {
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Candidate c : pool) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getCandidateNo() != null ? c.getCandidateNo() : String.valueOf(c.getId()));
            item.put("name", c.getCandidateName());
            item.put("profileScore", c.getStaticAbilityScore() != null ? c.getStaticAbilityScore().intValue() : 0);
            item.put("matchScore", computeMatchScore(c, demand));
            item.put("profileGrade", profileGrade(item.get("profileScore") instanceof Number n ? n.intValue() : 0));
            item.put("edu", eduLabel(c.getEduLevel()));
            item.put("years", yearsLabel(c.getWorkYears()));
            item.put("source", "pool");
            item.put("sourceLabel", "人才库");
            item.put("status", "available");
            item.put("statusLabel", "可联系");
            item.putAll(skillSignals(c, demand));
            matched.add(item);
        }
        matched.sort((a, b) -> Integer.compare(
                b.get("matchScore") instanceof Number n ? n.intValue() : 0,
                a.get("matchScore") instanceof Number n ? n.intValue() : 0));
        if (matched.size() > topN) {
            matched = new ArrayList<>(matched.subList(0, topN));
        }
        return matched;
    }

    /**
     * 基于岗位需求与候选人画像的真实匹配打分（对齐 Python match_service）。
     * 画像分(0-45) + 学历匹配(0-20) + 经验匹配(0-20) + 需求技能命中(0-15)。
     * 不再使用固定 JD 分或哈希扰动，避免把没有依据的分数展示成真实匹配结果。
     */
    private int computeMatchScore(Candidate c, RecruitDemand demand) {
        // ── 1. 画像分 (0-45) ──
        int profileScore = 20; // base
        Integer edu = c.getEduLevel();
        if (edu != null) profileScore += Math.min(12, edu * 3); // 大专+3,本科+6,硕士+9,博士+12
        Integer school = c.getSchoolLevel();
        if (school != null) profileScore += Math.min(6, school * 2); // 普通+2,211+4,985+6
        Integer wy = c.getWorkYears();
        if (wy != null) profileScore += Math.min(8, wy * 2); // 每年+2,上限8
        if (c.getBigCompanyFlag() != null && c.getBigCompanyFlag() == 1) profileScore += 4;
        Integer cert = c.getCertCount();
        if (cert != null) profileScore += Math.min(3, cert);
        profileScore = Math.min(45, profileScore);

        // ── 2. 学历匹配 (0-20) ──
        int eduMatch = 0;
        String eduMin = demand.getEduMin();
        int demandEduLevel = mapEduLevel(eduMin);
        if (demandEduLevel > 0) {
            int candEdu = edu != null ? edu : 0;
            if (candEdu >= demandEduLevel) eduMatch = 20; // 达标
            else if (candEdu == demandEduLevel - 1) eduMatch = 8; // 差一档
        } else {
            eduMatch = 12; // 无学历要求 → 中性分
        }

        // ── 3. 经验匹配 (0-20) ──
        int expMatch = 0;
        Integer expMin = demand.getExpMin();
        if (expMin != null && expMin > 0) {
            int candYears = wy != null ? wy : 0;
            if (candYears >= expMin) expMatch = 20; // 达标
            else if (candYears >= Math.max(0, expMin - 1)) expMatch = 8;
        } else {
            expMatch = 12; // 无经验要求 → 中性分
        }

        Map<String, Object> skills = skillSignals(c, demand);
        int skillHit = skills.get("skillScore") instanceof Number n ? n.intValue() : 0;
        return Math.min(100, Math.max(0, profileScore + eduMatch + expMatch + skillHit));
    }

    private Map<String, Object> skillSignals(Candidate candidate, RecruitDemand demand) {
        Set<String> required = jsonStringSet(demand.getRequiredSkills());
        Set<String> plus = jsonStringSet(demand.getPlusSkills());
        Set<String> candidateSkills = candidateSkills(candidate);
        Set<String> matchedRequired = matchedSkills(required, candidateSkills);
        Set<String> matchedPlus = matchedSkills(plus, candidateSkills);
        int skillScore = required.isEmpty() ? 0
                : (int) Math.round(10.0 * matchedRequired.size() / required.size());
        skillScore += plus.isEmpty() ? 0
                : (int) Math.round(5.0 * matchedPlus.size() / plus.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillScore", skillScore);
        result.put("matchedSkills", matchedRequired);
        result.put("missingSkills", required.stream().filter(s -> !matchedRequired.contains(s)).toList());
        result.put("matchedPlusSkills", matchedPlus);
        return result;
    }

    private Set<String> candidateSkills(Candidate candidate) {
        List<Resume> resumes = resumeRepository.findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(candidate.getId(), 0);
        if (resumes.isEmpty()) return Set.of();
        Object parsed = parseJson(resumes.get(0).getExtractJson());
        if (!(parsed instanceof Map<?, ?> map)) return Set.of();
        for (String key : List.of("skills", "tech_stack", "skill_tags", "tags", "keywords")) {
            Object raw = map.get(key);
            if (raw instanceof List<?> list) {
                return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toSet());
            }
            if (raw instanceof String text && !text.isBlank()) {
                return java.util.Arrays.stream(text.split(",|，|、|\\n"))
                        .map(String::trim).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toSet());
            }
        }
        return Set.of();
    }

    private Set<String> jsonStringSet(String json) {
        Object parsed = parseJson(json);
        if (parsed instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toSet());
        }
        if (parsed instanceof String text && !text.isBlank()) {
            return java.util.Arrays.stream(text.split(",|，|、|\\n"))
                    .map(String::trim).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    private Set<String> matchedSkills(Set<String> required, Set<String> actual) {
        Set<String> matched = new HashSet<>();
        for (String expected : required) {
            String e = expected.replaceAll("\\s+", "").toLowerCase();
            if (actual.stream().map(s -> s.replaceAll("\\s+", "").toLowerCase())
                    .anyMatch(a -> a.contains(e) || e.contains(a))) {
                matched.add(expected);
            }
        }
        return matched;
    }

    /** 学历文本 → 层级数值 */
    private int mapEduLevel(String eduMin) {
        if (eduMin == null || eduMin.isBlank() || "不限".equals(eduMin)) return 0;
        return switch (eduMin) {
            case "博士" -> 4;
            case "硕士" -> 3;
            case "本科" -> 2;
            case "大专", "专科" -> 1;
            default -> 0;
        };
    }
}
