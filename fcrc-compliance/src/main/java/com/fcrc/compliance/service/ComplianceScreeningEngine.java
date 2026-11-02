package com.fcrc.compliance.service;

import com.fcrc.common.domain.ComplianceResult;
import com.fcrc.compliance.dto.ComplianceScreenRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 确定性合规筛查引擎。
 *
 * <p>结论采用 PASS / REVIEW / FAIL 三态：
 * <ul>
 *   <li>PASS：未发现需要人工介入的问题；</li>
 *   <li>REVIEW：存在中高风险信号，交由合规官结合材料判断；</li>
 *   <li>FAIL：名单命中或综合分达到拒绝阈值。</li>
 * </ul>
 * “命中名单”不直接等于犯罪事实，但必须进入人工复核并记录处置理由。</p>
 */
@Component
public class ComplianceScreeningEngine {

    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
            "iran", "north korea", "dprk", "syria", "cuba", "cayman islands", "panama",
            "bahamas", "lebanon", "myanmar", "afghanistan", "yemen", "south sudan", "libya", "somalia");

    private final SanctionsList sanctionsList;

    public ComplianceScreeningEngine(SanctionsList sanctionsList) {
        this.sanctionsList = sanctionsList;
    }

    public ComplianceResult screen(ComplianceScreenRequest request) {
        List<ComplianceResult.Finding> findings = new ArrayList<>();
        int points = 0;

        var match = sanctionsList.screen(request.profile().name());
        if (match.isPresent()) {
            SanctionsList.Match hit = match.get();
            points += 100;
            findings.add(new ComplianceResult.Finding("SANCTIONS", "HIGH",
                    "名单命中：" + hit.entry().program() + " / 别名 " + hit.matchedAlias()
                            + "，相似度 " + String.format(Locale.ROOT, "%.2f", hit.score())));
        }

        String country = normalize(request.profile().country());
        if (HIGH_RISK_COUNTRIES.contains(country)) {
            points += 30;
            findings.add(new ComplianceResult.Finding("AML", "HIGH",
                    "客户所在国家/地区属于高风险辖区：" + request.profile().country()));
        }

        if (request.profile().pep()) {
            points += 25;
            findings.add(new ComplianceResult.Finding("AML", "HIGH",
                    "客户为政治公众人物，必须执行强化尽职调查并识别资金来源。"));
        }

        if (request.adverseMediaFlag()) {
            points += 25;
            findings.add(new ComplianceResult.Finding("ADVERSE_MEDIA", "HIGH",
                    "负面媒体筛查发现潜在不利信息，需要人工核实真实性、时效性与相关性。"));
        }

        if (!request.identityDocumentVerified()) {
            points += 20;
            findings.add(new ComplianceResult.Finding("KYC", "HIGH",
                    "身份证明文件尚未完成核验，不得进入业务关系。"));
        }

        if (isBlank(request.sourceOfFunds())) {
            points += 15;
            findings.add(new ComplianceResult.Finding("KYC", "WARN",
                    "缺少资金来源说明，无法完成财富来源合理性判断。"));
        }

        if (isBlank(request.businessPurpose())) {
            points += 10;
            findings.add(new ComplianceResult.Finding("KYC", "WARN",
                    "缺少业务目的/交易背景说明。"));
        }

        if ("CORPORATE".equalsIgnoreCase(request.profile().customerType())
                && !request.beneficialOwnerVerified()) {
            points += 20;
            findings.add(new ComplianceResult.Finding("KYC", "HIGH",
                    "机构客户尚未完成受益所有人识别与核验。"));
        }

        if (request.crossBorder()) {
            points += 5;
            findings.add(new ComplianceResult.Finding("AML", "INFO",
                    "跨境交易需额外核查制裁、出口管制与贸易背景。"));
        }

        if ("HIGH".equalsIgnoreCase(request.riskLevel())) {
            points += 10;
            findings.add(new ComplianceResult.Finding("AML", "WARN",
                    "上游风险评级为 HIGH，合规筛查采用风险为本的强化标准。"));
        } else if ("CRITICAL".equalsIgnoreCase(request.riskLevel())) {
            points += 20;
            findings.add(new ComplianceResult.Finding("AML", "HIGH",
                    "上游风险评级为 CRITICAL，建议限制业务关系。"));
        }

        BigDecimal amount = request.transactionAmount() == null ? BigDecimal.ZERO : request.transactionAmount();
        if (amount.compareTo(new BigDecimal("5000000")) >= 0) {
            points += 10;
            findings.add(new ComplianceResult.Finding("AML", "WARN",
                    "单笔/预计交易金额超过 500 万，需核查交易背景与资金来源。"));
        }

        int score = Math.min(points, 100);
        String result = decide(match.isPresent(), score);
        return new ComplianceResult(result, score, findings, recommendation(result));
    }

    private String decide(boolean sanctionsHit, int score) {
        if (sanctionsHit || score >= 60) {
            return "FAIL";
        }
        if (score >= 25) {
            return "REVIEW";
        }
        return "PASS";
    }

    private String recommendation(String result) {
        return switch (result) {
            case "PASS" -> "可以继续办理；按客户风险等级执行持续监测。";
            case "REVIEW" -> "转合规官人工复核，补齐 KYC 材料后再作决定。";
            default -> "不得自动放行；如确认名单命中，应拒绝业务并按监管要求提交报告。";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
