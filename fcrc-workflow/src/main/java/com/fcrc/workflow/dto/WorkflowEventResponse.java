package com.fcrc.workflow.dto;

import java.time.Instant;

/** 工作流事件视图。 */
public record WorkflowEventResponse(
        Long seq,
        String type,
        String node,
        String message,
        String payload,
        Instant createdAt) {
}
