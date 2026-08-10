package com.hr.config.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.AesUtil;
import com.hr.config.entity.AiKnowledgeBase;
import com.hr.config.entity.ApiKeyConfig;
import com.hr.config.entity.AuditLog;
import com.hr.config.entity.NotifyTemplate;
import com.hr.config.entity.RecruitChannel;
import com.hr.config.entity.RecruitMailAccount;
import com.hr.config.entity.RoleMenuPermission;
import com.hr.config.entity.ScoreRule;
import com.hr.config.repository.AiKnowledgeBaseRepository;
import com.hr.config.repository.ApiKeyConfigRepository;
import com.hr.config.repository.AuditLogRepository;
import com.hr.config.repository.NotifyTemplateRepository;
import com.hr.config.repository.RecruitChannelRepository;
import com.hr.config.repository.RecruitMailAccountRepository;
import com.hr.config.repository.RoleMenuPermissionRepository;
import com.hr.config.repository.ScoreRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final RoleMenuPermissionRepository roleMenuPermissionRepository;

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

    /** AES 加密密钥：优先环境变量，其次固定默认值（对齐 Flask SECRET_KEY）。 */
    private String cryptoSecret() {
        String env = System.getenv("PASSWORD_SALT");
        return (env != null && !env.isBlank()) ? env : "default-salt-change-me";
    }

    // ── 渠道 ────────────────────────────────────────────────

    @Cacheable(cacheNames = "config", key = "'channels'")
    public List<Map<String, Object>> getChannels() {
        // 用 ArrayList 而非 Stream.toList()：后者返回 final 类型 ListN，
        // GenericJackson2JsonRedisSerializer 的 NON_FINAL 类型标记不会写入，
        // 导致缓存反序列化失败
        return new java.util.ArrayList<>(channelRepository.findByIsDeletedOrderByIdAsc(0)
                .stream().map(this::channelToMap).toList());
    }

    @Transactional
    @CacheEvict(cacheNames = "config", key = "'channels'")
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
    @CacheEvict(cacheNames = "config", key = "'channels'")
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

    /**
     * GET /api/config/email-accounts/resolve — 按邮箱域名识别服务商（MX 指纹/域名关键字兜底）。
     */
    public Map<String, Object> resolveEmailServer(String email) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", "未知");
        result.put("imap_host", "");
        result.put("imap_port", 993);
        result.put("encryption", "SSL/TLS");
        result.put("confidence", "low");
        result.put("detection", "unknown");

        String domain = "";
        if (email != null && email.contains("@")) {
            domain = email.substring(email.indexOf('@') + 1).trim().toLowerCase();
        }
        if (domain.isEmpty()) {
            return result;
        }

        // 域名关键字 → (服务商, IMAP 主机)，对齐 Flask _DOMAIN_KEYWORD_FALLBACK
        for (Map.Entry<String, String[]> e : DOMAIN_FALLBACK.entrySet()) {
            for (String kw : e.getValue()) {
                if (domain.contains(kw)) {
                    String[] p = e.getKey().split("\\|", 2);
                    result.put("provider", p[0]);
                    result.put("imap_host", p[1]);
                    result.put("confidence", "medium");
                    result.put("detection", "domain");
                    return result;
                }
            }
        }

        // 通用猜测 imap.<domain>
        if (domain.contains(".")) {
            result.put("provider", "企业邮箱");
            result.put("imap_host", "imap." + domain);
            result.put("confidence", "low");
            result.put("detection", "pattern");
        }
        return result;
    }

    private static final Map<String, String[]> DOMAIN_FALLBACK = buildDomainFallback();

    private static Map<String, String[]> buildDomainFallback() {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("腾讯企业邮|imap.exmail.qq.com", new String[]{"exmail.qq"});
        m.put("网易企业邮|imap.qiye.163.com", new String[]{"qiye.163"});
        m.put("QQ邮箱|imap.qq.com", new String[]{"qq"});
        m.put("163邮箱|imap.163.com", new String[]{"163"});
        m.put("126邮箱|imap.126.com", new String[]{"126"});
        m.put("Gmail|imap.gmail.com", new String[]{"gmail", "googlemail"});
        m.put("Outlook|outlook.office365.com", new String[]{"outlook", "hotmail", "live"});
        m.put("阿里企业邮|imap.qiye.aliyun.com", new String[]{"aliyun", "hichina"});
        m.put("飞书邮箱|imap.feishu.cn", new String[]{"feishu", "larksuite"});
        m.put("Zoho Mail|imap.zoho.com", new String[]{"zoho"});
        m.put("263企业邮箱|imap.263.net", new String[]{"263"});
        return m;
    }

    /**
     * GET /api/config/role-permissions — 角色菜单权限矩阵。
     */
    public List<Map<String, Object>> getRolePermissions() {
        List<RoleMenuPermission> rows = roleMenuPermissionRepository.findByIsDeletedOrderByRoleCodeAsc(0);
        Map<String, List<String>> dbMenus = new LinkedHashMap<>();
        for (RoleMenuPermission r : rows) {
            if (r.getEnabled() != null && r.getEnabled() == 1) {
                dbMenus.computeIfAbsent(r.getRoleCode(), k -> new java.util.ArrayList<>()).add(r.getMenuId());
            }
        }

        List<String> roles = List.of("admin", "hr", "dept_head", "employee", "interviewer", "no_recruit");
        List<String> allMenus = List.of("recruit-dashboard", "recruit-demand", "recruit-talent",
                "recruit-interview", "recruit-ai", "recruit-config");
        Map<String, String> menuLabels = new LinkedHashMap<>();
        menuLabels.put("recruit-dashboard", "招聘看板");
        menuLabels.put("recruit-demand", "招聘需求");
        menuLabels.put("recruit-talent", "人才库");
        menuLabels.put("recruit-interview", "面试管理");
        menuLabels.put("recruit-ai", "AI 助手");
        menuLabels.put("recruit-config", "系统配置");

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (String rk : roles) {
            List<String> menuIds = dbMenus.containsKey(rk) ? dbMenus.get(rk)
                    : com.hr.common.enums.RoleMenus.getMenus(rk);
            List<String> labels = new java.util.ArrayList<>();
            for (String m : menuIds) {
                labels.add(menuLabels.getOrDefault(m, m));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roleCode", rk);
            row.put("role", rk);
            row.put("badgeClass", "");
            row.put("menus", labels.isEmpty() ? "侧边栏隐藏" : String.join("、", labels));
            row.put("menuIds", menuIds);
            row.put("allMenuIds", allMenus);
            row.put("allMenuLabels", menuLabels);
            row.put("dataScope", "全部数据");
            row.put("ops", "查看");
            result.add(row);
        }
        return result;
    }

    /**
     * PUT /api/config/role-permissions — 保存角色菜单权限。
     */
    @Transactional
    public Map<String, Object> updateRolePermissions(Map<String, Object> data) {
        if (data == null) {
            data = new LinkedHashMap<>();
        }
        List<RoleMenuPermission> old = roleMenuPermissionRepository.findByIsDeletedOrderByRoleCodeAsc(0);
        for (RoleMenuPermission r : old) {
            r.setIsDeleted(1);
            r.setUpdatedAt(LocalDateTime.now());
        }
        roleMenuPermissionRepository.saveAll(old);

        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            String roleCode = e.getKey();
            Object v = e.getValue();
            if (!(v instanceof List<?> menuIds)) {
                continue;
            }
            for (Object mid : menuIds) {
                RoleMenuPermission p = new RoleMenuPermission();
                p.setRoleCode(roleCode);
                p.setMenuId(String.valueOf(mid));
                p.setEnabled(1);
                p.setCreatedAt(now);
                p.setUpdatedAt(now);
                p.setIsDeleted(0);
                roleMenuPermissionRepository.save(p);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", true);
        result.put("roles", new java.util.ArrayList<>(data.keySet()));
        return result;
    }

    public List<Map<String, Object>> getEmailAccounts() {
        return mailAccountRepository.findByIsDeletedOrderByIdAsc(0)
                .stream().map(this::mailAccountToMap).toList();
    }

    /**
     * POST /api/config/email-accounts — 创建邮箱账号（对齐 Flask create_email_account）。
     */
    @Transactional
    public Map<String, Object> createEmailAccount(Map<String, Object> body) {
        String address = body.get("address") == null ? "" : String.valueOf(body.get("address")).trim();
        if (address.isEmpty() || !address.contains("@")) {
            throw BusinessException.invalidInput("请输入有效的邮箱地址");
        }

        RecruitMailAccount existing = mailAccountRepository.findByEmailAddress(address).orElse(null);
        if (existing != null) {
            if (existing.getIsDeleted() != null && existing.getIsDeleted() == 1) {
                // 复用软删除账号：重新启用并应用新配置
                existing.setIsDeleted(0);
                if (body.get("server") != null) existing.setImapHost(String.valueOf(body.get("server")));
                if (body.get("port") != null) existing.setImapPort(toInt(body.get("port"), null));
                if (body.get("pass") != null && !String.valueOf(body.get("pass")).isBlank()) {
                    existing.setPasswordEncrypted(encryptMailPassword(String.valueOf(body.get("pass"))));
                }
                if (body.get("type") != null) existing.setMailType(String.valueOf(body.get("type")));
                existing.setMonitorFolder(body.get("folder") != null ? String.valueOf(body.get("folder")) : "INBOX");
                existing.setStatus(1);
                existing.setLastSyncTime(null);
                existing.setUpdatedAt(LocalDateTime.now());
                mailAccountRepository.save(existing);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("created", true);
                r.put("id", existing.getId());
                r.put("address", address);
                r.put("reactivated", true);
                return r;
            }
            throw BusinessException.invalidInput("邮箱 " + address + " 已存在，请勿重复添加");
        }

        RecruitMailAccount account = new RecruitMailAccount();
        account.setEmailAddress(address);
        account.setAccountName(body.get("name") != null ? String.valueOf(body.get("name"))
                : (address.contains("@") ? address.split("@")[0] : address));
        account.setImapHost(body.get("server") != null ? String.valueOf(body.get("server")) : null);
        account.setImapPort(body.get("port") != null ? toInt(body.get("port"), null) : null);
        account.setOwnerUserId(body.get("owner_user_id") != null ? toLong(body.get("owner_user_id")) : null);
        account.setStatus(toInt(body.get("status"), 1));
        account.setMonitorFolder(body.get("folder") != null ? String.valueOf(body.get("folder")) : "INBOX");
        account.setMailType(body.get("type") != null ? String.valueOf(body.get("type")) : null);
        account.setSyncFreq(freqLabelToMinutes(String.valueOf(body.get("freq"))));
        account.setPasswordEncrypted(body.get("pass") != null && !String.valueOf(body.get("pass")).isBlank()
                ? encryptMailPassword(String.valueOf(body.get("pass"))) : null);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        account.setIsDeleted(0);
        mailAccountRepository.save(account);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", true);
        result.put("id", account.getId());
        result.put("address", address);
        return result;
    }

    /**
     * PUT /api/config/email-accounts/{id} — 更新邮箱账号（对齐 Flask update_email_account）。
     */
    @Transactional
    public Map<String, Object> updateEmailAccount(Long id, Map<String, Object> data) {
        RecruitMailAccount account = mailAccountRepository.findById(id)
                .filter(a -> a.getIsDeleted() == null || a.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("邮箱账号不存在: " + id));

        if (Boolean.TRUE.equals(data.get("_delete"))) {
            account.setIsDeleted(1);
            account.setUpdatedAt(LocalDateTime.now());
            mailAccountRepository.save(account);
            return Map.of("deleted", true, "account_id", id);
        }
        if (data.get("status") != null) {
            account.setStatus(parseStatus(data.get("status")));
        }
        if (data.get("address") != null) {
            account.setEmailAddress(String.valueOf(data.get("address")));
        }
        if (data.get("server") != null) {
            account.setImapHost(String.valueOf(data.get("server")));
        }
        if (data.get("port") != null && !String.valueOf(data.get("port")).isBlank()) {
            account.setImapPort(toInt(data.get("port"), null));
        }
        if (data.get("folder") != null) {
            account.setMonitorFolder(String.valueOf(data.get("folder")));
        }
        if (data.get("type") != null) {
            account.setMailType(String.valueOf(data.get("type")));
        }
        if (data.get("freq") != null) {
            account.setSyncFreq(freqLabelToMinutes(String.valueOf(data.get("freq"))));
        }
        if (data.get("pass") != null && !String.valueOf(data.get("pass")).isBlank()) {
            account.setPasswordEncrypted(encryptMailPassword(String.valueOf(data.get("pass"))));
        }
        account.setUpdatedAt(LocalDateTime.now());
        mailAccountRepository.save(account);
        return Map.of("updated", true, "id", id);
    }

    /**
     * DELETE /api/config/email-accounts/{id} — 软删除邮箱账号（对齐 Flask delete_email_account）。
     */
    @Transactional
    public Map<String, Object> deleteEmailAccount(Long id) {
        RecruitMailAccount account = mailAccountRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("邮箱账号不存在: " + id));
        account.setIsDeleted(1);
        account.setStatus(0);
        account.setUpdatedAt(LocalDateTime.now());
        mailAccountRepository.save(account);
        return Map.of("deleted", true, "account_id", id);
    }

    /**
     * POST /api/config/email-accounts/detect — 自动探测 IMAP 服务器。
     * 对齐 Flask detect_imap_server() 的 4 层回退策略。
     */
    public Map<String, Object> detectImapServer(String email) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", "未知");
        result.put("imap_host", "");
        result.put("imap_port", 993);
        result.put("detection", "unknown");

        if (email == null || !email.contains("@")) {
            return result;
        }
        String domain = email.substring(email.indexOf('@') + 1).trim().toLowerCase();

        // Tier 1: 域名精确匹配
        if (DOMAIN_IMAP_FALLBACK.containsKey(domain)) {
            String[] cfg = DOMAIN_IMAP_FALLBACK.get(domain);
            result.put("provider", cfg[0]);
            result.put("imap_host", cfg[1]);
            result.put("detection", "domain");
            return result;
        }

        // Tier 2: MX 指纹匹配（与 resolveEmailServer 共享 DOMAIN_FALLBACK 逻辑）
        Map<String, Object> resolved = resolveEmailServer(email);
        if (!"unknown".equals(resolved.get("detection"))) {
            result.put("provider", resolved.get("provider"));
            result.put("imap_host", resolved.get("imap_host"));
            result.put("detection", resolved.get("detection"));
            return result;
        }

        // Tier 3–4: 通用模式猜测
        result.put("imap_host", "imap." + domain);
        result.put("detection", "pattern");
        return result;
    }

    /**
     * POST /api/config/email-accounts/get-preview — 邮箱预览（不实际连接）。
     * 对齐 Flask POST /api/config/email-accounts/get-preview。
     */
    public List<Map<String, Object>> getEmailPreview() {
        List<RecruitMailAccount> accounts = mailAccountRepository.findByIsDeletedOrderByIdAsc(0);
        return accounts.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("account_name", a.getAccountName());
            m.put("email_address", a.getEmailAddress());
            m.put("imap_host", a.getImapHost() != null ? a.getImapHost() : "");
            m.put("status", a.getStatus() != null && a.getStatus() == 1 ? "active" : "inactive");
            m.put("last_sync_time", a.getLastSyncTime() != null ? a.getLastSyncTime().toString() : null);
            return m;
        }).toList();
    }

    /**
     * GET /api/config/tencent-meeting/status — 腾讯会议配置状态。
     */
    public Map<String, Object> getTencentMeetingStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        String appId = decryptStoredKey("tencent_meeting_app_id");
        String secret = decryptStoredKey("tencent_meeting");
        boolean configured = appId != null && !appId.isBlank() && secret != null && !secret.isBlank();
        result.put("configured", configured);
        result.put("message", configured ? "已配置" : "未配置腾讯会议密钥");
        return result;
    }

    /**
     * GET /api/config/feishu/status — 飞书配置状态。
     */
    public Map<String, Object> getFeishuStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        String appId = decryptStoredKey("feishu_app_id");
        String secret = decryptStoredKey("feishu");
        boolean configured = appId != null && !appId.isBlank() && secret != null && !secret.isBlank();
        result.put("configured", configured);
        result.put("message", configured ? "已配置" : "未配置飞书密钥");
        return result;
    }

    /**
     * POST /api/config/email-accounts/sync — 手动同步全部邮箱（返回统计摘要）。
     * 触发后台同步任务，当前由 AI 服务负责 IMAP 采集。
     */
    public Map<String, Object> syncAllEmailAccounts() {
        List<RecruitMailAccount> accounts = mailAccountRepository.findByIsDeletedOrderByIdAsc(0)
                .stream().filter(a -> a.getStatus() != null && a.getStatus() == 1).toList();
        int configured = 0;
        int noHost = 0;
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (RecruitMailAccount a : accounts) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", a.getId());
            r.put("address", a.getEmailAddress());
            if (a.getImapHost() == null || a.getImapHost().isBlank()) {
                r.put("status", "error");
                r.put("message", "未配置 IMAP 服务器地址，请先填写邮箱服务器信息");
                noHost++;
            } else {
                r.put("status", "accepted");
                r.put("message", "同步任务已提交");
                configured++;
                a.setLastSyncTime(now);
                a.setUpdatedAt(now);
                mailAccountRepository.save(a);
            }
            results.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("synced", configured);
        out.put("skipped", 0);
        out.put("failed", noHost);
        out.put("results", results);
        return out;
    }

    // DOMAIN_IMAP_FALLBACK for detectImapServer
    private static final Map<String, String[]> DOMAIN_IMAP_FALLBACK = Map.ofEntries(
            Map.entry("qq.com", new String[]{"QQ邮箱", "imap.qq.com"}),
            Map.entry("163.com", new String[]{"163邮箱", "imap.163.com"}),
            Map.entry("126.com", new String[]{"126邮箱", "imap.126.com"}),
            Map.entry("gmail.com", new String[]{"Gmail", "imap.gmail.com"}),
            Map.entry("outlook.com", new String[]{"Outlook", "outlook.office365.com"}),
            Map.entry("hotmail.com", new String[]{"Outlook", "outlook.office365.com"}),
            Map.entry("live.com", new String[]{"Outlook", "outlook.office365.com"}),
            Map.entry("aliyun.com", new String[]{"阿里企业邮箱", "imap.qiye.aliyun.com"}),
            Map.entry("exmail.qq.com", new String[]{"腾讯企业邮箱", "imap.exmail.qq.com"})
    );

    private String encryptMailPassword(String raw) {
        try {
            return AesUtil.encrypt(raw, cryptoSecret());
        } catch (Exception e) {
            return raw;
        }
    }

    private int freqLabelToMinutes(String label) {
        if (label == null || label.isBlank()) return 30;
        for (Map.Entry<Integer, String> e : MAIL_FREQ_LABELS.entrySet()) {
            if (e.getValue().equals(label)) return e.getKey();
        }
        try {
            return Integer.parseInt(label);
        } catch (NumberFormatException ex) {
            return 30;
        }
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

    /**
     * POST /api/config/notify-templates — 创建通知模板（对齐 Flask create_notify_template）。
     */
    @Transactional
    public Map<String, Object> createNotifyTemplate(Map<String, Object> body) {
        NotifyTemplate t = new NotifyTemplate();
        t.setTemplateName(body.get("name") != null ? String.valueOf(body.get("name")) : "");
        t.setTemplateType(body.get("type") != null ? String.valueOf(body.get("type")) : "通用");
        t.setSendMethod(body.get("method") != null ? String.valueOf(body.get("method")) : "");
        t.setSubject(body.get("subject") != null ? String.valueOf(body.get("subject")) : "");
        t.setBody(body.get("body") != null ? String.valueOf(body.get("body")) : "");
        t.setStatus(1);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        t.setIsDeleted(0);
        notifyTemplateRepository.save(t);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", true);
        result.put("id", t.getId());
        result.put("name", t.getTemplateName());
        return result;
    }

    /**
     * PUT /api/config/notify-templates/{id} — 更新通知模板（对齐 Flask update_notify_template）。
     */
    @Transactional
    public Map<String, Object> updateNotifyTemplate(Long id, Map<String, Object> data) {
        NotifyTemplate t = notifyTemplateRepository.findById(id)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("模板不存在: " + id));
        if (data.get("name") != null) t.setTemplateName(String.valueOf(data.get("name")));
        if (data.get("type") != null) t.setTemplateType(String.valueOf(data.get("type")));
        if (data.get("method") != null) t.setSendMethod(String.valueOf(data.get("method")));
        if (data.get("subject") != null) t.setSubject(String.valueOf(data.get("subject")));
        if (data.get("body") != null) t.setBody(String.valueOf(data.get("body")));
        t.setUpdatedAt(LocalDateTime.now());
        notifyTemplateRepository.save(t);
        return Map.of("updated", true, "id", id);
    }

    /**
     * DELETE /api/config/notify-templates/{id} — 软删除通知模板（对齐 Flask delete_notify_template）。
     */
    @Transactional
    public Map<String, Object> deleteNotifyTemplate(Long id) {
        NotifyTemplate t = notifyTemplateRepository.findById(id)
                .filter(x -> x.getIsDeleted() == null || x.getIsDeleted() == 0)
                .orElseThrow(() -> BusinessException.notFound("模板不存在: " + id));
        t.setIsDeleted(1);
        t.setStatus(0);
        t.setUpdatedAt(LocalDateTime.now());
        notifyTemplateRepository.save(t);
        return Map.of("deleted", true, "id", id);
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
    @CacheEvict(cacheNames = "config", key = "'knowledge-base'")
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

    @Cacheable(cacheNames = "config", key = "'score-rules'")
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

    /**
     * PUT /api/config/score-rules — 更新打分规则（对齐 Flask update_score_rules）。
     */
    @Transactional
    public Map<String, Object> updateScoreRules(Map<String, Object> data) {
        if (data == null) {
            data = new LinkedHashMap<>();
        }
        ScoreRule rule = scoreRuleRepository.findFirstByStatusAndIsDeletedOrderByIdDesc(1, 0)
                .orElse(null);
        if (rule == null) {
            rule = new ScoreRule();
            rule.setScoreScene(1);
            rule.setPoolMinScore(new BigDecimal("60"));
            rule.setStatus(1);
            rule.setIsDeleted(0);
            rule.setCreatedAt(LocalDateTime.now());
        }
        rule.setUpdatedAt(LocalDateTime.now());

        Map<String, Object> w = parseJsonMap(rule.getWeightJson());
        for (String key : new String[]{"profileWeight", "matchWeight", "decay30",
                "decay90", "decayOver90", "topCount", "searchRange"}) {
            if (data.containsKey(key)) {
                w.put(key, data.get(key));
            }
        }
        rule.setWeightJson(toJson(w));
        if (data.containsKey("passLine")) {
            rule.setPoolMinScore(new BigDecimal(String.valueOf(data.get("passLine"))));
        }
        if (data.containsKey("autoInviteMinScore")) {
            Object v = data.get("autoInviteMinScore");
            rule.setAutoInviteMinScore(v != null ? new BigDecimal(String.valueOf(v)) : null);
        }
        if (data.containsKey("scoreScene")) {
            rule.setScoreScene(((Number) data.get("scoreScene")).intValue());
        }
        scoreRuleRepository.save(rule);

        Map<String, Object> result = new LinkedHashMap<>(data);
        result.put("updated", true);
        result.put("id", rule.getId());
        return result;
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

    /**
     * PUT /api/config/api-keys — 加密保存 API 密钥（对齐 Flask save_api_keys）。
     */
    @Transactional
    public Map<String, Object> saveApiKeys(Map<String, Object> data) {
        List<String> savedKeys = new java.util.ArrayList<>();
        for (String keyName : API_KEY_DISPLAY.keySet()) {
            Object raw = data.get(keyName);
            if (raw == null || String.valueOf(raw).isBlank()) {
                continue;
            }
            String value = String.valueOf(raw).trim();
            if (value.equals("••••••••")) {
                continue; // 掩码占位符，不重复加密
            }
            String encrypted = encryptMailPassword(value);
            ApiKeyConfig row = apiKeyRepository.findByKeyNameAndIsDeleted(keyName, 0).orElse(null);
            LocalDateTime now = LocalDateTime.now();
            if (row != null) {
                row.setValueEncrypted(encrypted);
                row.setUpdatedAt(now);
                apiKeyRepository.save(row);
            } else {
                ApiKeyConfig n = new ApiKeyConfig();
                n.setKeyName(keyName);
                n.setValueEncrypted(encrypted);
                n.setDisplayLabel(API_KEY_DISPLAY.get(keyName).label());
                n.setStatus(1);
                n.setCreatedAt(now);
                n.setUpdatedAt(now);
                n.setIsDeleted(0);
                apiKeyRepository.save(n);
            }
            savedKeys.add(keyName);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("keys", savedKeys);
        return result;
    }

    /**
     * POST /api/config/api-keys/test — 密钥连通性测试（对齐 Flask test_api_key）。
     * deepseek 走真实 HTTP 探测；其余仅返回配置状态。
     */
    public Map<String, Object> testApiKey(String keyName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("supported", true);
        result.put("message", "");
        result.put("source", null);

        if (!List.of("deepseek", "feishu", "dify").contains(keyName)) {
            result.put("supported", false);
            result.put("message", "该密钥暂不支持连通性测试");
            return result;
        }
        if ("dify".equals(keyName)) {
            result.put("supported", false);
            result.put("message", "Dify 为兼容保留配置，暂不支持连通性测试");
            return result;
        }

        if ("deepseek".equals(keyName)) {
            String env = System.getenv("DEEPSEEK_API_KEY");
            String stored = env != null && !env.isBlank() ? env : decryptStoredKey(keyName);
            result.put("source", env != null && !env.isBlank() ? "env" : (stored != null ? "db" : null));
            if (stored == null || stored.isBlank()) {
                result.put("message", "未配置密钥，请先保存");
                return result;
            }
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL("https://api.deepseek.com/models").openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + stored);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int status = conn.getResponseCode();
                if (status == 200) {
                    result.put("ok", true);
                    result.put("message", "连接成功，密钥可用");
                } else if (status == 401 || status == 403) {
                    result.put("message", "密钥无效或已过期（HTTP " + status + "）");
                } else {
                    result.put("message", "服务返回 HTTP " + status);
                }
                conn.disconnect();
            } catch (Exception e) {
                result.put("message", "网络请求失败：" + e.getClass().getSimpleName());
            }
            return result;
        }

        // feishu：app_id/secret 配对
        String appId = decryptStoredKey("feishu_app_id");
        String secret = decryptStoredKey("feishu");
        if (secret == null || secret.isBlank()) {
            result.put("message", "未配置 App Secret，请先保存");
            return result;
        }
        if (appId == null || appId.isBlank()) {
            result.put("message", "未配置 App ID，请先保存");
            return result;
        }
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
                            .openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(("{\"app_id\":\"" + appId + "\",\"app_secret\":\"" + secret + "\"}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status == 200) {
                result.put("ok", true);
                result.put("message", "连接成功，密钥可用");
            } else {
                result.put("message", "服务返回 HTTP " + status);
            }
            conn.disconnect();
        } catch (Exception e) {
            result.put("message", "网络请求失败：" + e.getClass().getSimpleName());
        }
        return result;
    }

    private String decryptStoredKey(String keyName) {
        ApiKeyConfig row = apiKeyRepository.findByKeyNameAndIsDeleted(keyName, 0).orElse(null);
        if (row == null || row.getValueEncrypted() == null || row.getValueEncrypted().isEmpty()) {
            return null;
        }
        try {
            return AesUtil.decrypt(row.getValueEncrypted(), cryptoSecret());
        } catch (Exception e) {
            return null;
        }
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

    private Long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o != null) {
            try {
                return Long.parseLong(String.valueOf(o));
            } catch (NumberFormatException e) {
                // fallthrough
            }
        }
        return null;
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

    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
