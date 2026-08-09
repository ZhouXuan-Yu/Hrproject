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
 * 简历采集邮箱配置，映射 t_hr_recruit_mail_account。
 */
@Data
@Entity
@Table(name = "t_hr_recruit_mail_account")
public class RecruitMailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_name", nullable = false, length = 64)
    private String accountName;

    @Column(name = "email_address", nullable = false, unique = true, length = 128)
    private String emailAddress;

    @Column(name = "imap_host", length = 128)
    private String imapHost;

    @Column(name = "imap_port")
    private Integer imapPort;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "monitor_folder", length = 128)
    private String monitorFolder;

    @Column(name = "mail_type", length = 32)
    private String mailType;

    @Column(name = "sync_freq", nullable = false)
    private Integer syncFreq;

    @Column(name = "password_encrypted", length = 256)
    private String passwordEncrypted;

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

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
