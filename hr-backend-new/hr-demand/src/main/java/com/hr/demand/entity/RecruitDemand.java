package com.hr.demand.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招聘需求主表，映射 t_hr_recruit_demand。
 */
@Data
@Entity
@Table(name = "t_hr_recruit_demand")
public class RecruitDemand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "demand_no", unique = true, nullable = false)
    private String demandNo;

    @Column(name = "dept_id", nullable = false)
    private Long deptId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "recruit_type", nullable = false)
    private Integer recruitType;

    @Column(name = "plan_headcount", nullable = false)
    private Integer planHeadcount;

    @Column(name = "filled_count", nullable = false)
    private Integer filledCount;

    @Column(name = "expect_entry_date")
    private LocalDate expectEntryDate;

    @Column(name = "jd_content", columnDefinition = "TEXT")
    private String jdContent;

    @Column(name = "edu_min")
    private String eduMin;

    @Column(name = "exp_min")
    private Integer expMin;

    @Column(name = "work_city")
    private String workCity;

    @Column(name = "publishing_channels", columnDefinition = "JSON")
    private String publishingChannels;

    @Column(name = "demand_status", nullable = false)
    private Integer demandStatus;

    @Column(name = "cancel_at")
    private LocalDateTime cancelAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "audit_flow", columnDefinition = "JSON")
    private String auditFlow;

    @Column(name = "headcount_reserve_json", columnDefinition = "JSON")
    private String headcountReserveJson;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "hr_owner_id")
    private Long hrOwnerId;

    @Column(name = "internal_searched", nullable = false)
    private Integer internalSearched;

    @Column(name = "resume_searched", nullable = false)
    private Integer resumeSearched;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "is_internal_given_up", nullable = false)
    private Integer isInternalGivenUp;

    @Column(name = "recommend_limit")
    private Integer recommendLimit;

    @Column(name = "salary_range")
    private String salaryRange;

    @Column(name = "urgency", nullable = false)
    private String urgency;

    @Column(name = "required_skills", columnDefinition = "JSON")
    private String requiredSkills;

    @Column(name = "plus_skills", columnDefinition = "JSON")
    private String plusSkills;

    @Column(name = "position_name")
    private String positionName;

    @Column(name = "dept_name")
    private String deptName;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted")
    private Integer isDeleted;
}
