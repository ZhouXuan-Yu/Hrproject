package com.hr.bootstrap.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 通用邮件发送服务，对齐 Flask mail_sender.py。
 *
 * 安全设计：
 * - 默认不发送（mail.enabled=false），需显式配置 SMTP + 开关
 * - 所有发送 best-effort，失败只记日志不抛异常
 * - 未配置时返回 false + 原因说明
 */
@Slf4j
@Service
public class MailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${mail.from:}")
    private String mailFrom;

    /**
     * 发送 HTML 邮件。
     *
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param html    邮件正文（HTML）
     * @return {"ok": true/false, "message": "..."}
     */
    public Map<String, Object> sendMail(String to, String subject, String html) {
        if (!mailEnabled) {
            log.info("[MAIL] 邮件未启用 — mail.enabled=false, to={} subject={}", to, subject);
            return Map.of("ok", false, "message", "邮件服务未启用（mail.enabled=false）");
        }
        if (mailSender == null) {
            log.warn("[MAIL] JavaMailSender 未配置 — 请在 application.yml 中配置 spring.mail.*");
            return Map.of("ok", false, "message", "SMTP 未配置");
        }
        if (to == null || to.isBlank()) {
            log.warn("[MAIL] 收件人为空，跳过发送 subject={}", subject);
            return Map.of("ok", false, "message", "收件人为空");
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(mailFrom.isBlank() ? "noreply@hr-recruit.com" : mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("[MAIL] 发送成功: to={} subject={}", to, subject);
            return Map.of("ok", true, "message", "发送成功");
        } catch (Exception e) {
            log.error("[MAIL] 发送失败: to={} subject={} error={}", to, subject, e.getMessage());
            return Map.of("ok", false, "message", "发送失败: " + e.getMessage());
        }
    }

    /**
     * 面试邀请邮件。
     *
     * @param to              收件人
     * @param candidateName   候选人
     * @param position        岗位
     * @param interviewDate   面试时间
     * @param roundName       轮次
     * @param meetingUrl      会议链接（可空）
     * @param meetingCode     会议号（可空）
     */
    public Map<String, Object> sendInterviewInviteEmail(String to, String candidateName,
                                                         String position, String interviewDate,
                                                         String roundName, String meetingUrl,
                                                         String meetingCode) {
        String meetingInfo = "";
        if (meetingUrl != null && !meetingUrl.isBlank()) {
            meetingInfo += "<p>会议链接：<a href=\"" + meetingUrl + "\">" + meetingUrl + "</a></p>";
        }
        if (meetingCode != null && !meetingCode.isBlank()) {
            meetingInfo += "<p>会议号：" + meetingCode + "</p>";
        }
        String html = """
                <html><body style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                <h2>面试邀请</h2>
                <p>候选人：%s</p>
                <p>岗位：%s</p>
                <p>时间：%s</p>
                <p>轮次：%s</p>
                %s
                <hr>
                <p style="color: #8C95A6; font-size: 12px;">此邮件由招聘系统自动发送</p>
                </body></html>
                """.formatted(
                escapeHtml(candidateName), escapeHtml(position),
                escapeHtml(interviewDate), escapeHtml(roundName), meetingInfo);
        return sendMail(to, "【面试邀请】" + candidateName + " - " + position, html);
    }

    /**
     * Offer 倒计时提醒邮件。
     *
     * @param to         收件人（HR）
     * @param candidate  候选人姓名
     * @param position   岗位
     * @param daysLeft   剩余天数
     * @param deadline   截止日期
     */
    public Map<String, Object> sendOfferReminderEmail(String to, String candidate,
                                                       String position, int daysLeft,
                                                       String deadline) {
        String html = """
                <html><body style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                <h2>Offer 倒计时提醒</h2>
                <p>候选人 <strong>%s</strong> 的 Offer 还剩 <strong>%d 天</strong> 确认期。</p>
                <p>岗位：%s</p>
                <p>截止时间：%s</p>
                <p style="color: %s;">%s</p>
                <hr>
                <p style="color: #8C95A6; font-size: 12px;">此邮件由招聘系统自动发送</p>
                </body></html>
                """.formatted(
                escapeHtml(candidate), daysLeft, escapeHtml(position), deadline,
                daysLeft <= 1 ? "#EF4444" : "#F59E0B",
                daysLeft <= 1 ? "⚠ 即将过期，请尽快确认！" : "请及时跟进候选人确认意向");
        return sendMail(to, "【Offer提醒】" + candidate + " 还剩 " + daysLeft + " 天", html);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
