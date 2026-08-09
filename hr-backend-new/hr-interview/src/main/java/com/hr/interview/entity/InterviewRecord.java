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
 * 面试评价记录，映射 t_hr_interview_record。
 */
@Data
@Entity
@Table(name = "t_hr_interview_record")
public class InterviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "process_id", nullable = false)
    private Long processId;

    @Column(name = "interviewer_ids", nullable = false, columnDefinition = "JSON")
    private String interviewerIds;

    @Column(name = "submit_interviewer_id", nullable = false)
    private Long submitInterviewerId;

    @Column(name = "is_arrive", nullable = false)
    private Integer isArrive;

    @Column(name = "interview_result", nullable = false)
    private Integer interviewResult;

    @Column(name = "evaluate_text", columnDefinition = "TEXT")
    private String evaluateText;

    @Column(name = "score_json", columnDefinition = "JSON")
    private String scoreJson;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "feishu_memo_url")
    private String feishuMemoUrl;

    @Column(name = "highlight_json", columnDefinition = "JSON")
    private String highlightJson;

    @Column(name = "ai_draft_json", columnDefinition = "JSON")
    private String aiDraftJson;

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
