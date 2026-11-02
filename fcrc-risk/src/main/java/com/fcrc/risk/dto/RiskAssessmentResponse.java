package com.fcrc.risk.dto;

import com.fcrc.common.domain.RiskResult;
import java.time.Instant;

/** 风险评估结果视图，带业务编号与时间，供审计和报告引用。 */
public record RiskAssessmentResponse(
        String assessmentNo,
        String customerNo,
        int score,
        String level,
        java.util.List<RiskResult.RiskFactor> factors,
        String recommendation,
        Instant createdAt) {
}
