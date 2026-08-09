package com.hr.config.entity;

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
 * 打分规则，映射 t_hr_score_rule。
 */
@Data
@Entity
@Table(name = "t_hr_score_rule")
public class ScoreRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "score_scene", nullable = false)
    private Integer scoreScene;

    @Column(name = "weight_json", nullable = false, columnDefinition = "JSON")
    private String weightJson;

    @Column(name = "pool_min_score", precision = 4, scale = 1)
    private BigDecimal poolMinScore;

    @Column(name = "auto_invite_min_score", precision = 4, scale = 1)
    private BigDecimal autoInviteMinScore;

    @Column(name = "status", nullable = false)
    private Integer status;

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
