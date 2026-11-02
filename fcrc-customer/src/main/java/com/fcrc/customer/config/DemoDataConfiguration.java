package com.fcrc.customer.config;

import com.fcrc.customer.domain.CustomerEntity;
import com.fcrc.customer.repository.CustomerRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 演示数据初始化。
 *
 * <p>只在 local profile 生效，避免生产启动时向真实客户库插入样例。
 * 例子覆盖个人/机构、PEP、高风险国家、低风险等典型场景，方便演示规则引擎与人工复核。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "fcrc.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataConfiguration {

    @Bean
    CommandLineRunner seedCustomers(CustomerRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            repository.saveAll(List.of(
                    customer("CUS-DEMO-0001", "张伟", "INDIVIDUAL", "110101199001011234",
                            "13800001234", "zhangwei@example.com", "China", "科技", new BigDecimal("480000"),
                            "软件架构师", false, "WEB", "APPROVED", "COMPLETED", "LOW"),
                    customer("CUS-DEMO-0002", "李娜", "INDIVIDUAL", "310101199205052345",
                            "13900002345", "lina@example.com", "China", "金融", new BigDecimal("1200000"),
                            "投资经理", false, "APP", "IN_REVIEW", "MANUAL_REVIEW", "MEDIUM"),
                    customer("CUS-DEMO-0003", "王强", "INDIVIDUAL", "440301198803033456",
                            "13700003456", "wangqiang@example.com", "Singapore", "国际贸易",
                            new BigDecimal("2500000"), "企业主", true, "BRANCH", "IN_REVIEW", "MANUAL_REVIEW", "HIGH"),
                    customer("CUS-DEMO-0004", "陈静", "INDIVIDUAL", "320101199507074567",
                            "13600004567", "chenjing@example.com", "Iran", "能源", new BigDecimal("300000"),
                            "采购专员", false, "WEB", "REJECTED", "COMPLETED", "CRITICAL"),
                    customer("CUS-DEMO-0005", "华星科技有限公司", "CORPORATE", "91310000MA1K12345X",
                            "021-88886666", "compliance@huaxing.example.com", "China", "跨境电商",
                            new BigDecimal("50000000"), null, false, "PARTNER", "APPROVED", "COMPLETED", "MEDIUM"),
                    customer("CUS-DEMO-0006", "北辰资本控股", "CORPORATE", "KY-889922", "+852-9000-6677",
                            "ops@beichen.example.com", "Cayman Islands", "投资控股",
                            new BigDecimal("180000000"), null, true, "PARTNER", "PENDING", "NOT_STARTED", "HIGH")
            ));
        };
    }

    private static CustomerEntity customer(String no, String name, String type, String idNumber,
                                           String phone, String email, String country, String industry,
                                           BigDecimal income, String occupation, boolean pep, String channel,
                                           String kycStatus, String onboardingStatus, String riskLevel) {
        CustomerEntity entity = new CustomerEntity();
        entity.setCustomerNo(no);
        entity.setName(name);
        entity.setCustomerType(type);
        entity.setIdNumber(idNumber);
        entity.setPhone(phone);
        entity.setEmail(email);
        entity.setCountry(country);
        entity.setIndustry(industry);
        entity.setAnnualIncome(income);
        entity.setOccupation(occupation);
        entity.setPep(pep);
        entity.setChannel(channel);
        entity.setKycStatus(kycStatus);
        entity.setOnboardingStatus(onboardingStatus);
        entity.setRiskLevel(riskLevel);
        entity.setNotes("演示数据，仅用于 local profile");
        return entity;
    }
}
