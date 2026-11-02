package com.fcrc.agent.client.dto;

import java.time.Instant;
import java.util.List;

/** 工作流详情：运行摘要 + 事件时间线（AI 可读）。 */
public record WorkflowDetailView(WorkflowRunView run, List<EventView> events) {

    /** 工作流事件精简视图。 */
    public record EventView(Long seq, String type, String node, String message, Instant createdAt) {
    }
}
