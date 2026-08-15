package com.hr.talent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历搜索日志表，映射 t_hr_search_log。
 */
@Data
@Entity
@Table(name = "t_hr_search_log")
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "demand_id", nullable = false)
    private Long demandId;

    /** 1内部员工库 2外部简历库 */
    @Column(name = "search_type", nullable = false)
    private Integer searchType;

    /** 检索执行时间 */
    @Column(name = "search_at", nullable = false)
    private LocalDateTime searchAt;

    /** 合格匹配人数 */
    @Column(name = "match_total", nullable = false)
    private Integer matchTotal;

    /** 筛选条件备注 */
    @Column(name = "remark", length = 512)
    private String remark;

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
