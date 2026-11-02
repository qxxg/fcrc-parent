package com.fcrc.workflow.dto;

import java.util.List;

/** 运行详情：业务结果 + 完整事件时间线。 */
public record WorkflowRunDetail(WorkflowRunResponse run, List<WorkflowEventResponse> events) {
}
