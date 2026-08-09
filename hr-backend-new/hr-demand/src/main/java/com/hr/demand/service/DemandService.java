package com.hr.demand.service;

import com.hr.common.exception.BusinessException;
import com.hr.common.util.SnowflakeIdGenerator;
import com.hr.demand.entity.DemandApproval;
import com.hr.demand.entity.RecruitDemand;
import com.hr.demand.repository.DemandApprovalRepository;
import com.hr.demand.repository.RecruitDemandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求管理服务，对齐 Flask demand_service.py 核心逻辑。
 */
@Service
@RequiredArgsConstructor
public class DemandService {

    private final RecruitDemandRepository demandRepository;
    private final DemandApprovalRepository approvalRepository;

    /**
     * 需求列表（分页 + 筛选 + 数据范围）。
     */
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
     * 需求详情。
     */
    public Map<String, Object> getDemandDetail(Long demandId) {
        RecruitDemand demand = demandRepository.findById(demandId)
                .filter(d -> d.getIsDeleted() == null || d.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("需求不存在"));
        return detailMap(demand);
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

    // ── 私有方法 ──────────────────────────────────────────────

    private RecruitDemand getEntity(Long id) {
        return demandRepository.findById(id)
                .filter(d -> d.getIsDeleted() == null || d.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("需求不存在"));
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
        m.put("id", d.getId());
        m.put("demandNo", d.getDemandNo());
        m.put("positionName", d.getPositionName());
        m.put("deptName", d.getDeptName());
        m.put("deptId", d.getDeptId());
        m.put("positionId", d.getPositionId());
        m.put("recruitType", d.getRecruitType());
        m.put("planHeadcount", d.getPlanHeadcount());
        m.put("filledCount", d.getFilledCount());
        m.put("demandStatus", d.getDemandStatus());
        m.put("urgency", d.getUrgency());
        m.put("salaryRange", d.getSalaryRange());
        m.put("creatorId", d.getCreatorId());
        m.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> detailMap(RecruitDemand d) {
        Map<String, Object> m = toSimpleMap(d);
        m.put("jdContent", d.getJdContent());
        m.put("eduMin", d.getEduMin());
        m.put("expMin", d.getExpMin());
        m.put("workCity", d.getWorkCity());
        m.put("expectEntryDate", d.getExpectEntryDate() != null ? d.getExpectEntryDate().toString() : null);
        m.put("requiredSkills", parseJson(d.getRequiredSkills()));
        m.put("plusSkills", parseJson(d.getPlusSkills()));
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
