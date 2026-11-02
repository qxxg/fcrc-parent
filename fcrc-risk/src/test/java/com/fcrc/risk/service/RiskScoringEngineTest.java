package com.fcrc.risk.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcrc.common.domain.CustomerProfile;
import com.fcrc.common.domain.RiskResult;
import com.fcrc.risk.dto.RiskAssessmentRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 规则引擎单元测试：保证风险阈值和关键因子的回归稳定性。 */
class RiskScoringEngineTest {

    private final RiskScoringEngine engine = new RiskScoringEngine();

    @Test
    void lowRiskCustomerScoresLow() {
        RiskResult result = engine.evaluate(request("China", false, BigDecimal.valueOf(100_000),
                false, 1, false, false, "INDIVIDUAL"));
        assertThat(result.score()).isLessThan(20);
        assertThat(result.level()).isEqualTo("LOW");
        assertThat(result.factors()).extracting(RiskResult.RiskFactor::code).contains("BASE");
    }

    @Test
    void pepAndHighRiskCountryProducesCritical() {
        RiskResult result = engine.evaluate(request("Iran", true, BigDecimal.valueOf(5_000_000),
                true, 30, true, true, "INDIVIDUAL"));
        assertThat(result.score()).isGreaterThanOrEqualTo(60);
        assertThat(result.level()).isEqualTo("CRITICAL");
        assertThat(result.factors()).extracting(RiskResult.RiskFactor::code)
                .contains("HIGH_RISK_COUNTRY", "PEP", "VERY_LARGE_AMOUNT", "CROSS_BORDER");
    }

    @Test
    void scoreIsCappedAtOneHundred() {
        RiskResult result = engine.evaluate(request("North Korea", true, BigDecimal.valueOf(50_000_000),
                true, 100, true, true, "CORPORATE"));
        assertThat(result.score()).isEqualTo(100);
    }

    private RiskAssessmentRequest request(String country, boolean pep, BigDecimal amount,
                                          boolean crossBorder, int count, boolean newRelationship,
                                          boolean highRiskProduct, String type) {
        CustomerProfile profile = new CustomerProfile("测试客户", type, "110101199001011234",
                "13800000000", null, country, "cryptocurrency", BigDecimal.valueOf(10_000_000),
                "经理", pep, "WEB");
        return new RiskAssessmentRequest("CUS-TEST", profile, amount, "TRANSFER",
                crossBorder, count, newRelationship, highRiskProduct);
    }
}
