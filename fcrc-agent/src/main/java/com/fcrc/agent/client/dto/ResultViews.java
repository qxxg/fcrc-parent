package com.fcrc.agent.client.dto;

import com.fcrc.common.domain.ComplianceResult;
import com.fcrc.common.domain.RiskResult;
import java.time.Instant;
import java.util.List;

/** 风险/合规结果的 AI 视图。 */
public final class ResultViews {

    private ResultViews() {
    }

    /** 风险评估结果视图。 */
    public record RiskView(
            String assessmentNo,
            String customerNo,
            int score,
            String level,
            List<RiskResult.RiskFactor> factors,
            String recommendation,
            Instant createdAt) {
    }

    /** 合规筛查结果视图。 */
    public record ComplianceView(
            String screeningNo,
            String customerNo,
            String result,
            int riskPoints,
            List<ComplianceResult.Finding> findings,
            String recommendation,
            Instant createdAt) {
    }
}
