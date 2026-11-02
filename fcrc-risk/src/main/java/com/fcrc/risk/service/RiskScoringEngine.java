package com.fcrc.risk.service;

import com.fcrc.common.domain.RiskResult;
import com.fcrc.risk.dto.RiskAssessmentRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 确定性风险评分引擎。
 *
 * <p>为什么不用大模型直接打分？金融风控要求结果可解释、可复现、可审计。
 * 大模型适合做“把规则结果翻译成人话”，但分数必须来自版本化的规则。
 * 每一次规则变更都应伴随回归测试与模型风险管理（MRM）审批。</p>
 *
 * <p>评分结构：基础分 + 各风险因子累加，封顶 100。阈值：
 * LOW &lt; 20，MEDIUM &lt; 40，HIGH &lt; 60，CRITICAL &gt;= 60。</p>
 */
@Component
public class RiskScoringEngine {

    /** FATF 黑名单/需采取行动的高风险司法辖区（教学示例，实际应接入合规名单服务）。 */
    private static final Set<String> TIER_1_COUNTRIES = Set.of(
            "iran", "north korea", "dprk", "syria", "cuba");

    /** 离岸金融中心/较高风险地区，需要增强尽调。 */
    private static final Set<String> TIER_2_COUNTRIES = Set.of(
            "cayman islands", "panama", "bahamas", "seychelles", "british virgin islands",
            "lebanon", "myanmar", "afghanistan", "yemen", "south sudan", "libya", "somalia");

    /** 现金密集型或结构复杂行业。 */
    private static final Set<String> CASH_INTENSIVE_INDUSTRIES = Set.of(
            "cryptocurrency", "virtual assets", "gambling", "casino", "precious metals",
            "money transfer", "pawn", "real estate", "加密资产", "博彩", "贵金属", "汇兑");

    public RiskResult evaluate(RiskAssessmentRequest request) {
        List<RiskResult.RiskFactor> factors = new ArrayList<>();
        factors.add(new RiskResult.RiskFactor("BASE", "基础风险", 5,
                "所有业务关系的基础风险权重"));

        String country = normalize(request.profile().country());
        if (TIER_1_COUNTRIES.contains(country)) {
            factors.add(new RiskResult.RiskFactor("HIGH_RISK_COUNTRY", "高风险司法辖区", 35,
                    "客户所在国家/地区属于 FATF 高风险名单：" + request.profile().country()));
        } else if (TIER_2_COUNTRIES.contains(country)) {
            factors.add(new RiskResult.RiskFactor("OFFSHORE_JURISDICTION", "离岸/较高风险地区", 20,
                    "客户所在地区需要增强尽职调查：" + request.profile().country()));
        }

        if (request.profile().pep()) {
            factors.add(new RiskResult.RiskFactor("PEP", "政治公众人物", 25,
                    "客户或其受益所有人被标记为 PEP，需高级管理层审批"));
        }

        String industry = normalize(request.profile().industry());
        if (CASH_INTENSIVE_INDUSTRIES.stream().anyMatch(industry::contains)) {
            factors.add(new RiskResult.RiskFactor("CASH_INTENSIVE", "现金密集型/高风险行业", 12,
                    "行业具有较高洗钱风险：" + request.profile().industry()));
        }

        if (request.highRiskProduct()) {
            factors.add(new RiskResult.RiskFactor("HIGH_RISK_PRODUCT", "高风险产品", 15,
                    "涉及虚拟资产、复杂衍生品等高风险产品"));
        }

        BigDecimal amount = request.transactionAmount() == null ? BigDecimal.ZERO : request.transactionAmount();
        if (amount.compareTo(new BigDecimal("5000000")) >= 0) {
            factors.add(new RiskResult.RiskFactor("VERY_LARGE_AMOUNT", "超大额交易", 20,
                    "预计交易金额超过 500 万，需资金来源与用途核查"));
        } else if (amount.compareTo(new BigDecimal("1000000")) >= 0) {
            factors.add(new RiskResult.RiskFactor("LARGE_AMOUNT", "大额交易", 12,
                    "预计交易金额超过 100 万，触发增强监测"));
        } else if (amount.compareTo(new BigDecimal("200000")) >= 0) {
            factors.add(new RiskResult.RiskFactor("ELEVATED_AMOUNT", "较高金额", 5,
                    "预计交易金额超过 20 万"));
        }

        if (request.crossBorder()) {
            factors.add(new RiskResult.RiskFactor("CROSS_BORDER", "跨境交易", 8,
                    "跨境资金流动需要额外的制裁与贸易背景筛查"));
        }

        if (request.recentTransactionCount() >= 50) {
            factors.add(new RiskResult.RiskFactor("HIGH_FREQUENCY", "高频交易", 12,
                    "近 30 天交易笔数达到 " + request.recentTransactionCount()));
        } else if (request.recentTransactionCount() >= 20) {
            factors.add(new RiskResult.RiskFactor("ELEVATED_FREQUENCY", "交易频率偏高", 6,
                    "近 30 天交易笔数达到 " + request.recentTransactionCount()));
        }

        if (request.newRelationship() && amount.compareTo(new BigDecimal("500000")) >= 0) {
            factors.add(new RiskResult.RiskFactor("NEW_RELATIONSHIP_LARGE_VALUE", "新关系大额交易", 6,
                    "新建立业务关系即发生较大金额交易"));
        }

        if ("CORPORATE".equalsIgnoreCase(request.profile().customerType())) {
            factors.add(new RiskResult.RiskFactor("CORPORATE", "机构客户", 4,
                    "机构客户需识别受益所有人并核查股权结构"));
            if (!country.isBlank() && !"china".equals(country)) {
                factors.add(new RiskResult.RiskFactor("FOREIGN_CORPORATE", "境外机构", 8,
                        "境外机构客户需核查注册地、实际经营地与受益所有人"));
            }
        }

        int rawScore = factors.stream().mapToInt(RiskResult.RiskFactor::points).sum();
        int score = Math.min(rawScore, 100);
        String level = levelOf(score);
        factors.sort(Comparator.comparingInt(RiskResult.RiskFactor::points).reversed());
        return new RiskResult(score, level, factors, recommendation(level));
    }

    public String levelOf(int score) {
        if (score < 20) {
            return "LOW";
        }
        if (score < 40) {
            return "MEDIUM";
        }
        if (score < 60) {
            return "HIGH";
        }
        return "CRITICAL";
    }

    private String recommendation(String level) {
        return switch (level) {
            case "LOW" -> "适用标准尽职调查，按常规频率持续监测。";
            case "MEDIUM" -> "执行增强尽职调查，补充资金来源/用途证明，6 个月内复评。";
            case "HIGH" -> "提交合规官复核，必要时要求高级管理层审批，缩短复评周期至 3 个月。";
            default -> "建议拒绝或限制业务关系；如确需建立，必须经合规负责人及高级管理层联合审批。";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
