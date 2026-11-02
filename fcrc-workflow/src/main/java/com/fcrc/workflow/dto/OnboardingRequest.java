package com.fcrc.workflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 发起开户编排的请求。
 *
 * <p>CustomerProfile 由编排服务从客户域读取，调用方不得自行上报身份资料，
 * 否则会出现“谁都能声明客户是谁”的越权风险。这里只接收本次业务关系/交易的
 * 上下文，以及 KYC 采集中已经确认的事实。</p>
 */
public record OnboardingRequest(
        @NotNull(message = "客户 ID 不能为空")
        @Min(value = 1, message = "客户 ID 必须大于 0")
        Long customerId,

        @PositiveOrZero(message = "交易金额不能为负数")
        BigDecimal transactionAmount,

        @Pattern(regexp = "DEPOSIT|TRANSFER|INVESTMENT|TRADE_FINANCE", message = "交易类型非法")
        String transactionType,

        boolean crossBorder,

        @Min(value = 0, message = "近 30 天交易笔数不能为负数")
        Integer recentTransactionCount,

        boolean newRelationship,

        boolean highRiskProduct,

        @Size(max = 200, message = "资金来源说明不能超过 200 字")
        String sourceOfFunds,

        @Size(max = 500, message = "业务目的说明不能超过 500 字")
        String businessPurpose,

        /** 身份证明文件是否完成核验。 */
        boolean identityDocumentVerified,

        /** 机构客户是否完成受益所有人识别。 */
        boolean beneficialOwnerVerified,

        /** 外部负面媒体供应商是否返回待核实线索。 */
        boolean adverseMediaFlag) {
}
