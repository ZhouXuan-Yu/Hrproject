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
 * 系统用户，映射 t_core_user。
 */
@Data
@Entity
@Table(name = "t_core_user")
public class IamUser {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "employee_no", unique = true)
    private String employeeNo;

    @Column(name = "username")
    private String username;

    @Column(name = "real_name")
    private String realName;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "feishu_open_id")
    private String feishuId;

    @Column(name = "role_code")
    private String roleCode;

    @Column(name = "data_scope")
    private String dataScope;

    @Column(name = "email")
    private String email;

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "must_change_password")
    private Integer mustChangePassword;

    @Column(name = "password_updated_at")
    private LocalDateTime passwordUpdatedAt;

    @Column(name = "status")
    private Integer status;

    @Column(name = "is_deleted")
    private Integer isDeleted;

    @Column(name = "created_at")
    private LocalDateTime createTime;

    @Column(name = "updated_at")
    private LocalDateTime updateTime;
}
