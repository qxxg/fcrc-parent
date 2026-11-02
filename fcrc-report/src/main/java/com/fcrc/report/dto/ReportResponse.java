package com.fcrc.report.dto;

import com.fcrc.report.domain.RegulatoryReportEntity;
import java.time.Instant;
import java.util.List;

/** 监管报告视图。 */
public record ReportResponse(
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

    public static ReportResponse from(RegulatoryReportEntity entity, ReportContent content) {
        return new ReportResponse(entity.getReportNo(), entity.getCustomerNo(), entity.getWorkflowNo(),
                entity.getReportType(), entity.getPeriod(), entity.getDecision(), entity.getRiskScore(),
                entity.getRiskLevel(), entity.getComplianceResult(), content.riskFactors(),
                content.complianceFindings(), entity.getSummary(), entity.getStatus(),
                entity.getCreatedAt(), entity.getFinalizedAt(), entity.getSubmittedAt());
    }

    /** 序列化在 content_json 中的报告正文。 */
    public record ReportContent(List<String> riskFactors, List<String> complianceFindings,
                                String narrative) {
    }
}
