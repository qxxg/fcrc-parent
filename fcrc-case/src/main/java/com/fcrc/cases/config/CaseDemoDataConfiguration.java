package com.fcrc.cases.config;

import com.fcrc.cases.dto.AssignCaseRequest;
import com.fcrc.cases.dto.CreateCaseRequest;
import com.fcrc.cases.repository.CaseRepository;
import com.fcrc.cases.service.CaseService;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** local profile 的合规案件演示数据。 */
@Configuration
@ConditionalOnProperty(prefix = "fcrc.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CaseDemoDataConfiguration {

    @Bean
    CommandLineRunner seedCases(CaseRepository repository, CaseService service) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            var high = service.create(new CreateCaseRequest("CUS-DEMO-0003", "王强", "KYC_REVIEW",
                    "HIGH", "PEP 客户增强尽调材料复核",
                    "客户为 PEP，需核验资金来源、财富来源及业务关系目的。", "WF-DEMO-0001"));
            service.assign(high.caseNo(), new AssignCaseRequest("compliance.li"));
            var critical = service.create(new CreateCaseRequest("CUS-DEMO-0006", "北辰资本控股",
                    "SANCTIONS_HIT", "CRITICAL", "疑似制裁名单命中",
                    "名称与 OFAC SDN 名单实体高度匹配，需在 24 小时内完成复核。", "SCR-DEMO-0006"));
            service.assign(critical.caseNo(), new AssignCaseRequest("compliance.wang"));
            service.create(new CreateCaseRequest("CUS-DEMO-0002", "李娜", "TRANSACTION_MONITORING",
                    "MEDIUM", "大额跨境交易持续监测",
                    "客户近 30 天跨境交易金额显著高于历史均值。", "RSK-DEMO-0002"));
            service.create(new CreateCaseRequest("CUS-DEMO-0004", "陈静", "ADVERSE_MEDIA",
                    "HIGH", "高风险辖区负面媒体线索",
                    "外部负面媒体供应商返回一条待核实线索。", "SCR-DEMO-0004"));
        };
    }
}
