package com.fcrc.common.exception;

/**
 * 平台错误码。
 *
 * <p>错误码采用“HTTP 语义 + 两位业务序号”的规则（例如 40401 表示客户不存在），
 * 既方便排障，也能被前端稳定识别。禁止在业务代码中直接写字符串错误码。</p>
 */
public enum ErrorCode {

    VALIDATION_ERROR("40001", "请求参数不合法"),
    UNAUTHORIZED("40101", "未认证或凭证已过期"),
    FORBIDDEN("40301", "无权访问该资源"),
    NOT_FOUND("40401", "资源不存在"),
    CONFLICT("40901", "资源状态冲突"),
    ILLEGAL_STATE("40902", "当前状态不允许该操作"),
    UPSTREAM_ERROR("50201", "上游服务调用失败"),
    INTERNAL_ERROR("50000", "系统内部错误");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
