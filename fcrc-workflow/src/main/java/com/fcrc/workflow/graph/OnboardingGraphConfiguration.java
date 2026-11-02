package com.fcrc.workflow.graph;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.fcrc.common.domain.CustomerProfile;
import com.fcrc.workflow.checkpoint.JpaCheckpointSaver;
import com.fcrc.workflow.client.CaseClient;
import com.fcrc.workflow.client.ComplianceClient;
import com.fcrc.workflow.client.CustomerClient;
import com.fcrc.workflow.client.ReportClient;
import com.fcrc.workflow.client.RiskClient;
import com.fcrc.workflow.dto.OnboardingRequest;
import com.fcrc.workflow.event.WorkflowEventRecorder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

/**
 * 开户审批图定义。
 *
 * <p>这是整个平台最核心的教学文件。请按以下顺序阅读：</p>
 *
 * <h2>1. 为什么用 StateGraph，而不是在 Service 里写 if/else？</h2>
 * <p>审批不是一次函数调用，而是一个有状态、可暂停、可恢复、需要审计的流程。
 * 把节点、边、条件路由显式建模后，流程结构可以被画出来、被测试、被单步观测；
 * 新增节点也不需要修改已有服务，只需改图。</p>
 *
 * <h2>2. 图的形状</h2>
 * <pre>
 * START
 *   └─ load_customer
 *        ├─ risk_assess ─────┐
 *        └─ compliance_screen ┴─ aggregate
 *                                  ├─(AUTO_APPROVE)── auto_approve ── finalize_approval ── END
 *                                  └─(MANUAL_REVIEW)─ manual_review ─┬─ finalize_approval ── END
 *                                                                    └─ finalize_rejection ─ END
 * </pre>
 *
 * <h2>3. 并行与汇聚</h2>
 * <p>{@code load_customer} 同时连向风险与合规两个节点，Graph 编译时会自动生成
 * {@code ParallelNode}：两个节点并发执行，全部完成后才进入 {@code aggregate}。
 * 两路分支只能读取 load_customer 已写入的状态，不能互相依赖——这是并行分支的边界。</p>
 *
 * <h2>4. Human-in-the-loop</h2>
 * <p>{@code CompileConfig.interruptBefore("manual_review")} 使图在人工节点前暂停。
 * 暂停不是“线程挂起”，而是把检查点写入数据库并结束本次调用；复核意见到达后，
 * 用 {@code RunnableConfig.withResume()} 从检查点继续。这样服务可以重启、可以扩容。</p>
 */
@Configuration
public class OnboardingGraphConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OnboardingGraphConfiguration.class);

    /** 节点 id 常量，避免在边定义和事件里出现拼写错误。 */
    public static final String LOAD_CUSTOMER = "load_customer";
    public static final String RISK_ASSESS = "risk_assess";
    public static final String COMPLIANCE_SCREEN = "compliance_screen";
    public static final String AGGREGATE = "aggregate";
    public static final String AUTO_APPROVE = "auto_approve";
    public static final String MANUAL_REVIEW = "manual_review";
    public static final String FINALIZE_APPROVAL = "finalize_approval";
    public static final String FINALIZE_REJECTION = "finalize_rejection";

    /** 自动审批阈值：低于该分数且合规通过、非 PEP，才允许自动通过。 */
    private static final int AUTO_APPROVE_SCORE_LIMIT = 40;

    @Bean
    public StateSerializer workflowStateSerializer() {
        // 使用 Graph 自带的 Jackson 序列化器：状态可读、跨语言、便于排查。
        return StateGraph.DEFAULT_JACKSON_SERIALIZER;
    }

    @Bean
    public JpaCheckpointSaver jpaCheckpointSaver(
            com.fcrc.workflow.repository.WorkflowCheckpointRepository repository,
            StateSerializer workflowStateSerializer) {
        return new JpaCheckpointSaver(repository, workflowStateSerializer);
    }

    @Bean
    public CompiledGraph onboardingGraph(
            CustomerClient customerClient,
            RiskClient riskClient,
            ComplianceClient complianceClient,
            CaseClient caseClient,
            ReportClient reportClient,
            WorkflowEventRecorder recorder,
            StateSerializer workflowStateSerializer,
            JpaCheckpointSaver checkpointSaver) throws Exception {

        KeyStrategyFactory keyStrategies = WorkflowState.keyStrategyFactory();
        StateGraph graph = new StateGraph("customer-onboarding", keyStrategies, workflowStateSerializer);

        graph.addNode(LOAD_CUSTOMER, tracked(LOAD_CUSTOMER, recorder, state -> loadCustomer(state, customerClient)));
        graph.addNode(RISK_ASSESS, tracked(RISK_ASSESS, recorder, state -> assessRisk(state, riskClient)));
        graph.addNode(COMPLIANCE_SCREEN,
                tracked(COMPLIANCE_SCREEN, recorder, state -> screenCompliance(state, complianceClient)));
        graph.addNode(AGGREGATE, tracked(AGGREGATE, recorder,
                state -> aggregate(state, caseClient, customerClient)));
        graph.addNode(AUTO_APPROVE, tracked(AUTO_APPROVE, recorder, this::autoApprove));
        graph.addNode(MANUAL_REVIEW, tracked(MANUAL_REVIEW, recorder, this::applyHumanReview));
        graph.addNode(FINALIZE_APPROVAL, tracked(FINALIZE_APPROVAL, recorder,
                state -> finalize(state, reportClient, customerClient, "COMPLETED")));
        graph.addNode(FINALIZE_REJECTION, tracked(FINALIZE_REJECTION, recorder,
                state -> finalize(state, reportClient, customerClient, "REJECTED")));

        graph.addEdge(START, LOAD_CUSTOMER);

        // 扇出：Graph 会把它编译为一个 ParallelNode，风险与合规并发执行。
        graph.addEdge(LOAD_CUSTOMER, List.of(RISK_ASSESS, COMPLIANCE_SCREEN));
        // 扇入：两个分支都指向 aggregate，ParallelNode 等待 ALL_OF 后只调用一次汇聚节点。
        graph.addEdge(List.of(RISK_ASSESS, COMPLIANCE_SCREEN), AGGREGATE);

        // 条件路由：聚合节点把结论写入 preliminaryDecision，边只负责“读结论、选方向”。
        graph.addConditionalEdges(AGGREGATE,
                AsyncEdgeAction.edge_async(state -> WorkflowState.string(state, WorkflowState.PRELIMINARY_DECISION)),
                Map.of("AUTO_APPROVE", AUTO_APPROVE, "MANUAL_REVIEW", MANUAL_REVIEW));

        graph.addEdge(AUTO_APPROVE, FINALIZE_APPROVAL);

        // 人工复核后再按复核结论分流：批准走正常收尾，拒绝走拒绝收尾。
        graph.addConditionalEdges(MANUAL_REVIEW,
                AsyncEdgeAction.edge_async(state -> "APPROVED".equals(WorkflowState.string(state, WorkflowState.DECISION))
                        ? FINALIZE_APPROVAL : FINALIZE_REJECTION),
                Map.of(FINALIZE_APPROVAL, FINALIZE_APPROVAL, FINALIZE_REJECTION, FINALIZE_REJECTION));

        graph.addEdge(FINALIZE_APPROVAL, END);
        graph.addEdge(FINALIZE_REJECTION, END);

        CompileConfig compileConfig = CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(checkpointSaver).build())
                // 教学点：在人工节点执行前中断。恢复时该节点会从头执行一次。
                .interruptBefore(MANUAL_REVIEW)
                .recursionLimit(50)
                .build();

        CompiledGraph compiled = graph.compile(compileConfig);
        log.info("Onboarding graph compiled: nodes=8, interruptBefore={}", MANUAL_REVIEW);
        return compiled;
    }

    // ================================================================================================
    // 节点实现
    // ================================================================================================

    private Map<String, Object> loadCustomer(OverAllState state, CustomerClient customerClient) {
        OnboardingRequest request = onboardingRequest(state);
        CustomerClient.InternalProfile profile = customerClient.profile(request.customerId());
        if (profile == null || profile.profile() == null) {
            throw new IllegalStateException("客户不存在或档案为空: " + request.customerId());
        }
        customerClient.updateOnboarding(request.customerId(), "RUNNING", null, "开户编排已启动");

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(WorkflowState.CUSTOMER_NO, profile.customerNo());
        updates.put(WorkflowState.CUSTOMER_NAME, profile.profile().name());
        updates.put(WorkflowState.CUSTOMER_PROFILE, profile.profile());
        updates.put(WorkflowState.ONBOARDING_STATUS, "RUNNING");
        return updates;
    }

    private Map<String, Object> assessRisk(OverAllState state, RiskClient riskClient) {
        OnboardingRequest request = onboardingRequest(state);
        CustomerProfile profile = WorkflowState.profile(state);
        Objects.requireNonNull(profile, "客户档案缺失，无法评估风险");

        RiskClient.Assessment assessment = riskClient.assess(new RiskClient.RiskRequest(
                WorkflowState.string(state, WorkflowState.CUSTOMER_NO),
                profile,
                request.transactionAmount(),
                request.transactionType(),
                request.crossBorder(),
                Optional.ofNullable(request.recentTransactionCount()).orElse(0),
                request.newRelationship(),
                request.highRiskProduct()));

        List<String> factors = assessment.factors() == null ? List.of()
                : assessment.factors().stream()
                        .map(f -> f.label() + " (+" + f.points() + ")")
                        .toList();

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(WorkflowState.RISK_ASSESSMENT_NO, assessment.assessmentNo());
        updates.put(WorkflowState.RISK_SCORE, assessment.score());
        updates.put(WorkflowState.RISK_LEVEL, assessment.level());
        updates.put(WorkflowState.RISK_FACTORS, factors);
        updates.put(WorkflowState.RISK_RECOMMENDATION, assessment.recommendation());
        return updates;
    }

    private Map<String, Object> screenCompliance(OverAllState state, ComplianceClient complianceClient) {
        OnboardingRequest request = onboardingRequest(state);
        CustomerProfile profile = WorkflowState.profile(state);
        Objects.requireNonNull(profile, "客户档案缺失，无法进行合规筛查");

        ComplianceClient.Screening screening = complianceClient.screen(new ComplianceClient.ScreenRequest(
                WorkflowState.string(state, WorkflowState.CUSTOMER_NO),
                profile,
                request.transactionAmount(),
                request.transactionType(),
                request.crossBorder(),
                request.sourceOfFunds(),
                request.businessPurpose(),
                request.identityDocumentVerified(),
                request.beneficialOwnerVerified(),
                request.adverseMediaFlag(),
                // 并行约束：此时风险节点可能尚未完成，不能依赖 riskLevel。
                null));

        List<String> findings = screening.findings() == null ? List.of()
                : screening.findings().stream()
                        .map(f -> f.type() + "/" + f.severity() + ": " + f.description())
                        .toList();

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(WorkflowState.COMPLIANCE_SCREENING_NO, screening.screeningNo());
        updates.put(WorkflowState.COMPLIANCE_RESULT, screening.result());
        updates.put(WorkflowState.COMPLIANCE_FINDINGS, findings);
        updates.put(WorkflowState.COMPLIANCE_RECOMMENDATION, screening.recommendation());
        updates.put(WorkflowState.COMPLIANCE_RISK_POINTS, screening.riskPoints());
        return updates;
    }

    /**
     * 汇聚与路由决策。
     *
     * <p>决策规则（教学用，生产需由合规政策中心配置并留痕）：</p>
     * <ul>
     *   <li>合规 PASS 且风险分低于 40 且客户非 PEP → 自动通过；</li>
     *   <li>其他情况 → 人工复核，并创建合规案件。</li>
     * </ul>
     *
     * <p>注意：这里把“决策”和“执行”分开。aggregate 只决定下一步，不直接改客户状态；
     * 真正的状态回写放在 finalize 节点，便于失败重试时保持幂等边界清晰。</p>
     */
    private Map<String, Object> aggregate(OverAllState state, CaseClient caseClient,
                                          CustomerClient customerClient) {
        Integer score = Optional.ofNullable(WorkflowState.integer(state, WorkflowState.RISK_SCORE)).orElse(100);
        String level = Optional.ofNullable(WorkflowState.string(state, WorkflowState.RISK_LEVEL)).orElse("CRITICAL");
        String complianceResult = Optional.ofNullable(
                WorkflowState.string(state, WorkflowState.COMPLIANCE_RESULT)).orElse("REVIEW");
        CustomerProfile profile = WorkflowState.profile(state);

        boolean auto = "PASS".equalsIgnoreCase(complianceResult)
                && score < AUTO_APPROVE_SCORE_LIMIT
                && (profile == null || !profile.pep());

        Map<String, Object> updates = new LinkedHashMap<>();
        if (auto) {
            updates.put(WorkflowState.PRELIMINARY_DECISION, "AUTO_APPROVE");
            updates.put(WorkflowState.DECISION_REASON,
                    "风险分 " + score + "（" + level + "）、合规结论 PASS，满足自动审批策略");
            return updates;
        }

        String reason;
        if (!"PASS".equalsIgnoreCase(complianceResult)) {
            reason = "合规结论为 " + complianceResult + "，必须人工复核";
        } else if (profile != null && profile.pep()) {
            reason = "客户为政治公众人物（PEP），必须人工复核";
        } else {
            reason = "风险分 " + score + "（" + level + "）达到人工复核阈值";
        }

        String caseNo = caseClient.createReviewCase(
                WorkflowState.string(state, WorkflowState.CUSTOMER_NO),
                WorkflowState.string(state, WorkflowState.CUSTOMER_NAME),
                priorityOf(level),
                "开户人工复核 - " + WorkflowState.string(state, WorkflowState.CUSTOMER_NAME),
                reason + "；runNo=" + WorkflowState.string(state, WorkflowState.RUN_NO),
                WorkflowState.string(state, WorkflowState.RUN_NO));

        // 暂停前把客户状态改为 MANUAL_REVIEW，前端列表能立即看到“等待人工”。
        customerClient.updateOnboarding(WorkflowState.longValue(state, WorkflowState.CUSTOMER_ID),
                "MANUAL_REVIEW", level, reason);

        updates.put(WorkflowState.PRELIMINARY_DECISION, "MANUAL_REVIEW");
        updates.put(WorkflowState.DECISION_REASON, reason);
        updates.put(WorkflowState.CASE_NO, caseNo);
        updates.put(WorkflowState.ONBOARDING_STATUS, "MANUAL_REVIEW");
        return updates;
    }

    private Map<String, Object> autoApprove(OverAllState state) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(WorkflowState.DECISION, "APPROVED");
        updates.put(WorkflowState.DECISION_REASON,
                Optional.ofNullable(WorkflowState.string(state, WorkflowState.DECISION_REASON))
                        .orElse("满足自动审批策略"));
        return updates;
    }

    /**
     * 人工复核节点。
     *
     * <p>该节点只在 resume 调用中执行。复核输入由 {@code invoke} 的 inputs 合并进状态，
     * 因此这里读取到的 reviewDecision/reviewer/reviewComment 是最新值。</p>
     */
    private Map<String, Object> applyHumanReview(OverAllState state) {
        String reviewDecision = WorkflowState.string(state, WorkflowState.REVIEW_DECISION);
        if (!"APPROVED".equals(reviewDecision) && !"REJECTED".equals(reviewDecision)) {
            throw new IllegalStateException("人工复核结论必须为 APPROVED 或 REJECTED");
        }
        String reviewer = WorkflowState.string(state, WorkflowState.REVIEWER);
        String comment = WorkflowState.string(state, WorkflowState.REVIEW_COMMENT);

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(WorkflowState.DECISION, "APPROVED".equals(reviewDecision) ? "APPROVED" : "REJECTED");
        updates.put(WorkflowState.DECISION_REASON,
                comment == null || comment.isBlank()
                        ? "人工复核结论：" + reviewDecision
                        : comment);
        updates.put(WorkflowState.REVIEWER, reviewer);
        updates.put(WorkflowState.REVIEW_COMMENT, comment);
        return updates;
    }

    /**
     * 收尾节点：生成监管报告、回写客户状态。
     *
     * <p>顺序很重要：先生成报告，再回写客户。如果报告服务失败，客户状态不会被误改为完成，
     * 运行会被标记 FAILED，人工可以修复下游后重新发起。生产环境更推荐 Saga/Outbox
     * 来保证跨服务最终一致性，而不是依赖调用顺序。</p>
     */
    private Map<String, Object> finalize(OverAllState state, ReportClient reportClient,
                                         CustomerClient customerClient, String onboardingStatus) {
        String decision = WorkflowState.string(state, WorkflowState.DECISION);
        Integer score = WorkflowState.integer(state, WorkflowState.RISK_SCORE);
        String level = WorkflowState.string(state, WorkflowState.RISK_LEVEL);
        String complianceResult = WorkflowState.string(state, WorkflowState.COMPLIANCE_RESULT);
        List<String> riskFactors = WorkflowState.stringList(state, WorkflowState.RISK_FACTORS);
        List<String> findings = WorkflowState.stringList(state, WorkflowState.COMPLIANCE_FINDINGS);

        String narrative = "客户 " + WorkflowState.string(state, WorkflowState.CUSTOMER_NO)
                + " 的开户审批结论为 " + decision
                + "。风险评分 " + score + "（" + level + "），合规筛查 " + complianceResult
                + "。决策依据：" + WorkflowState.string(state, WorkflowState.DECISION_REASON);

        String reportNo = reportClient.generateAndFinalize(new ReportClient.GenerateReport(
                WorkflowState.string(state, WorkflowState.CUSTOMER_NO),
                WorkflowState.string(state, WorkflowState.RUN_NO),
                "CUSTOMER_RISK",
                java.time.YearMonth.now().toString(),
                decision,
                score,
                level,
                complianceResult,
                riskFactors,
                findings,
                narrative));

        customerClient.updateOnboarding(WorkflowState.longValue(state, WorkflowState.CUSTOMER_ID),
                onboardingStatus, level,
                WorkflowState.string(state, WorkflowState.DECISION_REASON));

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(WorkflowState.REPORT_NO, reportNo);
        updates.put(WorkflowState.ONBOARDING_STATUS, onboardingStatus);
        return updates;
    }

    // ================================================================================================
    // 通用节点包装
    // ================================================================================================

    /**
     * 给节点统一加上“开始/完成/失败”事件与审计轨迹。
     *
     * <p>把横切关注点收在一个包装器里，节点实现只关心业务，不在每个节点里重复写事件代码。
     * 这也是 Middleware/Interceptor 思想在图编排中的体现。</p>
     */
    private AsyncNodeActionWithConfig tracked(String node, WorkflowEventRecorder recorder, NodeWork work) {
        return AsyncNodeActionWithConfig.node_async((state, config) -> {
            String runNo = WorkflowState.string(state, WorkflowState.RUN_NO);
            String label = nodeLabel(node);
            recorder.nodeStarted(runNo, node, label + "开始");
            try {
                Map<String, Object> updates = new LinkedHashMap<>(work.apply(state));
                updates.put(WorkflowState.AUDIT_TRAIL, List.of(label + "（" + node + "）完成"));
                recorder.nodeCompleted(runNo, node, label + "完成", eventPayload(updates));
                return updates;
            } catch (RuntimeException ex) {
                recorder.nodeFailed(runNo, node, label + "失败: " + ex.getMessage(), ex);
                throw ex;
            }
        });
    }

    private Map<String, Object> eventPayload(Map<String, Object> updates) {
        Map<String, Object> payload = new LinkedHashMap<>(updates);
        payload.remove(WorkflowState.CUSTOMER_PROFILE);
        payload.remove(WorkflowState.ONBOARDING_REQUEST);
        payload.remove(WorkflowState.AUDIT_TRAIL);
        return payload;
    }

    private String nodeLabel(String node) {
        return switch (node) {
            case LOAD_CUSTOMER -> "加载客户档案";
            case RISK_ASSESS -> "风险评估";
            case COMPLIANCE_SCREEN -> "合规筛查";
            case AGGREGATE -> "汇聚决策";
            case AUTO_APPROVE -> "自动审批";
            case MANUAL_REVIEW -> "人工复核";
            case FINALIZE_APPROVAL -> "批准收尾";
            case FINALIZE_REJECTION -> "拒绝收尾";
            default -> node;
        };
    }

    private String priorityOf(String riskLevel) {
        return switch (riskLevel == null ? "" : riskLevel) {
            case "CRITICAL", "HIGH" -> "HIGH";
            case "MEDIUM" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private OnboardingRequest onboardingRequest(OverAllState state) {
        OnboardingRequest request = state.value(WorkflowState.ONBOARDING_REQUEST, OnboardingRequest.class)
                .orElse(null);
        if (request == null) {
            throw new IllegalStateException("缺少开户请求状态: " + WorkflowState.ONBOARDING_REQUEST);
        }
        return request;
    }

    @FunctionalInterface
    private interface NodeWork {
        Map<String, Object> apply(OverAllState state);
    }
}
