package com.hr.talent.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历解析器 — 纯 Java 正则实现，对齐 Python backend ai_engine.py。
 * 无需外部 API，确定性解析，不依赖 AI 服务。
 */
public final class ResumeParser {

    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final List<String> C9_SCHOOLS = List.of(
            "清华", "北大", "复旦", "上海交大", "浙大", "南大", "中科大", "哈工大", "西交大");

    private static final List<String> KNOWN_COMPANIES = List.of(
            "阿里巴巴", "腾讯", "字节跳动", "美团", "百度", "京东", "网易",
            "华为", "小米", "滴滴", "快手", "拼多多", "B站", "小红书",
            "微软", "谷歌", "亚马逊", "苹果", "Meta");

    private static final Map<String, String[]> SKILL_DICT = Map.ofEntries(
            Map.entry("Java", new String[]{"java", "Java"}),
            Map.entry("Spring Boot", new String[]{"spring boot", "springboot", "spring"}),
            Map.entry("Spring Cloud", new String[]{"spring cloud", "springcloud"}),
            Map.entry("MySQL", new String[]{"mysql", "mariadb"}),
            Map.entry("PostgreSQL", new String[]{"postgresql", "postgres"}),
            Map.entry("Redis", new String[]{"redis"}),
            Map.entry("Kafka", new String[]{"kafka"}),
            Map.entry("RabbitMQ", new String[]{"rabbitmq", "rabbit mq"}),
            Map.entry("Elasticsearch", new String[]{"elasticsearch", "es"}),
            Map.entry("Docker", new String[]{"docker"}),
            Map.entry("Kubernetes", new String[]{"kubernetes", "k8s"}),
            Map.entry("微服务", new String[]{"微服务", "microservice"}),
            Map.entry("分布式系统", new String[]{"分布式"}),
            Map.entry("Go", new String[]{"golang", "go语言", "go开发"}),
            Map.entry("Python", new String[]{"python"}),
            Map.entry("JavaScript", new String[]{"javascript", "js"}),
            Map.entry("TypeScript", new String[]{"typescript", "ts"}),
            Map.entry("React", new String[]{"react"}),
            Map.entry("Vue", new String[]{"vue"}),
            Map.entry("Node.js", new String[]{"node.js", "nodejs"}),
            Map.entry("Linux", new String[]{"linux"}),
            Map.entry("Git", new String[]{"git "}),
            Map.entry("CI/CD", new String[]{"ci/cd", "cicd", "持续集成", "持续交付"}),
            Map.entry("SQL", new String[]{"sql"}),
            Map.entry("MongoDB", new String[]{"mongodb", "mongo"}),
            Map.entry("Nginx", new String[]{"nginx"}),
            Map.entry("Spark", new String[]{"spark"}),
            Map.entry("Hadoop", new String[]{"hadoop"}),
            Map.entry("Flink", new String[]{"flink"}),
            Map.entry("TensorFlow", new String[]{"tensorflow", "tf"}),
            Map.entry("PyTorch", new String[]{"pytorch"})
    );

    private ResumeParser() {}

    /**
     * 解析简历文本，返回结构化数据。
     * 对齐 Python ai_engine.parse_resume()。
     */
    public static Map<String, Object> parse(String text) {
        if (text == null || text.isBlank()) {
            return defaultResult();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", extractName(text));
        result.put("phone", extractPhone(text));
        result.put("email", extractEmail(text));
        result.put("edu_level", extractEduLevel(text));
        result.put("school_level", extractSchool(text));
        result.put("work_years", extractWorkYears(text));
        result.put("recent_company", extractCompany(text));
        result.put("skills", extractSkills(text));
        result.put("summary", generateSummary(text));
        result.put("parse_engine", "java-builtin");
        result.put("parsed_at", LocalDateTime.now().toString());
        return result;
    }

    static Map<String, Object> defaultResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "未知");
        result.put("phone", "");
        result.put("email", "");
        result.put("edu_level", "本科");
        result.put("school_level", "211");
        result.put("work_years", 3);
        result.put("recent_company", "");
        result.put("skills", List.of("Java", "MySQL"));
        result.put("summary", "");
        result.put("parse_engine", "java-builtin");
        result.put("parsed_at", LocalDateTime.now().toString());
        return result;
    }

    // ── 姓名 ──
    private static String extractName(String text) {
        String[] patterns = {
                "姓名[：:]\\s*([^\\s\\n]{2,4})",
                "名字[：:]\\s*([^\\s\\n]{2,4})",
                "^([^\\s\\n]{2,4})\\s*\\n"
        };
        for (String pat : patterns) {
            Matcher m = Pattern.compile(pat, Pattern.MULTILINE).matcher(text);
            if (m.find()) {
                String name = m.group(1).trim();
                if (name.length() >= 2 && name.length() <= 4
                        && !Pattern.compile("[a-zA-Z0-9@]").matcher(name).find()) {
                    return name;
                }
            }
        }
        return "未知";
    }

    // ── 手机号 ──
    private static String extractPhone(String text) {
        Matcher m = PHONE_PATTERN.matcher(text);
        return m.find() ? m.group() : "";
    }

    // ── 邮箱 ──
    private static String extractEmail(String text) {
        Matcher m = EMAIL_PATTERN.matcher(text);
        return m.find() ? m.group() : "";
    }

    // ── 学历 ──
    private static String extractEduLevel(String text) {
        if (Pattern.compile("博士|博士研究生|Ph\\.?D").matcher(text).find()) return "博士";
        if (Pattern.compile("硕士|硕士研究生|MBA|EMBA").matcher(text).find()) return "硕士";
        if (Pattern.compile("本科|学士|大学|B\\.?S\\.?|B\\.?A\\.?").matcher(text).find()) return "本科";
        if (Pattern.compile("大专|专科|高职").matcher(text).find()) return "大专";
        return "本科";
    }

    // ── 学校层次 ──
    private static String extractSchool(String text) {
        for (String s : C9_SCHOOLS) {
            if (text.contains(s)) return "C9";
        }
        if (text.contains("985")) return "985";
        if (text.contains("211")) return "211";
        if (Pattern.compile("大学|学院|University|College").matcher(text).find()) return "普通";
        return "普通";
    }

    // ── 工作年限 ──
    private static int extractWorkYears(String text) {
        String[] patterns = {
                "(\\d+)\\s*年.*?工作.*?经验",
                "工作年限[：:]\\s*(\\d+)",
                "工作经验[：:]\\s*(\\d+)\\s*年",
                "(\\d+)\\s*年.*?开发.*?经验"
        };
        for (String pat : patterns) {
            Matcher m = Pattern.compile(pat).matcher(text);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        // 按工作经历年份跨度和
        Matcher m = Pattern.compile("(20\\d{2})\\s*[\\.\\-—/]\\s*(20\\d{2}|至今|现在)").matcher(text);
        int total = 0;
        while (m.find()) {
            int start = Integer.parseInt(m.group(1));
            String endStr = m.group(2);
            int end = endStr.matches("\\d+") ? Integer.parseInt(endStr) : LocalDateTime.now().getYear();
            total += Math.max(0, end - start);
        }
        return total > 0 ? Math.min(total, 20) : 3;
    }

    // ── 最近公司 ──
    private static String extractCompany(String text) {
        int bestPos = -1;
        String bestCompany = "";
        for (String company : KNOWN_COMPANIES) {
            int pos = text.indexOf(company);
            if (pos >= 0 && pos > bestPos) {
                bestPos = pos;
                bestCompany = company;
            }
        }
        if (!bestCompany.isEmpty()) return bestCompany;
        Matcher m = Pattern.compile("([^\\s\\n]{2,10}(?:公司|有限公司|集团|科技|网络))").matcher(text);
        return m.find() ? m.group(1) : "";
    }

    // ── 技能 ──
    private static List<String> extractSkills(String text) {
        String lower = text.toLowerCase();
        List<String> found = new ArrayList<>();
        for (var entry : SKILL_DICT.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    found.add(entry.getKey());
                    break;
                }
            }
        }
        return found.size() > 8 ? found.subList(0, 8) : found;
    }

    // ── 摘要 ──
    private static String generateSummary(String text) {
        String edu = extractEduLevel(text);
        int years = extractWorkYears(text);
        List<String> skills = extractSkills(text);
        String company = extractCompany(text);
        if (company.isEmpty()) company = "某公司";
        String skillStr = String.join("、", skills.size() > 4 ? skills.subList(0, 4) : skills);
        return edu + "学历，" + years + "年工作经验，曾就职于" + company + "，擅长" + skillStr;
    }
}
