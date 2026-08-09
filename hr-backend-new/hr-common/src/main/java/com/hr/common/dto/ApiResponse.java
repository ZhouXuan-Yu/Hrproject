package com.hr.common.dto;

import lombok.Data;

/**
 * 统一响应体，对齐现有 Flask 后端 success/error 格式。
 * 成功: { "data": ..., "message": "ok" }
 * 失败: { "error": { "code": "...", "message": "..." } }
 */
@Data
public class ApiResponse<T> {
    private T data;
    private String message;
    private Integer total;
    private Integer page;
    private Integer pageSize;
    private ErrorDetail error;

    @Data
    public static class ErrorDetail {
        private String code;
        private String message;
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setData(data);
        r.setMessage("ok");
        return r;
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setData(data);
        r.setMessage(message);
        return r;
    }

    public static <T> ApiResponse<T> successList(T data, int total) {
        // 对齐 Flask success_list：{ data:[...], total:N, page:N, pageSize:N }
        return successList(data, total, 1, 20);
    }

    public static <T> ApiResponse<T> successList(T data, int total, int page, int pageSize) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setData(data);
        r.setMessage("ok");
        r.setTotal(total);
        r.setPage(page);
        r.setPageSize(pageSize);
        return r;
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        ErrorDetail e = new ErrorDetail();
        e.setCode(code);
        e.setMessage(message);
        r.setError(e);
        return r;
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setData(data);
        ErrorDetail e = new ErrorDetail();
        e.setCode(code);
        e.setMessage(message);
        r.setError(e);
        return r;
    }
}
