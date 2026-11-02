package com.fcrc.agent.client.dto;

import java.math.BigDecimal;

/**
 * 客户视图（AI 可读版本）。
 *
 * <p>只保留业务口径需要的字段，刻意不包含明文证件号/手机号。
 * 即使下游返回脱敏值，AI 层也只声明它需要的字段，减少无意泄露面。</p>
 */
public record CustomerView(
        Long id,
        String customerNo,
        String name,
        String customerType,
        String country,
        String industry,
        BigDecimal annualIncome,
        String occupation,
        boolean pep,
        String channel,
        String kycStatus,
        String onboardingStatus,
        String riskLevel) {
}
