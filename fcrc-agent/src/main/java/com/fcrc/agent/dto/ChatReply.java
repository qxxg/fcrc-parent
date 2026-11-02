package com.fcrc.agent.dto;

/** 同步对话响应。 */
public record ChatReply(
        String userId,
        String sessionId,
        String reply,
        String modelMode) {
}
