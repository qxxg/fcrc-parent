package com.fcrc.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 编排服务回写客户开户流程状态。
 *
 * <p>该接口位于 /internal 下，只供内网服务调用。生产环境应在网关屏蔽该前缀，
 * 并在服务入口启用认证。</p>
 */
public record UpdateOnboardingRequest(
        @NotBlank(message = "流程状态不能为空")
        @Pattern(regexp = "NOT_STARTED|RUNNING|MANUAL_REVIEW|COMPLETED|REJECTED", message = "流程状态非法")
        String onboardingStatus,

        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "风险等级非法")
        String riskLevel,

        @Size(max = 1000, message = "备注长度不能超过 1000")
        String notes) {
}
