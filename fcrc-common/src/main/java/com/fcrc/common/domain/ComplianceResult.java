package com.fcrc.common.domain;

import java.util.List;

/**
 * 合规筛查结果。
 *
 * @param result       PASS / REVIEW / FAIL
 * @param riskPoints   0-100 的合规风险分
 * @param findings     命中项
 * @param recommendation 处置建议
 */
public record ComplianceResult(
        String result,
        int riskPoints,
        List<Finding> findings,
        String recommendation) {

    public ComplianceResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /**
     * 合规命中项。
     *
     * @param type        KYC / AML / SANCTIONS / ADVERSE_MEDIA
     * @param severity    INFO / WARN / HIGH
     * @param description 说明
     */
    public record Finding(String type, String severity, String description) {}
}
