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
 * 招聘需求审批身份映射表，映射 t_hr_approval_identity。
 */
@Data
@Entity
@Table(name = "t_hr_approval_identity")
public class RecruitApprovalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "identity_type", nullable = false, length = 32)
    private String identityType;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "status")
    private Integer status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private Integer isDeleted;
}
