package com.fcrc.compliance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcrc.common.domain.ComplianceResult;
import com.fcrc.common.domain.CustomerProfile;
import com.fcrc.compliance.dto.ComplianceScreenRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 合规规则回归测试：覆盖通过、人工复核、名单拒绝三条主路径。 */
class ComplianceScreeningEngineTest {

    private final SanctionsList list = new SanctionsList();
    private final ComplianceScreeningEngine engine = new ComplianceScreeningEngine(list);

    @Test
    void cleanCustomerPasses() {
        ComplianceResult result = engine.screen(request("张伟", "China", false, false,
                true, true, "工资", "日常储蓄", "LOW"));
        assertThat(result.result()).isEqualTo("PASS");
        assertThat(result.findings()).extracting(ComplianceResult.Finding::type)
                .doesNotContain("SANCTIONS");
    }

    @Test
    void pepTriggersReview() {
        ComplianceResult result = engine.screen(request("李娜", "China", true, false,
                true, true, "工资", "投资", "MEDIUM"));
        assertThat(result.result()).isEqualTo("REVIEW");
        assertThat(result.findings()).extracting(ComplianceResult.Finding::type).contains("AML");
    }

    @Test
    void watchlistHitFails() {
        ComplianceResult result = engine.screen(request("北辰资本控股", "Cayman Islands", true, false,
                true, false, "投资收益", "投资", "CRITICAL"));
        assertThat(result.result()).isEqualTo("FAIL");
        assertThat(result.riskPoints()).isEqualTo(100);
        assertThat(result.findings()).extracting(ComplianceResult.Finding::type).contains("SANCTIONS");
    }

    @Test
    void missingKycDocumentsScorePoints() {
        ComplianceResult result = engine.screen(request("普通客户", "China", false, false,
                false, false, null, null, "LOW"));
        assertThat(result.riskPoints()).isGreaterThan(0);
        assertThat(result.result()).isIn("REVIEW", "FAIL");
    }

    private ComplianceScreenRequest request(String name, String country, boolean pep,
                                            boolean adverseMedia, boolean identityVerified,
                                            boolean ownerVerified, String source, String purpose,
                                            String riskLevel) {
        return new ComplianceScreenRequest("CUS-TEST",
                new CustomerProfile(name, "INDIVIDUAL", "110101199001011234", "13800000000",
                        null, country, "科技", BigDecimal.valueOf(100_000), "工程师", pep, "WEB"),
                BigDecimal.valueOf(100_000), "TRANSFER", false, source, purpose,
                identityVerified, ownerVerified, adverseMedia, riskLevel);
    }
}
