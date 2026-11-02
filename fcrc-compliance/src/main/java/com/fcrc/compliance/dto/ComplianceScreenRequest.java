package com.fcrc.compliance.dto;

import com.fcrc.common.domain.CustomerProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * 合规筛查请求。
 *
 * <p>身份资料是否核验、受益所有人是否识别、资金来源/用途是否说明，
 * 在真实业务中来自不同系统，这里显式建模，便于展示“缺失项如何影响结论”。</p>
 */
public record ComplianceScreenRequest(
        @NotBlank(message = "客户编号不能为空") String customerNo,
        @NotNull(message = "客户画像不能为空") @Valid CustomerProfile profile,
        BigDecimal transactionAmount,
        String transactionType,
        boolean crossBorder,
        String sourceOfFunds,
        String businessPurpose,
        boolean identityDocumentVerified,
        boolean beneficialOwnerVerified,
        /** 负面媒体筛查结果，模拟外部供应商返回。 */
        boolean adverseMediaFlag,
        /** 上游风险等级，用于风险为本的合规判断。 */
        String riskLevel) {
}
