package com.fcrc.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fcrc.common.domain.CustomerProfile;
import com.fcrc.workflow.client.CaseClient;
import com.fcrc.workflow.client.ComplianceClient;
import com.fcrc.workflow.client.CustomerClient;
import com.fcrc.workflow.client.ReportClient;
import com.fcrc.workflow.client.RiskClient;
import com.fcrc.workflow.dto.OnboardingRequest;
import com.fcrc.workflow.dto.ReviewRequest;
import com.fcrc.workflow.repository.WorkflowCheckpointRepository;
import com.fcrc.workflow.repository.WorkflowEventRepository;
import com.fcrc.workflow.service.WorkflowRunService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 编排图集成测试。
 *
 * <p>测试重点不是“接口能不能返回 200”，而是验证图的三条关键路径：</p>
 * <ol>
 *   <li>低风险 + 合规通过 → 自动审批 → 完成；</li>
 *   <li>高风险 → 在 manual_review 前暂停 → 复核批准 → 完成；</li>
 *   <li>人工拒绝 → 拒绝收尾，客户状态回写 REJECTED。</li>
 * </ol>
 *
 * <p>下游服务用 Mockito 替换，但 Graph、检查点、事件、运行状态同步都使用真实实现，
 * 因此能覆盖“暂停后从数据库恢复”的核心逻辑。</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workflow-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false"
})
@ActiveProfiles("local")
class WorkflowRunServiceTest {

    @Autowired
    private WorkflowRunService service;

    @Autowired
    private WorkflowCheckpointRepository checkpointRepository;

    @Autowired
    private WorkflowEventRepository eventRepository;

    @MockitoBean
    private CustomerClient customerClient;

    @MockitoBean
    private RiskClient riskClient;

    @MockitoBean
    private ComplianceClient complianceClient;

    @MockitoBean
    private CaseClient caseClient;

    @MockitoBean
    private ReportClient reportClient;

    private final CustomerProfile profile = new CustomerProfile(
            "张三", "INDIVIDUAL", "110101199001011234", "13800001111",
            "zhangsan@example.com", "China", "科技", new BigDecimal("600000"),
            "工程师", false, "WEB");

    @Test
    void lowRiskCustomerIsAutoApproved() {
        stubProfile(1L, "CUS-AUTO");
        when(riskClient.assess(any())).thenReturn(new RiskClient.Assessment(
                "RSK-AUTO", "CUS-AUTO", 12, "LOW",
                List.of(new RiskClient.Factor("BASE", "基础风险", 5, "基础")), "标准尽职调查"));
        when(complianceClient.screen(any())).thenReturn(new ComplianceClient.Screening(
                "SCR-AUTO", "CUS-AUTO", "PASS", 0, List.of(), "通过"));
        when(reportClient.generateAndFinalize(any())).thenReturn("RPT-AUTO");
        doNothing().when(customerClient).updateOnboarding(anyLong(), anyString(), any(), any());

        var detail = service.start(request(1L));

        assertThat(detail.run().status()).isEqualTo("COMPLETED");
        assertThat(detail.run().decision()).isEqualTo("APPROVED");
        assertThat(detail.run().preliminaryDecision()).isEqualTo("AUTO_APPROVE");
        assertThat(detail.run().reportNo()).isEqualTo("RPT-AUTO");
        assertThat(detail.run().onboardingStatus()).isEqualTo("COMPLETED");
        assertThat(detail.run().canResume()).isFalse();
        assertThat(detail.events()).extracting("type")
                .contains("WORKFLOW_STARTED", "NODE_COMPLETED", "WORKFLOW_COMPLETED");
        assertThat(checkpointRepository.findByThreadIdOrderByIdDesc(detail.run().runNo())).isNotEmpty();
        assertThat(eventRepository.countByRunNo(detail.run().runNo())).isGreaterThan(5);

        verify(customerClient).updateOnboarding(eq(1L), eq("RUNNING"), isNull(), anyString());
        verify(customerClient).updateOnboarding(eq(1L), eq("COMPLETED"), eq("LOW"), anyString());
    }

    @Test
    void highRiskCustomerPausesForReviewAndCanBeApproved() {
        stubProfile(2L, "CUS-REVIEW");
        when(riskClient.assess(any())).thenReturn(new RiskClient.Assessment(
                "RSK-REVIEW", "CUS-REVIEW", 72, "CRITICAL",
                List.of(new RiskClient.Factor("PEP", "政治公众人物", 25, "PEP")), "人工复核"));
        when(complianceClient.screen(any())).thenReturn(new ComplianceClient.Screening(
                "SCR-REVIEW", "CUS-REVIEW", "REVIEW", 45,
                List.of(new ComplianceClient.Finding("AML", "HIGH", "需要人工判断")), "人工复核"));
        when(caseClient.createReviewCase(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("CASE-REVIEW");
        when(reportClient.generateAndFinalize(any())).thenReturn("RPT-REVIEW");
        doNothing().when(customerClient).updateOnboarding(anyLong(), anyString(), any(), any());

        var started = service.start(request(2L));
        assertThat(started.run().status()).isEqualTo("WAITING_REVIEW");
        assertThat(started.run().currentNode()).isEqualTo("manual_review");
        assertThat(started.run().caseNo()).isEqualTo("CASE-REVIEW");
        assertThat(started.run().onboardingStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(started.run().canResume()).isTrue();

        var finished = service.review(started.run().runNo(),
                new ReviewRequest("APPROVED", "reviewer.a", "已核实资金来源与业务背景"));

        assertThat(finished.run().status()).isEqualTo("COMPLETED");
        assertThat(finished.run().decision()).isEqualTo("APPROVED");
        assertThat(finished.run().reviewer()).isEqualTo("reviewer.a");
        assertThat(finished.run().reviewDecision()).isEqualTo("APPROVED");
        assertThat(finished.run().reportNo()).isEqualTo("RPT-REVIEW");
        assertThat(finished.run().onboardingStatus()).isEqualTo("COMPLETED");
        assertThat(finished.events()).extracting("type")
                .contains("HUMAN_REVIEW_REQUIRED", "HUMAN_REVIEW_SUBMITTED", "WORKFLOW_COMPLETED");

        verify(customerClient).updateOnboarding(eq(2L), eq("MANUAL_REVIEW"), eq("CRITICAL"), anyString());
        verify(customerClient).updateOnboarding(eq(2L), eq("COMPLETED"), eq("CRITICAL"), anyString());
    }

    @Test
    void reviewerCanRejectAndCustomerStatusIsWrittenBack() {
        stubProfile(3L, "CUS-REJECT");
        when(riskClient.assess(any())).thenReturn(new RiskClient.Assessment(
                "RSK-REJECT", "CUS-REJECT", 65, "CRITICAL", List.of(), "人工复核"));
        when(complianceClient.screen(any())).thenReturn(new ComplianceClient.Screening(
                "SCR-REJECT", "CUS-REJECT", "FAIL", 90,
                List.of(new ComplianceClient.Finding("SANCTIONS", "HIGH", "命中制裁名单")), "拒绝"));
        when(caseClient.createReviewCase(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("CASE-REJECT");
        when(reportClient.generateAndFinalize(any())).thenReturn("RPT-REJECT");
        doNothing().when(customerClient).updateOnboarding(anyLong(), anyString(), any(), any());

        var started = service.start(request(3L));
        assertThat(started.run().status()).isEqualTo("WAITING_REVIEW");

        var finished = service.review(started.run().runNo(),
                new ReviewRequest("REJECTED", "reviewer.b", "制裁名单命中，风险不可接受"));

        assertThat(finished.run().status()).isEqualTo("REJECTED");
        assertThat(finished.run().decision()).isEqualTo("REJECTED");
        assertThat(finished.run().onboardingStatus()).isEqualTo("REJECTED");
        assertThat(finished.run().currentNode()).isEqualTo("finalize_rejection");
        verify(customerClient).updateOnboarding(eq(3L), eq("REJECTED"), eq("CRITICAL"), anyString());
    }

    private void stubProfile(Long customerId, String customerNo) {
        when(customerClient.profile(customerId))
                .thenReturn(new CustomerClient.InternalProfile(customerNo, profile));
    }

    private OnboardingRequest request(Long customerId) {
        return new OnboardingRequest(customerId, new BigDecimal("120000"), "DEPOSIT",
                false, 3, true, false, "工资收入", "日常储蓄", true, true, false);
    }
}
