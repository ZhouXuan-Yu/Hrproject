package com.hr.bootstrap.scheduler;

import com.hr.config.service.ConfigService;
import com.hr.hire.service.HireService;
import com.hr.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 定时任务调度器，对齐 Flask Celery Beat 的 3 个周期任务。
 *
 * Flask Beat 对照：
 *   sync-email-tick          → 15 min  → syncEmailTick()
 *   check-overdue-evaluations → 1 hour  → checkOverdueEvaluations()
 *   offer-confirm-countdown   → 1 hour  → offerFollowup()
 *
 * 使用 off-minute 调度避免整点/半点请求集中。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final ConfigService configService;
    private final InterviewService interviewService;
    private final HireService hireService;

    /**
     * 邮箱同步：每 15 分钟执行一次 IMAP 邮件采集。
     * 首次延迟 120 秒（等待服务完全预热）。
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
     * 面试逾期检查：每 1 小时扫描超过 3 天未评价的面试，发送飞书提醒。
     * 首次延迟 300 秒。
     */
    @Scheduled(initialDelay = 300_000, fixedRate = 3_600_000)
    public void checkOverdueEvaluations() {
        try {
            List<Map<String, Object>> alerts = interviewService.getAlerts();
            long overdueCount = alerts.stream()
                    .filter(a -> "reject".equals(a.get("type")))
                    .count();
            if (overdueCount > 0) {
                log.warn("[Scheduled] 面试逾期检查: 发现 {} 条超期未评价", overdueCount);
                alerts.stream()
                        .filter(a -> "reject".equals(a.get("type")))
                        .forEach(a -> log.info("[Scheduled] 逾期: {}", a.get("text")));
            } else {
                log.info("[Scheduled] 面试逾期检查: 无逾期");
            }
        } catch (Exception e) {
            log.error("[Scheduled] 面试逾期检查异常: {}", e.getMessage(), e);
        }
    }

    /**
     * Offer 巡检：每 1 小时检查已发送 Offer 的倒计时，发送提醒邮件并自动淘汰逾期 Offer。
     * 首次延迟 480 秒。
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
}
