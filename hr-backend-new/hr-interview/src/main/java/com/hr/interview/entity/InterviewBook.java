package com.hr.interview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试预约单，映射 t_hr_interview_book。
 */
@Data
@Entity
@Table(name = "t_hr_interview_book")
public class InterviewBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "demand_id", nullable = false)
    private Long demandId;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "process_id", nullable = false)
    private Long processId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "interview_round", nullable = false)
    private Integer interviewRound;

    @Column(name = "interview_type", nullable = false)
    private Integer interviewType;

    @Column(name = "meeting_code")
    private String meetingCode;

    @Column(name = "meeting_pwd")
    private String meetingPwd;

    @Column(name = "meeting_url")
    private String meetingUrl;

    @Column(name = "address")
    private String address;

    @Column(name = "book_time", nullable = false)
    private LocalDateTime bookTime;

    @Column(name = "invite_json", columnDefinition = "JSON")
    private String inviteJson;

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
