package com.hr.dashboard.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 招聘看板聚合服务，对齐 Flask dashboard_service.py。
 * 全部使用原生 SQL 聚合，DB 异常时返回空结构保持看板可用。
 */
@Slf4j
@Service
public class DashboardService {

    private static final Map<Integer, String> DEPT_FALLBACK = Map.of(
            1, "技术部", 2, "产品部", 3, "运营部", 4, "数据部", 5, "财务部");

    private static final Map<String, String> CHANNEL_DISPLAY = Map.of(
            "邮箱", "邮箱采集", "Boss", "Boss 直聘", "猎聘", "猎聘", "内推", "内推");

    @PersistenceContext
    private EntityManager entityManager;

    // ── KPI（按角色） ─────────────────────────────────────────

    public List<Map<String, Object>> getKpi(String role, Long userId) {
        try {
            long candidates = count("SELECT COUNT(*) FROM t_hr_candidate WHERE status != 'archived' AND is_deleted = 0");
            long openDemands = count("SELECT COUNT(*) FROM t_hr_recruit_demand WHERE demand_status = 2 AND is_deleted = 0");
            long monthlyHires = count("SELECT COUNT(*) FROM t_hr_entry WHERE entry_date >= :ms AND is_deleted = 0",
                    Map.of("ms", LocalDate.now().withDayOfMonth(1)));
            long pendingApprovals = count("SELECT COUNT(*) FROM t_hr_recruit_demand WHERE demand_status = 1 AND is_deleted = 0");
            long totalInterviews = count("SELECT COUNT(*) FROM t_hr_interview_book WHERE is_deleted = 0");
            long pendingEvals = count("""
                    SELECT COUNT(*) FROM t_hr_interview_book b
                    INNER JOIN t_hr_interview_record r ON r.book_id = b.id
                    WHERE (r.evaluate_text IS NULL OR r.evaluate_text = '')
                    AND b.is_deleted = 0 AND r.is_deleted = 0""");

            List<Map<String, Object>> kpis = new ArrayList<>();
            switch (role == null ? "admin" : role) {
                case "hr" -> kpis.addAll(List.of(
                        kpi(totalInterviews, "全部待面试", "实时"),
                        kpi(pendingEvals, "待评价面试", "实时"),
                        kpi(pendingApprovals, "待审批岗位", "实时"),
                        kpi(monthlyHires, "本月入职总量", "实时")));
                case "interviewer" -> kpis.addAll(interviewerKpi(userId));
                default -> kpis.addAll(List.of(
                        kpi(totalInterviews, "全公司待面试", "实时"),
                        kpi(pendingEvals, "待评价", "实时"),
                        kpi(openDemands, "在招岗位", "实时"),
                        kpi(monthlyHires, "本月入职总量", "实时")));
            }
            return kpis;
        } catch (Exception e) {
            log.warn("Dashboard KPI query failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> interviewerKpi(Long userId) {
        long myPending = 0, myEvals = 0, myCompleted = 0;
        double avgScore = 0;
        if (userId != null) {
            myPending = count("""
                    SELECT COUNT(*) FROM t_hr_interview_book b
                    INNER JOIN t_hr_interview_slot s ON s.id = b.slot_id
                    WHERE s.interviewer_id = :uid AND b.is_deleted = 0 AND s.is_deleted = 0""", Map.of("uid", userId));
            myEvals = count("""
                    SELECT COUNT(*) FROM t_hr_interview_book b
                    INNER JOIN t_hr_interview_slot s ON s.id = b.slot_id
                    INNER JOIN t_hr_interview_record r ON r.book_id = b.id
                    WHERE s.interviewer_id = :uid
                    AND (r.evaluate_text IS NULL OR r.evaluate_text = '')
                    AND b.is_deleted = 0 AND s.is_deleted = 0 AND r.is_deleted = 0""", Map.of("uid", userId));
            myCompleted = count("""
                    SELECT COUNT(*) FROM t_hr_interview_book b
                    INNER JOIN t_hr_interview_slot s ON s.id = b.slot_id
                    INNER JOIN t_hr_interview_record r ON r.book_id = b.id
                    WHERE s.interviewer_id = :uid
                    AND r.evaluate_text IS NOT NULL AND r.evaluate_text != ''
                    AND b.is_deleted = 0 AND s.is_deleted = 0 AND r.is_deleted = 0""", Map.of("uid", userId));
            try {
                Object avg = entityManager.createNativeQuery("""
                        SELECT AVG(CAST(JSON_EXTRACT(r.score_json, '$.total') AS DECIMAL))
                        FROM t_hr_interview_record r
                        INNER JOIN t_hr_interview_book b ON b.id = r.book_id
                        INNER JOIN t_hr_interview_slot s ON s.id = b.slot_id
                        WHERE s.interviewer_id = :uid AND r.score_json IS NOT NULL
                        AND b.is_deleted = 0 AND r.is_deleted = 0 AND s.is_deleted = 0""")
                        .setParameter("uid", userId)
                        .getSingleResult();
                if (avg != null) {
                    avgScore = Math.round(((Number) avg).doubleValue() * 10) / 10.0;
                }
            } catch (Exception ignored) {
            }
        }
        return List.of(
                kpi(myPending, "本人待面试", "实时"),
                kpi(myEvals, "待评价", "实时"),
                kpi(avgScore, "均分", "—"),
                kpi(myCompleted, "已完成", "实时"));
    }

    // ── 招聘漏斗 ──────────────────────────────────────────────

    public Map<String, Object> getFunnel() {
        try {
            long resumes = count("SELECT COUNT(*) FROM t_hr_resume WHERE is_deleted = 0");
            if (resumes == 0) {
                resumes = count("SELECT COUNT(*) FROM t_hr_candidate WHERE status != 'archived' AND is_deleted = 0");
            }
            long screened = count("SELECT COUNT(DISTINCT candidate_id) FROM t_hr_recruit_process WHERE process_status >= 1 AND is_deleted = 0");
            long interviewed = count("SELECT COUNT(*) FROM t_hr_interview_book WHERE is_deleted = 0");
            long offered = count("SELECT COUNT(*) FROM t_hr_offer WHERE is_deleted = 0");
            long hired = count("SELECT COUNT(*) FROM t_hr_entry WHERE is_deleted = 0");

            long total = Math.max(resumes, 1);
            List<Map<String, Object>> stages = new ArrayList<>();
            stages.add(stage("收简历", resumes, total, null, "/recruit-talent", "HR 团队",
                    "简历池共 " + resumes + " 份，建议持续拓展招聘渠道。"));
            stages.add(stage("筛选通过", screened, total, resumes, "/recruit-demand", "用人部门 · HR",
                    "筛选通过 " + screened + " 人，通过率 " + pct(screened, total) + "。"));
            stages.add(stage("面试", interviewed, total, screened, "/recruit-interview", "面试官团队",
                    "共 " + interviewed + " 场面试安排。"));
            stages.add(stage("Offer", offered, total, interviewed, "/recruit-interview", "HR · 用人经理",
                    "已发放 " + offered + " 份 Offer。"));
            stages.add(stage("入职", hired, total, offered, "/recruit-demand", "HR 团队",
                    "本月已入职 " + hired + " 人。"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("period", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));
            result.put("overallRate", pct(hired, total));
            result.put("stages", stages);
            return result;
        } catch (Exception e) {
            log.warn("Dashboard funnel query failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> stage(String label, long count, long total, Long prevCount,
                                      String link, String owner, String note) {
        String conv = (prevCount != null && prevCount > 0 && count > 0)
                ? String.format("%.1f%%", count * 100.0 / prevCount) : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("count", count);
        m.put("pct", pct(count, total));
        m.put("link", link);
        m.put("conv", conv);
        m.put("wow", "--");
        m.put("wowUp", true);
        m.put("dwell", "--");
        m.put("health", health(conv));
        m.put("owner", owner);
        m.put("spark", genSpark(count));
        m.put("note", note);
        m.put("bottleneck", conv != null && parsePct(conv) < 20);
        return m;
    }

    // ── 部门编制进度 ──────────────────────────────────────────

    public List<Map<String, Object>> getDeptProgress() {
        try {
            List<?> rows = entityManager.createNativeQuery("""
                    SELECT dept_id,
                           SUM(COALESCE(plan_headcount, 0)) AS total_hc,
                           SUM(COALESCE(filled_count, 0)) AS filled_hc
                    FROM t_hr_recruit_demand
                    WHERE demand_status = 2 AND is_deleted = 0 AND plan_headcount > 0
                    GROUP BY dept_id
                    ORDER BY dept_id""").getResultList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Object row : rows) {
                Object[] cols = (Object[]) row;
                long deptId = ((Number) cols[0]).longValue();
                long totalHc = ((Number) cols[1]).longValue();
                long filledHc = ((Number) cols[2]).longValue();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("dept", resolveDeptName(deptId));
                m.put("hired", filledHc);
                m.put("total", totalHc);
                m.put("pct", totalHc > 0 ? Math.round(filledHc * 100.0 / totalHc) : 0);
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            log.warn("Dashboard dept progress query failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── 渠道统计 ──────────────────────────────────────────────

    public List<Map<String, Object>> getChannel() {
        try {
            List<?> rows = entityManager.createNativeQuery("""
                    SELECT c.source_channel,
                           COUNT(DISTINCT c.id) AS resume_cnt,
                           COUNT(DISTINCT CASE WHEN p.id IS NOT NULL AND p.process_status >= 1 THEN c.id END) AS pass_cnt,
                           COUNT(DISTINCT b.id) AS interview_cnt,
                           COUNT(DISTINCT e.id) AS hire_cnt
                    FROM t_hr_candidate c
                    LEFT JOIN t_hr_recruit_process p ON p.candidate_id = c.id AND p.is_deleted = 0
                    LEFT JOIN t_hr_resume r ON r.candidate_id = c.id AND r.is_deleted = 0
                    LEFT JOIN t_hr_interview_book b ON b.resume_id = r.id AND b.is_deleted = 0
                    LEFT JOIN t_hr_entry e ON e.resume_id = r.id AND e.is_deleted = 0
                    WHERE c.is_deleted = 0 AND c.source_channel IS NOT NULL
                    GROUP BY c.source_channel
                    ORDER BY c.source_channel""").getResultList();

            if (rows.isEmpty()) {
                return fallbackChannels();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object row : rows) {
                Object[] cols = (Object[]) row;
                String channel = String.valueOf(cols[0]);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("channel", CHANNEL_DISPLAY.getOrDefault(channel, channel));
                m.put("resume", ((Number) cols[1]).longValue());
                m.put("pass", ((Number) cols[2]).longValue());
                m.put("interview", ((Number) cols[3]).longValue());
                m.put("hire", ((Number) cols[4]).longValue());
                m.put("cost", "--");
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            log.warn("Dashboard channel query failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> fallbackChannels() {
        try {
            List<?> names = entityManager.createNativeQuery(
                    "SELECT channel_name FROM t_hr_recruit_channel WHERE status = 1").getResultList();
            if (names.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object name : names) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("channel", CHANNEL_DISPLAY.getOrDefault(String.valueOf(name), String.valueOf(name)));
                m.put("resume", 0L);
                m.put("pass", 0L);
                m.put("interview", 0L);
                m.put("hire", 0L);
                m.put("cost", "--");
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────

    private long count(String sql, Object... params) {
        var q = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        Object r = q.getSingleResult();
        return r != null ? ((Number) r).longValue() : 0L;
    }

    private Map<String, Object> kpi(long val, String label, String trend) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("val", val);
        m.put("label", label);
        m.put("trend", trend);
        return m;
    }

    private Map<String, Object> kpi(double val, String label, String trend) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("val", val);
        m.put("label", label);
        m.put("trend", trend);
        return m;
    }

    private String resolveDeptName(long deptId) {
        try {
            Object name = entityManager.createNativeQuery(
                    "SELECT dept_name FROM t_core_dept WHERE dept_id = :did AND is_deleted = 0 LIMIT 1")
                    .setParameter("did", deptId)
                    .getSingleResult();
            if (name != null) {
                return String.valueOf(name);
            }
        } catch (Exception ignored) {
        }
        return DEPT_FALLBACK.getOrDefault((int) deptId, "部门(" + deptId + ")");
    }

    /** 将总数均分为 7 点分布，对齐 Flask _gen_spark。 */
    private List<Long> genSpark(long count) {
        List<Long> spark = new ArrayList<>();
        if (count <= 0) {
            for (int i = 0; i < 7; i++) {
                spark.add(0L);
            }
            return spark;
        }
        long base = count / 7;
        long extra = count % 7;
        for (int i = 0; i < 7; i++) {
            spark.add(base + (i < extra ? 1 : 0));
        }
        return spark;
    }

    private static String pct(long n, long total) {
        if (total <= 0) {
            return "0%";
        }
        return String.format("%.1f%%", n * 100.0 / total);
    }

    private static String health(String conv) {
        if (conv == null) {
            return "good";
        }
        double v = parsePct(conv);
        if (v >= 30) {
            return "good";
        }
        return v >= 15 ? "watch" : "risk";
    }

    private static double parsePct(String conv) {
        try {
            return Double.parseDouble(conv.replace("%", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
