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
 * 简历表，映射 t_hr_resume。
 */
@Data
@Entity
@Table(name = "t_hr_resume")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "resume_file_id")
    private Long resumeFileId;

    @Column(name = "storage_time", nullable = false)
    private LocalDateTime storageTime;

    @Column(name = "base_score", nullable = false)
    private BigDecimal baseScore;

    @Column(name = "work_exp_text", columnDefinition = "TEXT")
    private String workExpText;

    @Column(name = "extract_json", columnDefinition = "JSON")
    private String extractJson;

    @Column(name = "touch_json", columnDefinition = "JSON")
    private String touchJson;

    @Column(name = "source_channel_id")
    private Long sourceChannelId;

    @Column(name = "mail_account_id")
    private Long mailAccountId;

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
