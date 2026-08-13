package com.hr.bootstrap.service;

import com.hr.common.util.AesUtil;
import com.hr.config.entity.AiKnowledgeBase;
import com.hr.config.entity.NotifyTemplate;
import com.hr.config.entity.RecruitMailAccount;
import com.hr.config.repository.AiKnowledgeBaseRepository;
import com.hr.config.repository.NotifyTemplateRepository;
import com.hr.config.repository.RecruitMailAccountRepository;
import com.hr.talent.entity.MailLog;
import com.hr.talent.repository.MailLogRepository;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 通用邮件发送服务，对齐 Flask mail_sender.py + confirm_service.py。
 *
 * 发件逻辑：
 * - 优先从 t_hr_recruit_mail_account 表读取启用的邮箱账号（与 Python _pick_sender_account 对齐）
 * - 支持 AES-256-GCM 密文密码解密（兼容 Flask crypto_utils）
 * - 所有发送 best-effort，失败只记日志不抛异常
 */
@Slf4j
@Service
public class MailService {

    private final RecruitMailAccountRepository mailAccountRepository;
    private final AiKnowledgeBaseRepository knowledgeBaseRepository;
    private final NotifyTemplateRepository notifyTemplateRepository;
    private final MailLogRepository mailLogRepository;

    @Value("${crypto.secret-key:${SECRET_KEY:default-salt-change-me}}")
    private String cryptoSecret;

    @Value("${company.name:}")
    private String companyNameProp;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.base-url:http://127.0.0.1:8080}")
    private String baseUrl;

    public MailService(RecruitMailAccountRepository mailAccountRepository,
                       AiKnowledgeBaseRepository knowledgeBaseRepository,
                       NotifyTemplateRepository notifyTemplateRepository,
                       MailLogRepository mailLogRepository) {
        this.mailAccountRepository = mailAccountRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.notifyTemplateRepository = notifyTemplateRepository;
        this.mailLogRepository = mailLogRepository;
    }

    // ── SMTP 预设（对齐 Python _SMTP_PRESETS）─────────────────────────────

    private static final Map<String, SmtpPreset> SMTP_PRESETS = new LinkedHashMap<>();
    static {
        SMTP_PRESETS.put("qq",       new SmtpPreset("smtp.qq.com", 465, true));
        SMTP_PRESETS.put("qq.com",   new SmtpPreset("smtp.qq.com", 465, true));
        SMTP_PRESETS.put("exmail.qq.com", new SmtpPreset("smtp.exmail.qq.com", 465, true));
        SMTP_PRESETS.put("163",      new SmtpPreset("smtp.163.com", 465, true));
        SMTP_PRESETS.put("163.com",  new SmtpPreset("smtp.163.com", 465, true));
        SMTP_PRESETS.put("126.com",  new SmtpPreset("smtp.126.com", 465, true));
        SMTP_PRESETS.put("gmail",    new SmtpPreset("smtp.gmail.com", 587, false));
        SMTP_PRESETS.put("gmail.com",new SmtpPreset("smtp.gmail.com", 587, false));
        SMTP_PRESETS.put("corp",     new SmtpPreset("smtp.exmail.qq.com", 465, true));
    }

    private record SmtpPreset(String host, int port, boolean useSsl) {}

    // ── 账号选择 + 密码解密（对齐 Python _pick_sender_account / _get_password）──

    private RecruitMailAccount pickSenderAccount() {
        List<RecruitMailAccount> accounts = mailAccountRepository
                .findByIsDeletedOrderByIdAsc(0);
        return accounts.stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == 1)
                .filter(a -> a.getPasswordEncrypted() != null && !a.getPasswordEncrypted().isBlank())
                .findFirst()
                .orElse(null);
    }

    private String decryptPassword(RecruitMailAccount account) {
        String raw = account.getPasswordEncrypted();
        if (raw == null || raw.isBlank()) return null;
        try {
            if (raw.startsWith("enc:")) {
                return AesUtil.decrypt(raw.substring(4), cryptoSecret);
            }
            // 兼容历史数据（尝解密，失败则明文兜底）
            try {
                return AesUtil.decrypt(raw, cryptoSecret);
            } catch (Exception ignored) {
                return raw;
            }
        } catch (Exception e) {
            log.warn("邮件账号 {} 密码解密失败: {}", account.getId(), e.getMessage());
            return null;
        }
    }

    private SmtpPreset resolveSmtp(RecruitMailAccount account) {
        // 环境变量覆盖（对齐 Flask，支持非标企业服务器）
        String envHost = System.getenv("MAIL_SMTP_HOST");
        if (envHost != null && !envHost.isBlank()) {
            int envPort = 465;
            boolean envSsl = true;
            try {
                String portStr = System.getenv("MAIL_SMTP_PORT");
                if (portStr != null && !portStr.isBlank()) envPort = Integer.parseInt(portStr);
            } catch (NumberFormatException ignored) {}
            String sslStr = System.getenv("MAIL_SMTP_SSL");
            if (sslStr != null && "false".equalsIgnoreCase(sslStr.trim())) envSsl = false;
            return new SmtpPreset(envHost, envPort, envSsl);
        }
        String mailType = account.getMailType() != null ? account.getMailType().trim().toLowerCase() : "";
        String email = account.getEmailAddress();
        String domain = email != null && email.contains("@")
                ? email.substring(email.indexOf('@') + 1).toLowerCase() : "";
        // mail_type 精确匹配
        if (SMTP_PRESETS.containsKey(mailType)) {
            return SMTP_PRESETS.get(mailType);
        }
        // 域名匹配（含子域名如 exmail.qq.com → smtp.exmail.qq.com）
        if (!domain.isBlank() && SMTP_PRESETS.containsKey(domain)) {
            return SMTP_PRESETS.get(domain);
        }
        // corp 类型默认 腾讯企业邮箱
        if ("corp".equals(mailType)) {
            return new SmtpPreset("smtp.exmail.qq.com", 465, true);
        }
        return new SmtpPreset("smtp." + domain, 465, true);
    }

    // ── 公司名 ─────────────────────────────────────────────────────────────

    private String companyName() {
        // 1) 数据库 t_hr_ai_knowledge_base（对齐 Python config_service 读 company_name）
        try {
            AiKnowledgeBase kb = knowledgeBaseRepository
                    .findFirstByStatusAndIsDeletedOrderByIdDesc(1, 0).orElse(null);
            if (kb != null && kb.getCompanyName() != null && !kb.getCompanyName().isBlank()) {
                return kb.getCompanyName().trim();
            }
        } catch (Exception ignored) {
            // DB 不可用时降级
        }
        // 2) 配置文件 company.name
        if (companyNameProp != null && !companyNameProp.isBlank()) return companyNameProp;
        // 3) 环境变量 COMPANY_NAME
        String env = System.getenv("COMPANY_NAME");
        if (env != null && !env.isBlank()) return env;
        // 4) 兜底
        return "XX公司";
    }

    // ── 模板渲染（对齐 Python _render_notify_template / _render_text / _template_matches）──

    private static final Map<String, String[]> KIND_KEYWORDS = Map.of(
            "interview", new String[]{"interview", "invite", "面试", "邀请"},
            "offer", new String[]{"offer", "录用", "入职"},
            "reject", new String[]{"reject", "fail", "未通过", "不通过", "婉拒", "淘汰", "结果"},
            "remind", new String[]{"remind", "提醒"}
    );

    private static final Map<String, String[]> VAR_ALIASES;

    static {
        java.util.LinkedHashMap<String, String[]> m = new java.util.LinkedHashMap<>();
        m.put("candidate", new String[]{"candidate", "name", "候选人", "候选人姓名"});
        m.put("company", new String[]{"company", "公司"});
        m.put("position", new String[]{"position", "岗位", "应聘岗位"});
        m.put("time", new String[]{"time", "时间", "面试时间"});
        m.put("method", new String[]{"method", "方式", "面试方式"});
        m.put("round", new String[]{"round", "轮次", "面试轮次"});
        m.put("meeting_url", new String[]{"meeting_url", "meetingUrl", "meetingURL", "meeting_link", "meetingLink", "会议链接", "面试链接", "视频链接"});
        m.put("address", new String[]{"address", "地点", "面试地点"});
        m.put("hr", new String[]{"hr", "HR", "hr"});
        m.put("confirm_url", new String[]{"confirm_url", "url", "链接", "确认链接"});
        m.put("offer_no", new String[]{"offer_no", "offerNo", "offer号", "offer编号", "录用编号"});
        m.put("offer_content", new String[]{"offer_content", "offerContent", "录用内容", "offer内容", "offer正文"});
        m.put("deadline", new String[]{"deadline", "截止", "有效期", "截止日期", "截止时间"});
        m.put("comment", new String[]{"comment", "评价", "原因"});
        VAR_ALIASES = java.util.Collections.unmodifiableMap(m);
    }

    /** 匹配模板类型（对齐 Python _template_matches） */
    private boolean templateMatches(NotifyTemplate t, String kind) {
        String text = String.join(" ",
                t.getTemplateType() != null ? t.getTemplateType() : "",
                t.getTemplateName() != null ? t.getTemplateName() : "",
                t.getSubject() != null ? t.getSubject() : "").toLowerCase();
        String[] keywords = KIND_KEYWORDS.getOrDefault(kind, new String[]{kind});
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /** 变量替换（对齐 Python _render_text） */
    private String renderText(String template, Map<String, String> context) {
        String rendered = template != null ? template : "";
        for (var entry : VAR_ALIASES.entrySet()) {
            String value = context.getOrDefault(entry.getKey(), "");
            if (value == null) value = "";
            for (String alias : entry.getValue()) {
                rendered = rendered.replace("{{" + alias + "}}", value);
                rendered = rendered.replace("{" + alias + "}", value);
            }
        }
        return rendered;
    }

    /**
     * 从 DB 读取通知模板渲染，没有则返回默认值。
     * 对齐 Python _render_notify_template。
     * @return [subject, html]
     */
    private String[] renderNotifyTemplate(String kind, String defaultSubject, String defaultHtml,
                                          Map<String, String> context) {
        try {
            List<NotifyTemplate> templates = notifyTemplateRepository
                    .findByStatusAndIsDeletedOrderByUpdatedAtDesc(1, 0);
            NotifyTemplate match = templates.stream()
                    .filter(t -> templateMatches(t, kind))
                    .findFirst().orElse(null);
            if (match == null) {
                return new String[]{defaultSubject, defaultHtml};
            }

            String subject = renderText(
                    match.getSubject() != null && !match.getSubject().isBlank()
                            ? match.getSubject() : defaultSubject, context);
            String body = renderText(match.getBody() != null ? match.getBody() : "", context);
            if (body.isBlank()) {
                return new String[]{subject, defaultHtml};
            }
            // 纯文本 → HTML（对齐 Python _plain_text_to_html）
            if (!body.matches("(?s).*<[a-zA-Z][\\s\\S]*?>.*")) {
                body = "<p>" + body.replace("\n\n", "</p><p>").replace("\n", "<br>") + "</p>";
            }
            // 自动追加确认按钮（对齐 Python）
            String confirmUrl = context.get("confirm_url");
            if (confirmUrl != null && !confirmUrl.isBlank() && !body.contains(confirmUrl)) {
                String label = kind.equals("offer") ? "查看并确认 Offer" : "确认面试安排";
                body += """
                        <p style="text-align:center;margin:24px 0">
                          <a href="%s" style="background:#4F6EF7;color:#fff;padding:12px 32px;border-radius:8px;text-decoration:none;font-weight:bold">%s</a>
                        </p>""".formatted(escapeHtml(confirmUrl), label);
            }
            String html = """
                    <div style="font-family:Microsoft YaHei,sans-serif;max-width:600px;margin:0 auto;color:#333;line-height:1.7">
                      %s
                    </div>""".formatted(body);
            return new String[]{subject, html};
        } catch (Exception e) {
            log.warn("渲染通知模板失败 kind={}: {}", kind, e.getMessage());
            return new String[]{defaultSubject, defaultHtml};
        }
    }

    // ── 发送 ────────────────────────────────────────────────────────────────

    /**
     * 发送 HTML 邮件（对齐 Flask send_mail）。
     * @return {"ok": true/false, "message": "..."}
     */
    public Map<String, Object> sendMail(String to, String subject, String html) {
        return sendMail(to, subject, html, "other");
    }

    /**
     * 发送 HTML 邮件 + 写入 MailLog（对齐 Flask send_mail + _log_mail）。
     */
    public Map<String, Object> sendMail(String to, String subject, String html, String mailType) {
        RecruitMailAccount account = pickSenderAccount();
        if (account == null) {
            log.info("[MAIL] 跳过发送 — 未配置可用的发件邮箱");
            writeMailLog(null, to, subject, mailType, false,
                    "未配置可用的发件邮箱（需要有密码/授权码的启用账号）");
            return Map.of("ok", false, "message", "未配置可用的发件邮箱（需要有密码/授权码的启用账号）");
        }
        String password = decryptPassword(account);
        if (password == null || password.isBlank()) {
            log.info("[MAIL] 跳过发送 — 发件邮箱 {} 未配置密码/授权码", account.getEmailAddress());
            writeMailLog(account, to, subject, mailType, false,
                    "发件邮箱 " + account.getEmailAddress() + " 未配置密码/授权码");
            return Map.of("ok", false, "message", "发件邮箱 " + account.getEmailAddress() + " 未配置密码/授权码");
        }
        SmtpPreset smtp = resolveSmtp(account);
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtp.host);
            props.put("mail.smtp.port", String.valueOf(smtp.port));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.connectiontimeout", "20000");
            props.put("mail.smtp.timeout", "20000");
            if (smtp.useSsl) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            } else {
                props.put("mail.smtp.starttls.enable", "true");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(account.getEmailAddress(), password);
                }
            });

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(account.getEmailAddress(), "招聘中心", "UTF-8"));
            msg.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject(subject, "UTF-8");
            msg.setContent(html, "text/html;charset=UTF-8");
            msg.setSentDate(new Date());
            Transport.send(msg);

            log.info("[MAIL] 发送成功 via {}: to={} subject={}", account.getEmailAddress(), to, subject);
            writeMailLog(account, to, subject, mailType, true, null);
            return Map.of("ok", true, "message", "发送成功");
        } catch (Exception e) {
            log.error("[MAIL] 发送失败 via {}: to={} subject={} error={}",
                    account.getEmailAddress(), to, subject, e.getMessage());
            writeMailLog(account, to, subject, mailType, false, e.getMessage());
            return Map.of("ok", false, "message", "发送失败: " + e.getMessage());
        }
    }

    /**
     * 写入 t_hr_mail_log（对齐 Python _log_mail）。
     */
    private void writeMailLog(RecruitMailAccount account, String to, String subject,
                              String mailType, boolean success, String errorMsg) {
        try {
            MailLog ml = new MailLog();
            ml.setSenderAccountId(account != null ? account.getId() : null);
            ml.setSenderEmail(account != null ? account.getEmailAddress() : "");
            ml.setRecipient(to != null ? to : "");
            ml.setSubject(subject != null ? subject : "");
            ml.setMailType(mailType != null ? mailType : "other");
            ml.setStatus(success ? 1 : 0);
            if (errorMsg != null && !errorMsg.isEmpty()) {
                ml.setErrorMsg(errorMsg.length() > 512 ? errorMsg.substring(0, 512) : errorMsg);
            }
            ml.setCreatedAt(java.time.LocalDateTime.now());
            ml.setIsDeleted(0);
            mailLogRepository.save(ml);
        } catch (Exception e) {
            log.warn("[MAIL] 写入 MailLog 失败: {}", e.getMessage());
        }
    }

    // ── 业务邮件 ──────────────────────────────────────────────────────────

    /**
     * 发送面试邀请邮件（对齐 Python confirm_service send_interview_invite_email）。
     * @param bookId 面试预约 ID，用于生成候选人确认 token
     * @param method 面试方式，如 "飞书视频" / "线下面试" / "电话面试"
     */
    public Map<String, Object> sendInterviewInviteEmail(String to, String candidateName,
                                                         String position, String interviewDate,
                                                         String roundName, String method,
                                                         String meetingUrl, String meetingCode,
                                                         Long bookId) {
        String company = companyName();
        String methodDisplay = (method != null && !method.isBlank()) ? method : "待定";

        // 生成候选人确认链接
        String confirmUrl = "";
        if (bookId != null && bookId > 0) {
            try {
                String token = io.jsonwebtoken.Jwts.builder()
                        .claim("purpose", "candidate-confirm")
                        .claim("kind", "interview")
                        .claim("ref", String.valueOf(bookId))
                        .issuedAt(new java.util.Date())
                        .expiration(new java.util.Date(System.currentTimeMillis() + 7 * 86400_000L))
                        .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .compact();
                confirmUrl = baseUrl.replaceFirst("/$", "") + "/confirm/" + token;
            } catch (Exception e) {
                log.warn("生成面试确认 token 失败 bookId={}: {}", bookId, e.getMessage());
            }
        }

        // ── 构建上下文（对齐 Python context） ──
        Map<String, String> ctx = new java.util.LinkedHashMap<>();
        ctx.put("candidate", candidateName != null ? candidateName : "");
        ctx.put("company", company);
        ctx.put("position", position != null ? position : "");
        ctx.put("time", interviewDate != null ? interviewDate : "");
        ctx.put("method", methodDisplay);
        ctx.put("round", roundName != null ? roundName : "");
        ctx.put("meeting_url", meetingUrl != null ? meetingUrl : "");
        ctx.put("meetingUrl", meetingUrl != null ? meetingUrl : "");
        ctx.put("address", "");
        ctx.put("confirm_url", confirmUrl);
        ctx.put("hr", System.getenv().getOrDefault("HR_DISPLAY_NAME", "HR"));

        // ── 默认模板（兜底，对齐 Python fallback HTML） ──
        String linkRow = "";
        if (meetingUrl != null && !meetingUrl.isBlank()) {
            linkRow = "<tr><td style=\"padding:9px 14px;color:#888\">面试链接</td><td><a href=\""
                    + escapeHtml(meetingUrl) + "\" style=\"color:#4F6EF7\">"
                    + escapeHtml(meetingUrl) + "</a></td></tr>";
        }
        String confirmButton = "";
        if (!confirmUrl.isBlank()) {
            confirmButton = """
                    <p style="text-align:center;margin:24px 0">
                      <a href="%s" style="background:#4F6EF7;color:#fff;padding:12px 32px;border-radius:8px;text-decoration:none;font-weight:bold;font-size:15px">确认面试安排</a>
                    </p>
                    <p style="color:#999;font-size:12px;text-align:center">如按钮无法点击，请复制链接到浏览器打开：<br>%s</p>
                    """.formatted(confirmUrl, confirmUrl);
        }
        String defaultSubject = "【面试邀请】" + company + " - " + position + " - " + interviewDate;
        String defaultHtml = """
                <html><body style="font-family:Microsoft YaHei,sans-serif;max-width:600px;margin:0 auto;color:#333;line-height:1.7">
                <div style="background:#4F6EF7;padding:20px 28px;border-radius:8px 8px 0 0">
                  <div style="font-size:18px;font-weight:700;color:#fff">%s</div>
                  <div style="font-size:13px;color:rgba(255,255,255,.7)">面试邀请函</div>
                </div>
                <div style="padding:20px 28px;border:1px solid #e8ecf1;border-top:none;border-radius:0 0 8px 8px">
                <p>尊敬的%s：</p>
                <p>您好！</p>
                <p>感谢您对%s的关注与信任。经过简历评估，现诚邀您参加 <b>%s</b> 的面试，具体安排如下：</p>
                <table style="border-collapse:collapse;margin:14px 0;background:#f7f8fa;width:100%%">
                  <tr><td style="padding:9px 14px;color:#888;width:80px">面试岗位</td><td style="font-weight:600">%s</td></tr>
                  <tr><td style="padding:9px 14px;color:#888">面试时间</td><td style="font-weight:600">%s</td></tr>
                  <tr><td style="padding:9px 14px;color:#888">面试方式</td><td>%s</td></tr>
                  <tr><td style="padding:9px 14px;color:#888">面试轮次</td><td>%s</td></tr>
                  %s
                </table>
                <p>请于收到邮件后尽快通过下方按钮确认是否参加。如需调整面试时间，请回复邮件与我们沟通。</p>
                %s
                <p style="margin-top:20px;color:#8C95A6">温馨提示：</p>
                <p style="color:#8C95A6">1、视频面试请选择安静、光线充足的环境，提前测试网络与设备<br>2、建议面试前 5 分钟进入会议室等候</p>
                <p style="margin-top:20px">祝您面试顺利，期待与您交流！</p>
                <p style="margin-top:12px;color:#5B6475">%s 招聘团队</p>
                <hr style="border:none;border-top:1px solid #e8ecf1;margin:12px 0">
                <p style="color:#8C95A6;font-size:12px">此邮件由招聘系统自动发送</p>
                </div></body></html>
                """.formatted(
                escapeHtml(company), escapeHtml(candidateName), escapeHtml(company),
                escapeHtml(position), escapeHtml(position), escapeHtml(interviewDate),
                methodDisplay, escapeHtml(roundName), linkRow, confirmButton,
                escapeHtml(company));

        // ── DB 模板优先，兜底默认 ──
        String[] rendered = renderNotifyTemplate("interview", defaultSubject, defaultHtml, ctx);
        return sendMail(to, rendered[0], rendered[1], "invite");
    }

    public Map<String, Object> sendOfferEmail(String to, String candidateName,
                                               String offerNo, String offerContent,
                                               String validDeadline) {
        String company = companyName();
        String deadlineInfo = validDeadline != null && !validDeadline.isBlank() ? validDeadline : "";

        // 生成 Offer 确认链接（candidate-confirm token，对齐面试邀请）
        String confirmUrl = "";
        if (offerNo != null && !offerNo.isBlank()) {
            try {
                String token = io.jsonwebtoken.Jwts.builder()
                        .claim("purpose", "candidate-confirm")
                        .claim("kind", "offer")
                        .claim("ref", offerNo)
                        .issuedAt(new java.util.Date())
                        .expiration(new java.util.Date(System.currentTimeMillis() + 7 * 86400_000L))
                        .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .compact();
                confirmUrl = baseUrl.replaceFirst("/$", "") + "/confirm/" + token;
            } catch (Exception e) {
                log.warn("生成 Offer 确认 token 失败 offerNo={}: {}", offerNo, e.getMessage());
            }
        }

        // 构建上下文（补 offer 专属变量，供 DB 模板替换）
        Map<String, String> ctx = new java.util.LinkedHashMap<>();
        ctx.put("candidate", candidateName != null ? candidateName : "");
        ctx.put("company", company);
        ctx.put("position", "");
        ctx.put("offer_no", offerNo != null ? offerNo : "");
        ctx.put("offer_content", offerContent != null ? offerContent : "");
        ctx.put("deadline", deadlineInfo);
        ctx.put("confirm_url", confirmUrl);

        // 默认模板（兜底，对齐 Python fallback），补「确认接受 Offer」按钮
        String contentHtml = offerContent != null && !offerContent.isBlank()
                ? "<div style=\"background:#f0faf4;border-left:4px solid #22a06b;border-radius:4px;padding:14px 18px;margin:14px 0;line-height:1.9\">"
                + offerContent.replace("\n", "<br>") + "</div>" : "";
        String confirmButton = "";
        if (!confirmUrl.isBlank()) {
            confirmButton = """
                    <p style="text-align:center;margin:24px 0">
                      <a href="%s" style="display:inline-block;background:#22a06b;color:#fff;padding:12px 40px;border-radius:6px;text-decoration:none;font-size:15px;font-weight:600">确认接受 Offer</a>
                    </p>
                    <p style="color:#999;font-size:12px;text-align:center">如按钮无法点击，请复制链接到浏览器打开：<br>%s</p>
                    """.formatted(escapeHtml(confirmUrl), escapeHtml(confirmUrl));
        }
        String defaultSubject = "【录用通知】" + company + " - " + offerNo;
        String defaultHtml = """
                <html><body style="font-family:Microsoft YaHei,sans-serif;max-width:600px;margin:0 auto;color:#333">
                <div style="background:#22a06b;padding:20px 28px;border-radius:8px 8px 0 0">
                  <div style="font-size:18px;font-weight:700;color:#fff">%s</div>
                  <div style="font-size:13px;color:rgba(255,255,255,.7)">录用通知书</div>
                </div>
                <div style="padding:20px 28px;border:1px solid #e8ecf1;border-top:none;border-radius:0 0 8px 8px">
                <p>%s 您好：</p>
                <p>恭喜您通过全部面试，现正式向您发出 <b>%s</b> 岗位的录用邀请：</p>
                %s
                <p>请在 <b style="color:#e67e22">%s</b> 前确认是否接受：</p>
                %s
                <hr style="border:none;border-top:1px solid #e8ecf1;margin:12px 0">
                <p style="color:#8C95A6;font-size:12px">此邮件由招聘系统自动发送</p>
                </div></body></html>
                """.formatted(
                escapeHtml(company), escapeHtml(candidateName), escapeHtml(offerNo),
                contentHtml, escapeHtml(deadlineInfo), confirmButton);

        // DB 模板优先，兜底默认
        String[] rendered = renderNotifyTemplate("offer", defaultSubject, defaultHtml, ctx);
        return sendMail(to, rendered[0], rendered[1], "offer");
    }

    public Map<String, Object> sendInterviewRejectEmail(String to, String candidateName, String position) {
        String company = companyName();
        String html = """
                <html><body style="font-family:Microsoft YaHei,sans-serif;max-width:600px;margin:0 auto;color:#333;line-height:1.7">
                <div style="background:#4F6EF7;padding:20px 28px;border-radius:8px 8px 0 0">
                  <div style="font-size:18px;font-weight:700;color:#fff">%s</div>
                  <div style="font-size:13px;color:rgba(255,255,255,.7)">面试结果通知</div>
                </div>
                <div style="padding:20px 28px;border:1px solid #e8ecf1;border-top:none;border-radius:0 0 8px 8px">
                <p>%s 您好：</p>
                <p>感谢您参加 <b>%s</b> 的面试。很遗憾，本次面试暂未通过。</p>
                <p>我们会将您的简历保留在人才库中，后续如有更匹配的机会将再次联系您。</p>
                <hr style="border:none;border-top:1px solid #e8ecf1;margin:12px 0">
                <p style="color:#8C95A6;font-size:12px">此邮件由招聘系统自动发送</p>
                </div></body></html>
                """.formatted(escapeHtml(company), escapeHtml(candidateName), escapeHtml(position));
        return sendMail(to, "【面试结果】" + company + " - " + position, html, "invite");
    }

    public Map<String, Object> sendOfferReminderEmail(String to, String candidate,
                                                       String position, int daysLeft, String deadline) {
        String company = companyName();
        String html = """
                <html><body style="font-family:Microsoft YaHei,sans-serif;max-width:600px;margin:0 auto;color:#333">
                <div style="background:#d97706;padding:20px 28px;border-radius:8px 8px 0 0">
                  <div style="font-size:18px;font-weight:700;color:#fff">%s</div>
                  <div style="font-size:13px;color:rgba(255,255,255,.7)">Offer 确认倒计时提醒</div>
                </div>
                <div style="padding:20px 28px;border:1px solid #e8ecf1;border-top:none;border-radius:0 0 8px 8px">
                <p>%s 您好：</p>
                <p>您收到的 <b>%s</b> 录用 Offer 还未确认，距离截止还剩 <b style="color:#d97706">%d 天</b>（%s）。</p>
                <p>逾期未确认将视为放弃本次录用机会，请尽快登录系统确认。</p>
                <hr style="border:none;border-top:1px solid #e8ecf1;margin:12px 0">
                <p style="color:#8C95A6;font-size:12px">此邮件由招聘系统自动发送</p>
                </div></body></html>
                """.formatted(
                escapeHtml(company), escapeHtml(candidate), escapeHtml(position), daysLeft, deadline);
        return sendMail(to, "【Offer提醒】" + company + " - " + candidate + " 还剩 " + daysLeft + " 天", html, "offer");
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
