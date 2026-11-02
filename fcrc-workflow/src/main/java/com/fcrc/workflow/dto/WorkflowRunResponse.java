package com.fcrc.workflow.dto;

import java.time.Instant;
import java.util.List;

/** 工作流运行视图。 */
public record WorkflowRunResponse(
        String runNo,
        Long customerId,
        String customerNo,
        String customerName,
        String status,
        String currentNode,
        String riskAssessmentNo,
        Integer riskScore,
        String riskLevel,
        List<String> riskFactors,
        String complianceScreeningNo,
        String complianceResult,
        List<String> complianceFindings,
        String preliminaryDecision,
        String decision,
        String decisionReason,
        String reviewDecision,
        String reviewer,
        String reviewComment,
        String caseNo,
        String reportNo,
        String onboardingStatus,
        List<String> auditTrail,
        boolean canResume,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {
}
