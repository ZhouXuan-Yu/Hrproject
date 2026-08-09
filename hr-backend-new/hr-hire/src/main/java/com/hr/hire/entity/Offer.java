package com.hr.hire.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Offer 发放表，映射 t_hr_offer。
 * offer_status: 0草稿 1已发送 2已接受 3已拒绝 4已过期
 */
@Data
@Entity
@Table(name = "t_hr_offer")
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_no", unique = true, nullable = false)
    private String offerNo;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "process_id", nullable = false)
    private Long processId;

    @Column(name = "demand_id", nullable = false)
    private Long demandId;

    @Column(name = "last_interview_id")
    private Long lastInterviewId;

    @Column(name = "offer_content", columnDefinition = "TEXT")
    private String offerContent;

    @Column(name = "salary_json", columnDefinition = "JSON")
    private String salaryJson;

    @Column(name = "valid_deadline", nullable = false)
    private LocalDateTime validDeadline;

    @Column(name = "offer_status", nullable = false)
    private Integer offerStatus;

    @Column(name = "send_user_id", nullable = false)
    private Long sendUserId;

    @Column(name = "send_time", nullable = false)
    private LocalDateTime sendTime;

    @Column(name = "offer_file_id")
    private Long offerFileId;

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
