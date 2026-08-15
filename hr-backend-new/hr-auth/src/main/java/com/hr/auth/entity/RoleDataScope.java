package com.hr.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色数据范围配置，映射 t_hr_role_data_scope。
 * 五档: all 全公司 / dept 本部门 / dept_and_self 本部门+指定给自己的 / self 仅自己 / none 无。
 */
@Data
@Entity
@Table(name = "t_hr_role_data_scope")
public class RoleDataScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    @Column(name = "enabled", nullable = false)
    private Integer enabled;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private Integer isDeleted;
}
