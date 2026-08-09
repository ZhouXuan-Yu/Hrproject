package com.hr.common.exception;

import lombok.Getter;

/**
 * 业务异常，对应 Flask 的 AppError。带错误码和 HTTP 状态码。
 */
@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final int status;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.status = 400;
    }

    public BusinessException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    // ── 常用错误码 ──────────────────────────────────────────
    public static BusinessException unauthorized() {
        return new BusinessException("UNAUTHORIZED", "请先登录", 401);
    }

    public static BusinessException forbidden() {
        return new BusinessException("FORBIDDEN", "无权限访问", 403);
    }

    public static BusinessException invalidInput(String message) {
        return new BusinessException("INVALID_INPUT", message, 400);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException("NOT_FOUND", message, 404);
    }

    public static BusinessException tokenExpired() {
        return new BusinessException("TOKEN_EXPIRED", "登录已过期", 401);
    }

    public static BusinessException invalidToken() {
        return new BusinessException("INVALID_TOKEN", "无效令牌", 401);
    }
}
