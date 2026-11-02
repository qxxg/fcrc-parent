package com.fcrc.risk.config;

import com.fcrc.common.domain.CustomerProfile;
import com.fcrc.risk.dto.RiskAssessmentRequest;
import com.fcrc.risk.repository.RiskAssessmentRepository;
import com.fcrc.risk.service.RiskService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** local profile 的风险评估演示数据，覆盖 LOW / MEDIUM / HIGH / CRITICAL。 */
@Configuration
@ConditionalOnProperty(prefix = "fcrc.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RiskDemoDataConfiguration {

    @Bean
    CommandLineRunner seedRiskAssessments(RiskAssessmentRepository repository, RiskService service) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            buildRequests().forEach(service::assess);
        };
    }

    private List<RiskAssessmentRequest> buildRequests() {
        return List.of(
                request("CUS-DEMO-0001", "张伟", "INDIVIDUAL", "China", "科技", "480000",
                        false, false, new BigDecimal("120000"), false, 2, false, "INVESTMENT"),
                request("CUS-DEMO-0002", "李娜", "INDIVIDUAL", "China", "金融", "1200000",
                        false, false, new BigDecimal("1500000"), true, 12, false, "TRANSFER"),
                request("CUS-DEMO-0003", "王强", "INDIVIDUAL", "Singapore", "国际贸易", "2500000",
                        true, false, new BigDecimal("3000000"), true, 30, false, "TRANSFER"),
                request("CUS-DEMO-0004", "陈静", "INDIVIDUAL", "Iran", "能源", "300000",
                        false, false, new BigDecimal("5000000"), true, 8, true, "TRANSFER"),
                request("CUS-DEMO-0006", "北辰资本控股", "CORPORATE", "Cayman Islands", "投资控股",
                        "180000000", true, true, new BigDecimal("8000000"), true, 55, true, "INVESTMENT"));
    }

    private RiskAssessmentRequest request(String no, String name, String type, String country,
                                          String industry, String income, boolean pep,
                                          boolean highRiskProduct, BigDecimal amount, boolean crossBorder,
                                          int count, boolean newRelationship, String txType) {
        return new RiskAssessmentRequest(no,
                new CustomerProfile(name, type, "ID-" + no, "13800000000", null, country, industry,
                        new BigDecimal(income), "经理", pep, "WEB"),
                amount, txType, crossBorder, count, newRelationship, highRiskProduct);
    }
}
