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
 * 通知模板，映射 t_hr_notify_template。
 */
@Data
@Entity
@Table(name = "t_hr_notify_template")
public class NotifyTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name", nullable = false, length = 128)
    private String templateName;

    @Column(name = "template_type", nullable = false, length = 32)
    private String templateType;

    @Column(name = "send_method", length = 64)
    private String sendMethod;

    @Column(name = "subject", length = 256)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

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
