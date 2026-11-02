package com.fcrc.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 对话请求。
 *
 * <p>{@code userId} 与 {@code sessionId} 共同决定会话隔离：
 * 同一用户的不同 sessionId 是不同对话；不同用户永远不会共享上下文。
 * 生产环境 userId 应从登录态解析，而不是由前端直接传入。</p>
 */
public record ChatRequest(
        @NotBlank(message = "message 不能为空")
        @Size(max = 4000, message = "单条消息不能超过 4000 字")
        String message,

        String userId,

        String sessionId) {
}
