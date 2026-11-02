package com.fcrc.common.exception;

import java.util.Map;

/**
 * 可预期的业务异常。
 *
 * <p>与受检异常相比，业务异常通常在控制器边界统一翻译为 HTTP 状态码和 {@code ApiResponse}。
 * 这里保留错误码和可选的上下文信息，便于审计与前端提示。</p>
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    public static BusinessException illegalState(String message) {
        return new BusinessException(ErrorCode.ILLEGAL_STATE, message);
    }

    public static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
