package com.fcrc.compliance.config;

import com.fcrc.common.domain.CustomerProfile;
import com.fcrc.compliance.dto.ComplianceScreenRequest;
import com.fcrc.compliance.repository.ScreeningRepository;
import com.fcrc.compliance.service.ComplianceService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** local profile 的合规筛查演示数据。 */
@Configuration
@ConditionalOnProperty(prefix = "fcrc.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ComplianceDemoDataConfiguration {

    @Bean
    CommandLineRunner seedScreenings(ScreeningRepository repository, ComplianceService service) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            List.of(
                    request("CUS-DEMO-0001", "张伟", "INDIVIDUAL", "China", "科技",
                            false, false, true, true, new BigDecimal("120000"), false, "LOW", true),
                    request("CUS-DEMO-0002", "李娜", "INDIVIDUAL", "China", "金融",
                            true, false, true, true, new BigDecimal("1500000"), true, "HIGH", true),
                    request("CUS-DEMO-0003", "王强", "INDIVIDUAL", "Singapore", "国际贸易",
                            true, false, true, false, new BigDecimal("3000000"), true, "HIGH", true),
                    request("CUS-DEMO-0004", "陈静", "INDIVIDUAL", "Iran", "能源",
                            false, false, false, true, new BigDecimal("5000000"), true, "CRITICAL", true),
                    request("CUS-DEMO-0006", "北辰资本控股", "CORPORATE", "Cayman Islands", "投资控股",
                            true, false, true, false, new BigDecimal("8000000"), true, "CRITICAL", false)
            ).forEach(service::screen);
        };
    }

    private ComplianceScreenRequest request(String no, String name, String type, String country,
                                            String industry, boolean pep, boolean adverseMedia,
                                            boolean identityVerified, boolean ownerVerified,
                                            BigDecimal amount, boolean crossBorder, String riskLevel,
                                            boolean sourceProvided) {
        return new ComplianceScreenRequest(no,
                new CustomerProfile(name, type, "ID-" + no, "13800000000", null, country, industry,
                        new BigDecimal("1000000"), "经理", pep, "WEB"),
                amount, "TRANSFER", crossBorder,
                sourceProvided ? "工资及投资收益" : null,
                sourceProvided ? "跨境投资" : null,
                identityVerified, ownerVerified, adverseMedia, riskLevel);
    }
}
