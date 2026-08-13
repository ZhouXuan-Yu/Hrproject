package com.hr.bootstrap.listener;

import com.hr.common.event.InterviewEmailEvent;
import com.hr.common.event.OfferEmailEvent;
import com.hr.bootstrap.service.MailService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 统一邮件事件监听器 — 接收 Interview/Offer 邮件事件，解析候选人邮箱后调 MailService。
 * 所有邮件发送均为 best-effort：失败只记日志，不抛异常。
 */
@Slf4j
@Component
public class EmailEventListener {

    private final MailService mailService;

    @PersistenceContext
    private EntityManager entityManager;

    public EmailEventListener(MailService mailService) {
        this.mailService = mailService;
    }

    @EventListener
    public void onInterviewEmail(InterviewEmailEvent event) {
        try {
            String email = resolveEmail(event.getBookId());
            if (email == null || email.isBlank()) {
                log.info("[EMAIL] 跳过 {} — 候选人无邮箱 bookId={}", event.getType(), event.getBookId());
                return;
            }
            if ("invite".equals(event.getType())) {
                mailService.sendInterviewInviteEmail(email, event.getCandidateName(),
                        event.getPosition(), event.getInterviewTime(), event.getRound(),
                        event.getMethod(), event.getMeetingUrl(), event.getMeetingCode(),
                        event.getBookId());
                log.info("[EMAIL] 面试邀请邮件已发送: bookId={} to={}", event.getBookId(), email);
            } else if ("reject".equals(event.getType())) {
                mailService.sendInterviewRejectEmail(email, event.getCandidateName(),
                        event.getPosition());
                log.info("[EMAIL] 面试淘汰邮件已发送: bookId={} to={}", event.getBookId(), email);
            }
        } catch (Exception e) {
            log.warn("[EMAIL] 面试邮件发送失败 (best-effort): type={} bookId={} error={}",
                    event.getType(), event.getBookId(), e.getMessage());
        }
    }

    @EventListener
    public void onOfferEmail(OfferEmailEvent event) {
        try {
            String email = resolveEmailByResume(event.getResumeId());
            if (email == null || email.isBlank()) {
                log.info("[EMAIL] 跳过 offer — 候选人无邮箱 offerNo={}", event.getOfferNo());
                return;
            }
            mailService.sendOfferEmail(email, event.getCandidateName(), event.getOfferNo(),
                    event.getOfferContent(), event.getValidDeadline());
            log.info("[EMAIL] Offer 邮件已发送: offerNo={} to={}", event.getOfferNo(), email);
        } catch (Exception e) {
            log.warn("[EMAIL] Offer 邮件发送失败 (best-effort): offerNo={} error={}",
                    event.getOfferNo(), e.getMessage());
        }
    }

    /** 通过面试 book_id 解析候选人邮箱。 */
    private String resolveEmail(Long bookId) {
        try {
            Object result = entityManager.createNativeQuery(
                    "SELECT c.email FROM t_hr_candidate c " +
                    "JOIN t_hr_resume r ON r.candidate_id = c.id " +
                    "JOIN t_hr_interview_book b ON b.resume_id = r.id " +
                    "WHERE b.id = :bookId AND b.is_deleted = 0 AND r.is_deleted = 0 AND c.is_deleted = 0 LIMIT 1")
                    .setParameter("bookId", bookId)
                    .getSingleResult();
            return result != null ? String.valueOf(result) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 通过 resume_id 解析候选人邮箱。 */
    private String resolveEmailByResume(Long resumeId) {
        if (resumeId == null || resumeId <= 0) return null;
        try {
            Object result = entityManager.createNativeQuery(
                    "SELECT c.email FROM t_hr_candidate c " +
                    "JOIN t_hr_resume r ON r.candidate_id = c.id " +
                    "WHERE r.id = :resumeId AND r.is_deleted = 0 AND c.is_deleted = 0 LIMIT 1")
                    .setParameter("resumeId", resumeId)
                    .getSingleResult();
            return result != null ? String.valueOf(result) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
