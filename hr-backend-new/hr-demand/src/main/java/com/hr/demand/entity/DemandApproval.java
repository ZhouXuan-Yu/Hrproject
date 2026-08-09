package com.hr.demand.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 需求审批流水，映射 t_hr_demand_approval。
 */
@Data
@Entity
@Table(name = "t_hr_demand_approval")
public class DemandApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "demand_id", nullable = false)
    private Long demandId;

    @Column(name = "approve_user_id")
    private Long approveUserId;

    @Column(name = "approve_level")
    private Integer approveLevel;

    @Column(name = "approve_result", nullable = false)
    private Integer approveResult;

    @Column(name = "approve_opinion")
    private String approveOpinion;

    @Column(name = "approve_time")
    private LocalDateTime approveTime;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private Integer isDeleted;
}
