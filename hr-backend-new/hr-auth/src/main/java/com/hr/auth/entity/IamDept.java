package com.hr.auth.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 部门组织，映射 t_core_dept。
 */
@Data
@Entity
@Table(name = "t_core_dept")
public class IamDept {

    @Id
    @JsonProperty("id")
    @Column(name = "dept_id")
    private Long deptId;

    @JsonProperty("name")
    @Column(name = "dept_name")
    private String deptName;

    @Column(name = "parent_dept_id")
    private Long parentDeptId;

    @Column(name = "dept_path")
    private String deptPath;

    @Column(name = "sort_num")
    private Integer sortNum;

    @Column(name = "status")
    private Integer status;

    @Column(name = "is_deleted")
    private Integer isDeleted;
}
