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

    /**
     * 计算静态画像分（0-100），落库到 static_ability_score。
     * 维度：学历 35 + 院校 20 + 工龄 20 + 大厂 15 + 证书 10，硬规则、确定性，不调 AI。
     */
    public BigDecimal computeStaticAbilityScore() {
        // 学历 (0-35)：大专 15 / 本科 22 / 硕士 28 / 博士 35
        int edu = 8;
        if (eduLevel != null) {
            edu = switch (eduLevel) {
                case 1 -> 15;
                case 2 -> 22;
                case 3 -> 28;
                case 4 -> 35;
                default -> 8;
            };
        }
        // 院校 (0-20)：普通 8 / 211 13 / 985 18 / C9 20
        int school = 4;
        if (schoolLevel != null) {
            school = switch (schoolLevel) {
                case 1 -> 8;
                case 2 -> 13;
                case 3 -> 18;
                case 4 -> 20;
                default -> 4;
            };
        }
        // 工龄 (0-20)：每年 +2，上限 20
        int years = 6;
        if (workYears != null) {
            years = Math.min(20, workYears * 2);
        }
        // 大厂 (0-15)
        int bigCompany = bigCompanyFlag != null && bigCompanyFlag == 1 ? 15 : 0;
        // 证书 (0-10)：每张 +2，上限 10
        int cert = Math.min(10, (certCount != null ? certCount : 0) * 2);

        int score = Math.max(0, Math.min(100, edu + school + years + bigCompany + cert));
        return BigDecimal.valueOf(score);
    }
}
