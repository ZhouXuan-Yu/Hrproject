package com.hr.config.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识库上下文，映射 t_hr_ai_knowledge_base。
 */
@Data
@Entity
@Table(name = "t_hr_ai_knowledge_base")
public class AiKnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false, length = 128)
    private String companyName;

    @Column(name = "industry", length = 128)
    private String industry;

    @Column(name = "website", length = 256)
    private String website;

    @Column(name = "company_profile", columnDefinition = "TEXT")
    private String companyProfile;

    @Column(name = "hiring_principles", columnDefinition = "TEXT")
    private String hiringPrinciples;

    @Column(name = "ai_context", columnDefinition = "TEXT")
    private String aiContext;

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
