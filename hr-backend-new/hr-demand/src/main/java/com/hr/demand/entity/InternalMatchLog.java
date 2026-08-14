package com.hr.demand.entity;

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
 * 内部员工匹配日志表，映射 t_hr_internal_match_log。
 */
@Data
@Entity
@Table(name = "t_hr_internal_match_log")
public class InternalMatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 匹配流水编号 */
    @Column(name = "match_no", nullable = false, unique = true, length = 32)
    private String matchNo;

    @Column(name = "demand_id", nullable = false)
    private Long demandId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** 内部匹配分 */
    @Column(name = "match_score", precision = 4, scale = 1)
    private BigDecimal matchScore;

    /** 10待确认 20已调配 30忽略 */
    @Column(name = "match_status", nullable = false)
    private Integer matchStatus;

    /** 操作HR */
    @Column(name = "operator_user_id")
    private Long operatorUserId;

    /** 匹配时间 */
    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;

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
