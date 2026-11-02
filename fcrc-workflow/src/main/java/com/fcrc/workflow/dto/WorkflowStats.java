package com.fcrc.workflow.dto;

/** 工作流看板统计。 */
public record WorkflowStats(
        long total,
        long running,
        long waitingReview,
        long completed,
        long rejected,
        long failed) {
}
