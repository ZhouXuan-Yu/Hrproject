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
 * 候选人主表，映射 t_hr_candidate。
 */
@Data
@Entity
@Table(name = "t_hr_candidate")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_no", unique = true, nullable = false)
    private String candidateNo;

    @Column(name = "candidate_name", nullable = false)
    private String candidateName;

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "mobile_hash", unique = true)
    private String mobileHash;

    @Column(name = "email")
    private String email;

    @Column(name = "static_ability_score")
    private BigDecimal staticAbilityScore;

    @Column(name = "edu_level")
    private Integer eduLevel;

    @Column(name = "school_level")
    private Integer schoolLevel;

    @Column(name = "work_years")
    private Integer workYears;

    @Column(name = "big_company_flag", nullable = false)
    private Integer bigCompanyFlag;

    @Column(name = "cert_count", nullable = false)
    private Integer certCount;

    @Column(name = "source_channel")
    private String sourceChannel;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "black_flag", nullable = false)
    private Integer blackFlag;

    @Column(name = "black_type")
    private Integer blackType;

    @Column(name = "black_add_at")
    private LocalDateTime blackAddAt;

    @Column(name = "note")
    private String note;

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
