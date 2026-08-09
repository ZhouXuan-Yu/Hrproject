package com.hr.integration.controller;

import lombok.RequiredArgsConstructor;
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

/**
 * AI 服务代理：/api/ai/* 转发到独立 Python FastAPI 服务（默认 :8100）。
 * 对齐 Flask 的 /api/ai/capabilities、/api/ai/run/{workflow}、/api/ai/stream/{workflow}。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiProxyController {

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(120))
            .build();

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
        Request.Builder rb = new Request.Builder()
                .url(aiServiceUrl + "/api/ai/run/" + workflow);
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
        Request.Builder rb = new Request.Builder()
                .url(aiServiceUrl + "/api/ai/stream/" + workflow);
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
