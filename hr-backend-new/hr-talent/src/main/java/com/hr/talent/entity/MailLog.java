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
 * 外发邮件日志，映射 t_hr_mail_log。
 */
@Data
@Entity
@Table(name = "t_hr_mail_log")
public class MailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_account_id")
    private Long senderAccountId;

    @Column(name = "sender_email", nullable = false)
    private String senderEmail;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "mail_type", nullable = false)
    private String mailType;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "error_msg")
    private String errorMsg;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_deleted")
    private Integer isDeleted;
}
