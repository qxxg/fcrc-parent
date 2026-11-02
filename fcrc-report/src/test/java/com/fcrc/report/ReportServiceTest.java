package com.fcrc.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcrc.common.exception.BusinessException;
import com.fcrc.report.dto.GenerateReportRequest;
import com.fcrc.report.service.ReportService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 报告状态机与导出测试。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:report-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("local")
class ReportServiceTest {

    @Autowired
    private ReportService service;

    @Test
    void reportMustBeFinalizedBeforeSubmission() {
        var report = service.generate(request("CUS-TEST-REPORT"));
        assertThat(report.status()).isEqualTo("DRAFT");
        assertThatThrownBy(() -> service.submit(report.reportNo()))
                .isInstanceOf(BusinessException.class);
        service.finalizeReport(report.reportNo());
        var submitted = service.submit(report.reportNo());
        assertThat(submitted.status()).isEqualTo("SUBMITTED");
    }

    @Test
    void exportsCsvWithBomAndChineseContent() {
        var report = service.generate(request("CUS-TEST-CSV"));
        byte[] csv = service.exportCsv(report.reportNo());
        assertThat(csv).isNotEmpty();
        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(new String(csv, java.nio.charset.StandardCharsets.UTF_8)).contains("报告编号");
    }

    private GenerateReportRequest request(String customerNo) {
        return new GenerateReportRequest(customerNo, "WF-TEST", "CUSTOMER_RISK", "2026-11",
                "APPROVED", 10, "LOW", "PASS", List.of("基础风险"), List.of(),
                "测试说明");
    }
}
