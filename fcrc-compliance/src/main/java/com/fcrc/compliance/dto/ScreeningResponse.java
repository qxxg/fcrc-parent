package com.fcrc.compliance.dto;

import com.fcrc.common.domain.ComplianceResult;
import java.time.Instant;
import java.util.List;

/** 合规筛查结果视图。 */
public record ScreeningResponse(
        String screeningNo,
        String customerNo,
        String result,
        int riskPoints,
        List<ComplianceResult.Finding> findings,
        String recommendation,
        Instant createdAt) {
}
