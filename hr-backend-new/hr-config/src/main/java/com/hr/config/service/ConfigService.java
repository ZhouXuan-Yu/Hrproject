package com.hr.config.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.common.exception.BusinessException;
import com.hr.config.entity.AiKnowledgeBase;
import com.hr.config.entity.ApiKeyConfig;
import com.hr.config.entity.AuditLog;
import com.hr.config.entity.NotifyTemplate;
import com.hr.config.entity.RecruitChannel;
import com.hr.config.entity.RecruitMailAccount;
import com.hr.config.entity.ScoreRule;
import com.hr.config.repository.AiKnowledgeBaseRepository;
import com.hr.config.repository.ApiKeyConfigRepository;
import com.hr.config.repository.AuditLogRepository;
import com.hr.config.repository.NotifyTemplateRepository;
import com.hr.config.repository.RecruitChannelRepository;
import com.hr.config.repository.RecruitMailAccountRepository;
import com.hr.config.repository.ScoreRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基础配置服务，对齐 Flask config_service.py。
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final RecruitChannelRepository channelRepository;
    private final RecruitMailAccountRepository mailAccountRepository;
    private final NotifyTemplateRepository notifyTemplateRepository;
    private final AiKnowledgeBaseRepository knowledgeBaseRepository;
    private final ScoreRuleRepository scoreRuleRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApiKeyConfigRepository apiKeyRepository;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final DateTimeFormatter MONTH_DAY_HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");

    // ── 渠道映射（对齐 Flask 内内存映射表） ─────────────────────
    private static final Map<String, String> CHANNEL_COST_MAP = new LinkedHashMap<>(Map.of(
            "BOSS", "¥8,000", "LIEPIN", "¥12,000", "EMAIL", "¥0", "NEITUI", "¥3,000"));

    private static final Map<String, String> CHANNEL_CODE_MAP = new LinkedHashMap<>(Map.of(
            "Boss直聘", "BOSS", "猎聘", "LIEPIN", "邮箱采集", "EMAIL", "内部推荐", "NEITUI"));

    private static final Map<String, String> REVERSE_CHANNEL_CODE = new LinkedHashMap<>(Map.of(
            "BOSS", "Boss直聘", "LIEPIN", "猎聘", "EMAIL", "邮箱采集", "NEITUI", "内部推荐"));

    private static final Map<String, String> CHANNEL_TYPE_OVERRIDE = Map.of(
            "BOSS", "招聘平台", "LIEPIN", "猎头平台", "EMAIL", "自动管道", "NEITUI", "内部渠道");

    private static final Map<Integer, String> CHANNEL_TYPE_LABELS = Map.of(
            1, "官网渠道", 2, "第三方平台", 3, "内部渠道");

    private static final Map<Integer, String> MAIL_FREQ_LABELS = Map.of(
            0, "手动", 15, "每 15 分钟", 30, "每 30 分钟", 60, "每 60 分钟",
            120, "每 2 小时", 360, "每 6 小时", 1440, "每天");

    private static final Map<String, String> DEFAULT_KNOWLEDGE = new LinkedHashMap<>();
    static {
        DEFAULT_KNOWLEDGE.put("companyName", "XX公司");
        DEFAULT_KNOWLEDGE.put("industry", "企业服务 / 智能招聘");
        DEFAULT_KNOWLEDGE.put("website", "");
        DEFAULT_KNOWLEDGE.put("companyProfile", "XX公司是一家重视长期主义、工程质量和业务结果的企业，招聘流程以岗位胜任力、候选人体验和合规留痕为基础。");
        DEFAULT_KNOWLEDGE.put("hiringPrinciples", "岗位分析应基于真实业务目标、团队阶段、必要技能和可验证经历；沟通候选人时保持专业、克制、尊重隐私。");
        DEFAULT_KNOWLEDGE.put("aiContext", "所有 AI 分析默认代表 XX公司，不夸大公司规模，不编造福利，不生成歧视性或无法验证的判断。");
    }

    private record ApiKeyDisplay(String label, String desc, String link, String linkText) {}

    private static final Map<String, ApiKeyDisplay> API_KEY_DISPLAY = new LinkedHashMap<>();
    static {
        API_KEY_DISPLAY.put("deepseek", new ApiKeyDisplay(
                "DeepSeek API Key", "AI 简历解析、匹配打分、JD生成",
                "https://platform.deepseek.com/api_keys", "去 DeepSeek 开放平台获取 Key"));
        API_KEY_DISPLAY.put("feishu_app_id", new ApiKeyDisplay(
                "飞书 App ID", "消息通知、审批推送（与 App Secret 配对使用）",
                "https://open.feishu.cn/app", "去飞书开放平台创建应用，获取 App ID 与 App Secret"));
        API_KEY_DISPLAY.put("feishu", new ApiKeyDisplay(
                "飞书 App Secret", "消息通知、审批推送（与 App ID 配对使用）",
                "https://open.feishu.cn/app", "去飞书开放平台创建应用，获取 App ID 与 App Secret"));
        API_KEY_DISPLAY.put("dify", new ApiKeyDisplay("Dify API Key", "Dify 工作流引擎（兼容保留）", null, null));
        API_KEY_DISPLAY.put("tencent_appid", new ApiKeyDisplay("腾讯会议 AppId", "腾讯会议开放平台应用 AppId", null, null));
        API_KEY_DISPLAY.put("tencent_secretid", new ApiKeyDisplay("腾讯会议 SecretId", "腾讯会议开放平台 SecretId", null, null));
        API_KEY_DISPLAY.put("tencent_secretkey", new ApiKeyDisplay("腾讯会议 SecretKey", "腾讯会议开放平台 SecretKey（加密存储）", null, null));
        API_KEY_DISPLAY.put("tencent_userid", new ApiKeyDisplay("腾讯会议主持人 UserID", "创建会议时的主持人 userid（必填）", null, null));
    }

    // ── 渠道 ────────────────────────────────────────────────

    @Cacheable(cacheNames = "config", key = "'channels'")
    public List<Map<String, Object>> getChannels() {
        return channelRepository.findByIsDeletedOrderByIdAsc(0)
                .stream().map(this::channelToMap).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "config", allEntries = true)
    public Map<String, Object> createChannel(Map<String, Object> body) {
        String name = body.get("name") == null ? null : String.valueOf(body.get("name")).trim();
        if (name == null || name.isEmpty()) {
            throw BusinessException.invalidInput("渠道名称不能为空");
        }
        Integer chType = 2; // 默认第三方平台
        String type = body.get("type") == null ? null : String.valueOf(body.get("type"));
        if ("内部渠道".equals(type) || "内推".equals(type)) {
            chType = 3;
        } else if ("官网渠道".equals(type)) {
            chType = 1;
        }
        RecruitChannel channel = new RecruitChannel();
        channel.setChannelName(name);
        channel.setChannelType(chType);
        channel.setStatus(toInt(body.get("status"), 1));
        channel.setCreatedAt(LocalDateTime.now());
        channel.setUpdatedAt(LocalDateTime.now());
        channel.setIsDeleted(0);
        channelRepository.save(channel);

        if (body.get("cost") != null) {
            CHANNEL_COST_MAP.put(name.toUpperCase(), String.valueOf(body.get("cost")));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", true);
        result.put("id", channel.getId());
        result.put("name", name);
        return result;
    }

    @Transactional
    @CacheEvict(cacheNames = "config", allEntries = true)
    public Map<String, Object> updateChannel(String code, Map<String, Object> body) {
        String name = REVERSE_CHANNEL_CODE.getOrDefault(code, code);
        RecruitChannel channel = channelRepository.findByChannelNameAndIsDeleted(name, 0)
                .orElseThrow(() -> BusinessException.notFound("渠道不存在: " + code));

        if (body.get("cost") != null) {
            CHANNEL_COST_MAP.put(code, String.valueOf(body.get("cost")));
        }
        if (body.get("status") != null) {
            channel.setStatus(parseStatus(body.get("status")));
        }
        if (body.get("name") != null) {
            String newName = String.valueOf(body.get("name"));
            channel.setChannelName(newName);
            CHANNEL_CODE_MAP.put(newName, code);
            REVERSE_CHANNEL_CODE.put(code, newName);
        }
        channel.setUpdatedAt(LocalDateTime.now());
        channelRepository.save(channel);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", true);
        result.put("code", code);
        return result;
    }

    private Map<String, Object> channelToMap(RecruitChannel channel) {
        String name = channel.getChannelName();
        String code = CHANNEL_CODE_MAP.getOrDefault(name, name);
        String type = CHANNEL_TYPE_OVERRIDE.getOrDefault(code,
                CHANNEL_TYPE_LABELS.getOrDefault(channel.getChannelType(), "未知"));
        String cost = CHANNEL_COST_MAP.getOrDefault(code, "¥0");
        String status = channel.getStatus() != null && channel.getStatus() == 1 ? "启用" : "停用";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", channel.getId());
        m.put("code", code);
        m.put("name", name);
        m.put("type", type);
        m.put("cost", cost);
        m.put("status", status);
        return m;
    }

    // ── 邮箱账号 ────────────────────────────────────────────

    public List<Map<String, Object>> getEmailAccounts() {
        return mailAccountRepository.findByIsDeletedOrderByIdAsc(0)
                .stream().map(this::mailAccountToMap).toList();
    }

    private Map<String, Object> mailAccountToMap(RecruitMailAccount account) {
        int freqMinutes = account.getSyncFreq() != null ? account.getSyncFreq() : 30;
        String freqLabel = MAIL_FREQ_LABELS.getOrDefault(freqMinutes, "每 " + freqMinutes + " 分钟");
        boolean enabled = account.getStatus() != null && account.getStatus() == 1;
        LocalDateTime lastSyncDt = account.getLastSyncTime() != null ? account.getLastSyncTime() : account.getUpdatedAt();
        String lastSync = lastSyncDt != null ? lastSyncDt.format(MONTH_DAY_HM) : "—";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", account.getId());
        m.put("address", account.getEmailAddress());
        m.put("type", account.getMailType() != null ? account.getMailType() : "企业邮箱");
        m.put("freq", freqLabel);
        m.put("status", enabled ? "正常" : "异常");
        m.put("statusColor", enabled ? "done" : "warn");
        m.put("lastSync", lastSync);
        m.put("proto", "IMAP（推荐）");
        m.put("server", account.getImapHost() != null ? account.getImapHost() : "");
        m.put("port", account.getImapPort() != null ? String.valueOf(account.getImapPort()) : "993");
        m.put("ssl", "SSL/TLS");
        m.put("folder", account.getMonitorFolder() != null ? account.getMonitorFolder() : "INBOX");
        m.put("syncFreqMin", freqMinutes);
        return m;
    }

    // ── 通知模板 ────────────────────────────────────────────

    public List<Map<String, Object>> getNotifyTemplates() {
        return notifyTemplateRepository.findByStatusAndIsDeletedOrderByUpdatedAtDesc(1, 0)
                .stream().map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getTemplateName());
                    m.put("type", t.getTemplateType());
                    m.put("method", t.getSendMethod() != null ? t.getSendMethod() : "—");
                    m.put("subject", t.getSubject() != null ? t.getSubject() : "");
                    m.put("body", t.getBody() != null ? t.getBody() : "");
                    m.put("updated", t.getUpdatedAt() != null ? t.getUpdatedAt().format(MONTH_DAY) : "—");
                    return m;
                }).toList();
    }

    // ── AI 知识库 ───────────────────────────────────────────

    @Cacheable(cacheNames = "config", key = "'knowledge-base'")
    public Map<String, Object> getKnowledgeBase() {
        Optional<AiKnowledgeBase> row = knowledgeBaseRepository.findFirstByStatusAndIsDeletedOrderByIdDesc(1, 0);
        Map<String, Object> m = new LinkedHashMap<>();
        if (row.isPresent()) {
            AiKnowledgeBase kb = row.get();
            m.put("companyName", kb.getCompanyName() != null ? kb.getCompanyName() : DEFAULT_KNOWLEDGE.get("companyName"));
            m.put("industry", kb.getIndustry() != null ? kb.getIndustry() : "");
            m.put("website", kb.getWebsite() != null ? kb.getWebsite() : "");
            m.put("companyProfile", kb.getCompanyProfile() != null ? kb.getCompanyProfile() : "");
            m.put("hiringPrinciples", kb.getHiringPrinciples() != null ? kb.getHiringPrinciples() : "");
            m.put("aiContext", kb.getAiContext() != null ? kb.getAiContext() : "");
        } else {
            m.putAll(DEFAULT_KNOWLEDGE);
        }
        return m;
    }

    @Transactional
    @CacheEvict(cacheNames = "config", allEntries = true)
    public Map<String, Object> updateKnowledgeBase(Map<String, Object> body) {
        AiKnowledgeBase kb = knowledgeBaseRepository.findFirstByStatusAndIsDeletedOrderByIdDesc(1, 0)
                .orElseGet(() -> {
                    AiKnowledgeBase n = new AiKnowledgeBase();
                    n.setStatus(1);
                    n.setIsDeleted(0);
                    n.setCreatedAt(LocalDateTime.now());
                    return n;
                });

        String companyName = pick(body, "companyName", "company_name");
        kb.setCompanyName(companyName != null ? companyName : DEFAULT_KNOWLEDGE.get("companyName"));
        String industry = pick(body, "industry");
        kb.setIndustry(industry != null ? industry : "");
        String website = pick(body, "website");
        kb.setWebsite(website != null ? website : "");
        String profile = pick(body, "companyProfile", "company_profile");
        kb.setCompanyProfile(profile != null ? profile : "");
        String principles = pick(body, "hiringPrinciples", "hiring_principles");
        kb.setHiringPrinciples(principles != null ? principles : "");
        String aiContext = pick(body, "aiContext", "ai_context");
        kb.setAiContext(aiContext != null ? aiContext : "");
        kb.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseRepository.save(kb);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", true);
        result.put("data", getKnowledgeBase());
        return result;
    }

    // ── 打分规则 ───────────────────────────────────────────

    public Map<String, Object> getScoreRules() {
        Optional<ScoreRule> ruleOpt = scoreRuleRepository.findFirstByStatusAndIsDeletedOrderByIdDesc(1, 0);
        Map<String, Object> m = new LinkedHashMap<>();
        if (ruleOpt.isEmpty()) {
            m.put("id", 1);
            m.put("profileWeight", 0.10);
            m.put("matchWeight", 0.90);
            m.put("decay30", 1.0);
            m.put("decay90", 0.85);
            m.put("decayOver90", 0.70);
            m.put("passLine", 60);
            m.put("topCount", 5);
            m.put("searchRange", "近 3 个月");
            return m;
        }
        ScoreRule rule = ruleOpt.get();
        Map<String, Object> w = parseJsonMap(rule.getWeightJson());
        m.put("id", rule.getId());
        m.put("profileWeight", num(w.get("profileWeight"), 0.10));
        m.put("matchWeight", num(w.get("matchWeight"), 0.90));
        m.put("decay30", num(w.get("decay30"), 1.0));
        m.put("decay90", num(w.get("decay90"), 0.85));
        m.put("decayOver90", num(w.get("decayOver90"), 0.70));
        m.put("passLine", rule.getPoolMinScore() != null ? rule.getPoolMinScore().doubleValue() : 60.0);
        m.put("topCount", intNum(w.get("topCount"), 5));
        m.put("searchRange", w.get("searchRange") != null ? String.valueOf(w.get("searchRange")) : "近 3 个月");
        if (rule.getAutoInviteMinScore() != null) {
            m.put("autoInviteMinScore", rule.getAutoInviteMinScore().doubleValue());
        }
        if (rule.getScoreScene() != null) {
            m.put("scoreScene", rule.getScoreScene());
        }
        return m;
    }

    // ── 审计日志 ───────────────────────────────────────────

    public List<Map<String, Object>> getAuditLogs(int limit) {
        List<AuditLog> logs = auditLogRepository.findByIsDeletedOrderByOperateTimeDesc(0);
        if (logs.size() > limit) {
            logs = logs.subList(0, limit);
        }
        return logs.stream().map(al -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", al.getId());
            m.put("time", al.getOperateTime() != null ? al.getOperateTime().format(MONTH_DAY_HM) : "—");
            m.put("user", al.getOperatorName());
            m.put("module", al.getModule());
            m.put("action", al.getAction());
            m.put("detail", al.getDetail() != null ? al.getDetail() : "");
            return m;
        }).toList();
    }

    // ── API 密钥（只返回掩码，绝不返回明文） ────────────────

    public Map<String, Object> getApiKeys() {
        Map<String, Object> result = new LinkedHashMap<>();
        API_KEY_DISPLAY.forEach((keyName, display) -> {
            Optional<ApiKeyConfig> row = apiKeyRepository.findByKeyNameAndIsDeleted(keyName, 0);
            boolean hasValue = row.isPresent()
                    && row.get().getValueEncrypted() != null
                    && !row.get().getValueEncrypted().isEmpty();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key_name", keyName);
            item.put("label", display.label());
            item.put("desc", display.desc());
            item.put("masked", hasValue ? maskValue(row.get().getValueEncrypted()) : "••••••••");
            item.put("has_value", hasValue);
            item.put("link", display.link());
            item.put("link_text", display.linkText());
            result.put(keyName, item);
        });
        return result;
    }

    private String maskValue(String encrypted) {
        if (encrypted.length() <= 8) {
            return "••••••••";
        }
        return encrypted.substring(0, 4) + "••••••••" + encrypted.substring(encrypted.length() - 4);
    }

    // ── 工具方法 ────────────────────────────────────────────

    private Integer toInt(Object o, Integer def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o));
            } catch (NumberFormatException e) {
                // fallthrough
            }
        }
        return def;
    }

    private Integer parseStatus(Object raw) {
        if (raw instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (raw instanceof Number n) {
            return n.intValue() == 0 ? 0 : 1;
        }
        String s = String.valueOf(raw);
        return ("1".equals(s) || "启用".equals(s) || "true".equalsIgnoreCase(s)) ? 1 : 0;
    }

    private double num(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o));
            } catch (NumberFormatException e) {
                // fallthrough
            }
        }
        return def;
    }

    private int intNum(Object o, int def) {
        return (int) num(o, def);
    }

    private String pick(Map<String, Object> body, String... keys) {
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v).trim();
            }
        }
        return null;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
