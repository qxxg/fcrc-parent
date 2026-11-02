package com.fcrc.report.config;

import com.fcrc.report.dto.GenerateReportRequest;
import com.fcrc.report.repository.RegulatoryReportRepository;
import com.fcrc.report.service.ReportService;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** local profile 的监管报告演示数据。 */
@Configuration
@ConditionalOnProperty(prefix = "fcrc.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReportDemoDataConfiguration {

    @Bean
    CommandLineRunner seedReports(RegulatoryReportRepository repository, ReportService service) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            var approved = service.generate(new GenerateReportRequest(
                    "CUS-DEMO-0001", "WF-DEMO-0001", "CUSTOMER_RISK", "2026-10", "APPROVED",
                    8, "LOW", "PASS", List.of("基础风险 5 分"), List.of(),
                    "客户资料完整，风险水平低，适用标准尽职调查。"));
            service.finalizeReport(approved.reportNo());
            service.submit(approved.reportNo());

            var manual = service.generate(new GenerateReportRequest(
                    "CUS-DEMO-0002", "WF-DEMO-0002", "CUSTOMER_RISK", "2026-10", "MANUAL_REVIEW",
                    38, "MEDIUM", "REVIEW", List.of("大额交易", "跨境交易"),
                    List.of("负面媒体筛查无命中"), "等待合规官复核资金来源材料。"));
            service.finalizeReport(manual.reportNo());

            service.generate(new GenerateReportRequest(
                    "CUS-DEMO-0006", "WF-DEMO-0006", "SAR", "2026-10", "REJECTED",
                    100, "CRITICAL", "FAIL", List.of("高风险辖区", "PEP", "境外机构"),
                    List.of("制裁名单命中", "受益所有人未核验"),
                    "疑似制裁名单命中，建议提交可疑活动报告并冻结业务关系。"));
        };
    }
}
