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
 * 岗位，映射 t_core_position。
 */
@Data
@Entity
@Table(name = "t_core_position")
public class IamPosition {

    @Id
    @JsonProperty("id")
    @Column(name = "position_id")
    private Long positionId;

    /** 岗位编号 PO{YYYYMM}{seq:04d} */
    @Column(name = "position_no", length = 32, unique = true)
    private String positionNo;

    @JsonProperty("name")
    @Column(name = "position_name")
    private String positionName;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "status")
    private Integer status;

    @Column(name = "is_deleted")
    private Integer isDeleted;
}
