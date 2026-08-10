package com.hr.talent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话日志表，映射 t_hr_chat_log。
 */
@Data
@Entity
@Table(name = "t_hr_chat_log")
public class ChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resume_id")
    private Long resumeId;

    @Column(name = "demand_id")
    private Long demandId;

    /** 1AI自动对话 2人工HR */
    @Column(name = "chat_type", nullable = false)
    private Integer chatType;

    @Column(name = "chat_content", nullable = false, columnDefinition = "TEXT")
    private String chatContent;

    /** 对话发生时间 */
    @Column(name = "operate_time", nullable = false)
    private LocalDateTime operateTime;

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
