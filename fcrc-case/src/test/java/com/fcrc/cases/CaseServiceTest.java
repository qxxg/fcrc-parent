package com.fcrc.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcrc.cases.dto.AssignCaseRequest;
import com.fcrc.cases.dto.CreateCaseRequest;
import com.fcrc.cases.dto.UpdateCaseStatusRequest;
import com.fcrc.cases.service.CaseService;
import com.fcrc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 案件状态机集成测试。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:case-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("local")
class CaseServiceTest {

    @Autowired
    private CaseService service;

    @Test
    void fullCaseLifecycle() {
        var created = service.create(new CreateCaseRequest("CUS-TEST", "测试客户", "KYC_REVIEW",
                "HIGH", "测试案件", "测试描述", "SRC-1"));
        assertThat(created.status()).isEqualTo("OPEN");

        var assigned = service.assign(created.caseNo(), new AssignCaseRequest("reviewer.a"));
        assertThat(assigned.status()).isEqualTo("ASSIGNED");

        var inProgress = service.updateStatus(created.caseNo(),
                new UpdateCaseStatusRequest("IN_PROGRESS", null));
        assertThat(inProgress.status()).isEqualTo("IN_PROGRESS");

        var resolved = service.updateStatus(created.caseNo(),
                new UpdateCaseStatusRequest("RESOLVED", "材料齐全，风险可接受"));
        assertThat(resolved.status()).isEqualTo("RESOLVED");

        var closed = service.updateStatus(created.caseNo(),
                new UpdateCaseStatusRequest("CLOSED", "归档"));
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(service.get(created.caseNo()).notes()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void rejectsIllegalTransition() {
        var created = service.create(new CreateCaseRequest("CUS-TEST-2", "测试客户", "KYC_REVIEW",
                "LOW", "非法流转", null, null));
        assertThatThrownBy(() -> service.updateStatus(created.caseNo(),
                new UpdateCaseStatusRequest("CLOSED", "直接关闭")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许");
    }
}
