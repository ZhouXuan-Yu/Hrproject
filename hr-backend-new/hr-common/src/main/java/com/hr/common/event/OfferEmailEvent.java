package com.hr.common.event;

import lombok.Getter;

/**
 * Offer 邮件事件 — 发送 Offer 时通知候选人。
 * 由 hr-hire 模块发布，hr-bootstrap 的 EmailEventListener 监听并调 MailService。
 */
@Getter
public class OfferEmailEvent {

    /** t_hr_offer.offer_no */
    private final String offerNo;
    /** 简历 ID */
    private final Long resumeId;
    /** 候选人姓名 */
    private final String candidateName;
    /** Offer 正文 */
    private final String offerContent;
    /** 有效期截止 */
    private final String validDeadline;

    public OfferEmailEvent(String offerNo, Long resumeId, String candidateName,
                           String offerContent, String validDeadline) {
        this.offerNo = offerNo;
        this.resumeId = resumeId;
        this.candidateName = candidateName;
        this.offerContent = offerContent;
        this.validDeadline = validDeadline;
    }
}
