package com.fcrc.agent.client.dto;

import java.time.Instant;
import java.util.List;

/** 监管报告视图（AI 只读）。 */
public record ReportView(
        String reportNo,
        String customerNo,
        String workflowNo,
        String reportType,
        String period,
        String decision,
        Integer riskScore,
        String riskLevel,
        String complianceResult,
        List<String> riskFactors,
        List<String> complianceFindings,
        String summary,
        String status,
        Instant createdAt,
        Instant finalizedAt,
        Instant submittedAt) {
}
