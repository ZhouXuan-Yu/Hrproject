package com.hr.talent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 简历岗位匹配记录表，映射 t_hr_resume_match。
 */
@Data
@Entity
@Table(name = "t_hr_resume_match")
public class ResumeMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "demand_id", nullable = false)
    private Long demandId;

    @Column(name = "match_score", nullable = false)
    private BigDecimal matchScore;

    @Column(name = "score_detail", columnDefinition = "JSON")
    private String scoreDetail;

    @Column(name = "calculate_time", nullable = false)
    private LocalDateTime calculateTime;

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
