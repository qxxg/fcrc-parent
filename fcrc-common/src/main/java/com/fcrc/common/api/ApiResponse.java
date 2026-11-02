package com.fcrc.common.api;

import com.fcrc.common.exception.ErrorCode;
import java.time.Instant;

/**
 * 全平台统一响应包装。
 *
 * <p>企业系统里，前端、网关、监控和审计系统需要稳定的响应契约。所有控制器必须返回该类型，
 * 不允许直接返回裸对象，否则错误处理、告警和链路追踪会失去统一入口。</p>
 *
 * @param success 业务是否成功
 * @param code    业务码，成功固定为 {@code 0}
 * @param message 面向用户的提示
 * @param data    业务负载，失败时可为 {@code null}
 * @param timestamp 服务端响应时间（UTC）
 * @param <T>     负载类型
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "0", "OK", data, Instant.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, "0", "OK", null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, "0", message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.code(), message, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, Instant.now());
    }
}
