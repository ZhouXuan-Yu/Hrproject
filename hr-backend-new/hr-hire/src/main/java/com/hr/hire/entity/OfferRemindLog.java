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
 * Offer 倒计时提醒发送记录，映射 t_hr_offer_remind_log。
 * 用于去重：同一 offer 24h 内不重复发送提醒。
 */
@Data
@Entity
@Table(name = "t_hr_offer_remind_log")
public class OfferRemindLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_id", nullable = false)
    private Long offerId;

    @Column(name = "offer_no", nullable = false, length = 32)
    private String offerNo;

    /** countdown倒计时提醒 */
    @Column(name = "remind_type", nullable = false, length = 16)
    private String remindType;

    /** 发送时剩余天数 */
    @Column(name = "days_left", nullable = false)
    private Integer daysLeft;

    /** 收件邮箱 */
    @Column(name = "sent_to", length = 128)
    private String sentTo;

    /** 0失败 1成功 */
    @Column(name = "send_ok", nullable = false)
    private Integer sendOk;

    /** 发送结果说明 */
    @Column(name = "send_msg", length = 255)
    private String sendMsg;

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
