package com.hr.bootstrap.scheduler;

import com.hr.config.service.ConfigService;
import com.hr.hire.service.HireService;
import com.hr.integration.feishu.FeishuClient;
import com.hr.interview.service.InterviewService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务调度器，对齐 Flask Celery Beat 的 3 个周期任务。
 *
 * Flask Beat 对照：
 *   sync-email-tick            → 15 min  → syncEmailTick()
 *   check-overdue-evaluations  → 1 hour  → checkOverdueEvaluations()
 *   offer-confirm-countdown    → 1 hour  → offerFollowup()
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final ConfigService configService;
    private final InterviewService interviewService;
    private final HireService hireService;
    private final FeishuClient feishuClient;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 邮箱同步：每 15 分钟执行一次 IMAP 邮件采集。
     */
    @Scheduled(initialDelay = 120_000, fixedRate = 900_000)
    public void syncEmailTick() {
        try {
            Map<String, Object> result = configService.syncAllEmailAccounts();
            log.info("[Scheduled] 邮箱同步完成: {}", result);
        } catch (Exception e) {
            log.error("[Scheduled] 邮箱同步异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 面试逾期检查：每 1 小时扫描超过 3 天未评价的面试。
     * 发送飞书提醒给对应面试官（best-effort，不抛异常）。
     */
    @Scheduled(initialDelay = 300_000, fixedRate = 3_600_000)
    public void checkOverdueEvaluations() {
        try {
            List<Map<String, Object>> alerts = interviewService.getAlerts();
            long overdueCount = alerts.stream()
                    .filter(a -> "reject".equals(a.get("type")))
                    .count();
            if (overdueCount == 0) {
                log.info("[Scheduled] 面试逾期检查: 无逾期");
                return;
            }

            log.warn("[Scheduled] 面试逾期检查: 发现 {} 条超期未评价", overdueCount);

            // 按面试官分组，每人发一条飞书提醒
            Map<String, OverdueGroup> groups = new LinkedHashMap<>();
            for (Map<String, Object> a : alerts) {
                if (!"reject".equals(a.get("type"))) {
                    continue;
                }
                String text = String.valueOf(a.getOrDefault("text", ""));
                String interviewer = extractInterviewer(text);
                groups.computeIfAbsent(interviewer, k -> new OverdueGroup()).count++;
            }

            for (var entry : groups.entrySet()) {
                String interviewerName = entry.getKey();
                int count = entry.getValue().count;

                // 查面试官的飞书 open_id
                String openId = resolveFeishuOpenId(interviewerName);
                Map<String, Object> result = feishuClient.sendOverdueAlert(interviewerName, openId, count);
                log.info("[Scheduled] 逾期告警: 面试官={} 逾期={}条 发送={}",
                        interviewerName, count,
                        Boolean.TRUE.equals(result.get("success")) ? "成功" : "跳过(" + result.get("reason") + ")");
            }
        } catch (Exception e) {
            log.error("[Scheduled] 面试逾期检查异常: {}", e.getMessage(), e);
        }
    }

    /**
     * Offer 巡检：每 1 小时检查已发送 Offer 的倒计时，自动淘汰逾期 Offer。
     * 提醒邮件由 HireService.offerFollowup() 内部记录 OfferRemindLog，
     * 实际邮件发送待 MailService 配置后启用。
     */
    @Scheduled(initialDelay = 480_000, fixedRate = 3_600_000)
    public void offerFollowup() {
        try {
            Map<String, Object> result = hireService.offerFollowup();
            log.info("[Scheduled] Offer 巡检完成: {}", result);
        } catch (Exception e) {
            log.error("[Scheduled] Offer 巡检异常: {}", e.getMessage(), e);
        }
    }

    // ── 私有 ────────────────────────────────────────────────────

    /**
     * 从告警文本 "张三 · 面试超5天未评价" 中提取面试官姓名。
     */
    private String extractInterviewer(String alertText) {
        if (alertText == null || alertText.isBlank()) {
            return "未知";
        }
        int sep = alertText.indexOf("·");
        if (sep > 0) {
            return alertText.substring(0, sep).trim();
        }
        return alertText;
    }

    /**
     * 按面试官姓名查 feishu_open_id（从 IamUser 表）。
     */
    private String resolveFeishuOpenId(String name) {
        if (name == null || name.isBlank() || "未知".equals(name)) {
            return null;
        }
        try {
            List<?> rows = entityManager.createNativeQuery(
                    "SELECT feishu_open_id FROM t_hr_iam_user " +
                    "WHERE real_name = :name AND status = 1 AND is_deleted = 0 LIMIT 1")
                    .setParameter("name", name)
                    .getResultList();
            if (!rows.isEmpty() && rows.get(0) != null) {
                return String.valueOf(rows.get(0));
            }
        } catch (Exception e) {
            log.debug("查询飞书 openId 失败: name={} error={}", name, e.getMessage());
        }
        return null;
    }

    private static class OverdueGroup {
        int count;
    }
}
