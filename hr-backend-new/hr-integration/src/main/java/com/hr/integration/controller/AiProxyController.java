package com.hr.integration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hr.config.service.ConfigService;
import com.hr.demand.service.DemandService;
import com.hr.talent.service.TalentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 服务代理：/api/ai/* 转发到独立 Python FastAPI 服务（默认 :8100）。
 * 对齐 Flask 的 /api/ai/capabilities、/api/ai/run/{workflow}、/api/ai/stream/{workflow}。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiProxyController {

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(120))
            .build();

    private final ConfigService configService;
    private final DemandService demandService;
    private final TalentService talentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai-service.url:http://127.0.0.1:8100}")
    private String aiServiceUrl;

    @GetMapping("/capabilities")
    public ResponseEntity<?> capabilities() throws IOException {
        Request req = new Request.Builder()
                .url(aiServiceUrl + "/api/ai/capabilities")
                .get()
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            return passthrough(resp);
        }
    }

    @PostMapping("/run/{workflow}")
    public ResponseEntity<?> run(@PathVariable String workflow,
                                 @RequestBody(required = false) String body) throws IOException {
        body = enrichBody(workflow, body);
        Request.Builder rb = new Request.Builder()
                .url(aiServiceUrl + "/api/ai/run/" + workflow);
        applyDeepSeekKey(rb);
        rb.post(body == null ? okhttp3.RequestBody.create(new byte[0])
                : okhttp3.RequestBody.create(body,
                        okhttp3.MediaType.parse("application/json; charset=utf-8")));
        try (Response resp = httpClient.newCall(rb.build()).execute()) {
            return passthrough(resp);
        }
    }

    @PostMapping(value = "/stream/{workflow}", produces = "text/event-stream")
    public ResponseEntity<?> stream(@PathVariable String workflow,
                                    @RequestBody(required = false) String body) throws IOException {
        body = enrichBody(workflow, body);
        Request.Builder rb = new Request.Builder()
                .url(aiServiceUrl + "/api/ai/stream/" + workflow);
        applyDeepSeekKey(rb);
        rb.post(body == null ? okhttp3.RequestBody.create(new byte[0])
                : okhttp3.RequestBody.create(body,
                        okhttp3.MediaType.parse("application/json; charset=utf-8")));
        try (Response resp = httpClient.newCall(rb.build()).execute()) {
            ResponseBody rbBody = resp.body();
            if (rbBody == null) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/event-stream;charset=UTF-8")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .header("X-Accel-Buffering", "no")
                    .body(rbBody.string());
        }
    }

    /**
     * 解析 DeepSeek key：优先环境变量，其次数据库（网页配置，加密存储）。
     * 由 hr-ai-service 优先使用请求头里的 key，保持网页配置 key 的体验。
     */
    private String resolveDeepSeekKey() {
        String env = System.getenv("DEEPSEEK_API_KEY");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return configService.decryptStoredKey("deepseek");
    }

    private void applyDeepSeekKey(Request.Builder rb) {
        String dsKey = resolveDeepSeekKey();
        if (dsKey != null && !dsKey.isBlank()) {
            rb.header("X-DeepSeek-Key", dsKey);
        }
    }

    /**
     * 转发前组装 JD 全文与候选人画像。
     * 前端只传 candidate_id/demand_id，Python 端读 jd/candidate/resume 对象，
     * 这里查库补齐字段。任何异常都原样返回，不阻塞 AI 调用。
     */
    private String enrichBody(String workflow, String body) {
        boolean needsEnrich = "match".equals(workflow) || "match-score".equals(workflow)
                || "interview-questions".equals(workflow) || "interview-qa".equals(workflow);
        if (!needsEnrich || body == null || body.isBlank()) {
            return body;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (!node.isObject()) {
                return body;
            }
            String candidateId = node.has("candidate_id") ? node.get("candidate_id").asText("") : "";
            String demandId = node.has("demand_id") ? node.get("demand_id").asText("") : "";
            if (candidateId.isBlank() || demandId.isBlank()) {
                return body;
            }

            ObjectNode out = (ObjectNode) node;

            // JD 全文
            Map<String, Object> demand = demandService.getDemandDetail(demandService.resolveDemandId(demandId));
            Object jd = demand.get("description");
            out.put("jd", jd != null ? String.valueOf(jd) : "");

            // 候选人画像
            Map<String, Object> cand = talentService.getCandidateDetail(talentService.resolveCandidateId(candidateId));

            ObjectNode candidateNode = objectMapper.createObjectNode();
            candidateNode.put("name", cand.get("name") != null ? String.valueOf(cand.get("name")) : "");
            candidateNode.put("education", cand.get("edu") != null ? String.valueOf(cand.get("edu")) : "");
            int workYears = parseWorkYears(cand.get("years"));
            candidateNode.put("workYears", workYears);
            candidateNode.put("work_years", workYears);
            candidateNode.set("skills", toJsonArray(cand.get("skills")));

            String summary = "";
            List<?> resumeSkills = List.of();
            Object resumeObj = cand.get("resume");
            if (resumeObj instanceof Map<?, ?> rm) {
                Object s = rm.get("summary");
                if (s != null) summary = String.valueOf(s);
                Object sk = rm.get("skills");
                if (sk instanceof List<?> l) resumeSkills = l;
            }
            candidateNode.put("summary", summary);
            out.set("candidate", candidateNode);

            ObjectNode resumeNode = objectMapper.createObjectNode();
            resumeNode.put("summary", summary);
            resumeNode.set("skills", toJsonArray(resumeSkills));
            out.set("resume", resumeNode);

            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            log.warn("[AI] enrich body failed for workflow {}: {}", workflow, e.getMessage());
            return body;
        }
    }

    /** "应届"/"3年" → 数字工作年限。 */
    private int parseWorkYears(Object yearsObj) {
        if (yearsObj == null) return 0;
        Matcher m = Pattern.compile("\\d+").matcher(String.valueOf(yearsObj));
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    /** 技能列表 → JSON 数组，过滤空串和占位符 "—"。 */
    private JsonNode toJsonArray(Object list) {
        ArrayNode arr = objectMapper.createArrayNode();
        if (list instanceof List<?> l) {
            for (Object o : l) {
                String v = String.valueOf(o);
                if (v != null && !v.isBlank() && !"—".equals(v)) {
                    arr.add(v);
                }
            }
        }
        return arr;
    }

    private ResponseEntity<?> passthrough(Response resp) throws IOException {
        ResponseBody respBody = resp.body();
        String content = respBody == null ? "" : respBody.string();
        MediaType mt = respBody != null && respBody.contentType() != null
                ? MediaType.parseMediaType(respBody.contentType().toString())
                : MediaType.parseMediaType("application/json; charset=utf-8");
        return ResponseEntity.status(resp.code())
                .contentType(mt)
                .body(content);
    }
}
