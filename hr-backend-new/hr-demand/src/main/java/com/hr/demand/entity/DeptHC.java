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
 * 部门编制表，映射 t_hr_dept_hc。
 */
@Data
@Entity
@Table(name = "t_hr_dept_hc")
public class DeptHC {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dept_id", nullable = false, unique = true)
    private Long deptId;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "hc_total", nullable = false)
    private Integer hcTotal;

    @Column(name = "hc_used", nullable = false)
    private Integer hcUsed;

    @Column(name = "hc_available", nullable = false)
    private Integer hcAvailable;

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
