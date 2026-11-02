package com.fcrc.agent.client.dto;

import java.time.Instant;

/**
 * 工作流运行视图。
 *
 * <p>只暴露 AI 回答用户问题所需的字段；审计轨迹、内部错误堆栈不在这里。
 * 尤其是 {@code canResume} 只表示"可以人工复核"，不代表 AI 有权复核。</p>
 */
public record WorkflowRunView(
        String runNo,
        Long customerId,
        String customerNo,
        String customerName,
        String status,
        String currentNode,
        String riskAssessmentNo,
        Integer riskScore,
        String riskLevel,
        String complianceScreeningNo,
        String complianceResult,
        String preliminaryDecision,
        String decision,
        String decisionReason,
        String reviewDecision,
        String reviewer,
        String reviewComment,
        String caseNo,
        String reportNo,
        String onboardingStatus,
        boolean canResume,
        Instant createdAt,
        Instant updatedAt) {
}
