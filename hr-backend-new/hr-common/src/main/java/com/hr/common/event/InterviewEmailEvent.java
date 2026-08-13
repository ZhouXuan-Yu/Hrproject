package com.hr.common.event;

import lombok.Getter;

/**
 * 面试邮件事件 — 创建面试时发邀请，评价淘汰时发通知。
 * 由 hr-interview 模块发布，hr-bootstrap 的 EmailEventListener 监听并调 MailService。
 */
@Getter
public class InterviewEmailEvent {

    /** invite = 面试邀请邮件（含确认链接），reject = 淘汰通知邮件 */
    private final String type;
    /** t_hr_interview_book.id */
    private final Long bookId;
    /** 候选人姓名 */
    private final String candidateName;
    /** 岗位名称 */
    private final String position;
    /** 面试时间 "yyyy-MM-dd HH:mm" */
    private final String interviewTime;
    /** 轮次描述，如 "初试(1轮)" */
    private final String round;
    /** 面试方式，如 "飞书视频" */
    private final String method;
    /** 会议链接 */
    private final String meetingUrl;
    /** 会议号 */
    private final String meetingCode;
    /** address */
    private final String address;

    public InterviewEmailEvent(String type, Long bookId, String candidateName,
                               String position, String interviewTime, String round,
                               String method, String meetingUrl, String meetingCode,
                               String address) {
        this.type = type;
        this.bookId = bookId;
        this.candidateName = candidateName;
        this.position = position;
        this.interviewTime = interviewTime;
        this.round = round;
        this.method = method;
        this.meetingUrl = meetingUrl;
        this.meetingCode = meetingCode;
        this.address = address;
    }
}
