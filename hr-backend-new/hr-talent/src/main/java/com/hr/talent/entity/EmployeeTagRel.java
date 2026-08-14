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
 * 内部员工标签关联表，映射 t_hr_employee_tag_rel。
 */
@Data
@Entity
@Table(name = "t_hr_employee_tag_rel")
public class EmployeeTagRel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    /** 1档案同步 2HR手动 3内部匹配生成 */
    @Column(name = "tag_source", nullable = false)
    private Integer tagSource;

    /** 标签附带绩效分 */
    @Column(name = "tag_related_score", precision = 3, scale = 1)
    private BigDecimal tagRelatedScore;

    /** 荣誉/临时标签过期时间 */
    @Column(name = "valid_end")
    private LocalDate validEnd;

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
