package com.fcrc.common.domain;

import java.util.List;

/**
 * 风险评分结果。
 *
 * @param score          0-100 的风险分，越高越危险
 * @param level          LOW / MEDIUM / HIGH / CRITICAL
 * @param factors        命中的风险因子说明
 * @param recommendation 处置建议
 */
public record RiskResult(
        int score,
        String level,
        List<RiskFactor> factors,
        String recommendation) {

    public RiskResult {
        factors = factors == null ? List.of() : List.copyOf(factors);
    }

    /**
     * 单个风险因子。
     *
     * @param code        机器可读编码，例如 PEP / HIGH_RISK_COUNTRY
     * @param label       中文标签
     * @param points      命中后增加的分数
     * @param description 解释
     */
    public record RiskFactor(String code, String label, int points, String description) {}
}
