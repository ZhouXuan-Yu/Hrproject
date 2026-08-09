package com.hr.talent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 简历招聘流程表，映射 t_hr_recruit_process。
 */
@Data
@Entity
@Table(name = "t_hr_recruit_process")
public class RecruitProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_no", unique = true, nullable = false)
    private String processNo;

    @Column(name = "demand_id", nullable = false)
    private Long demandId;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    /** 0待筛 1邀约 2一面 3二面 4淘汰 5待Offer 6接受 7放弃 8入职 */
    @Column(name = "process_status", nullable = false)
    private Integer processStatus;

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
