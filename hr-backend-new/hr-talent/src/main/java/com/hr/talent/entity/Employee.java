package com.hr.talent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 内部员工档案表，映射 t_hr_employee。
 */
@Data
@Entity
@Table(name = "t_hr_employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "work_years")
    private Integer workYears;

    @Column(name = "perf_score")
    private BigDecimal perfScore;

    @Column(name = "last_promote_date")
    private LocalDate lastPromoteDate;

    @Column(name = "can_transfer", nullable = false)
    private Integer canTransfer;

    @Column(name = "compositive_score")
    private BigDecimal compositiveScore;

    @Column(name = "transfer_restrict_reason")
    private String transferRestrictReason;

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
