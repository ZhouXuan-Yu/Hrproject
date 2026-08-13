package com.hr.talent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 候选人标签关联表，映射 t_hr_candidate_tag_rel。
 */
@Data
@Entity
@Table(name = "t_hr_candidate_tag_rel")
public class CandidateTagRel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    /** 1系统自动 2HR手动 3JD自动匹配 */
    @Column(name = "tag_source", nullable = false)
    private Integer tagSource;

    /** 证书/技能过期时间 */
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
