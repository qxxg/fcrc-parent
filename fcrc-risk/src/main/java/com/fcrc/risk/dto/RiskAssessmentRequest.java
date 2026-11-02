package com.fcrc.risk.dto;

import com.fcrc.common.domain.CustomerProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * 风险评估请求。
 *
 * <p>除了客户画像，还携带本次业务关系/交易的特征。风险不是静态标签：
 * 同一个客户办理不同产品、金额和跨境属性，风险结果可能完全不同。</p>
 *
 * @param customerNo               客户业务编号
 * @param profile                  客户画像（由客户中心内部接口提供）
 * @param transactionAmount        预计交易金额
 * @param transactionType          DEPOSIT / TRANSFER / INVESTMENT / TRADE_FINANCE
 * @param crossBorder              是否跨境
 * @param recentTransactionCount   近 30 天交易笔数
 * @param newRelationship          是否新建立业务关系
 * @param highRiskProduct          是否涉及高风险产品（如虚拟资产、复杂衍生品）
 */
public record RiskAssessmentRequest(
        @NotBlank(message = "客户编号不能为空") String customerNo,
        @NotNull(message = "客户画像不能为空") @Valid CustomerProfile profile,
        @PositiveOrZero(message = "交易金额不能为负数") BigDecimal transactionAmount,
        String transactionType,
        boolean crossBorder,
        @PositiveOrZero(message = "交易笔数不能为负数") int recentTransactionCount,
        boolean newRelationship,
        boolean highRiskProduct) {
}
