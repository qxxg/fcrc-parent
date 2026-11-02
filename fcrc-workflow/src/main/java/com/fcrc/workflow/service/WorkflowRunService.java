package com.fcrc.workflow.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcrc.common.api.PageResult;
import com.fcrc.common.exception.BusinessException;
import com.fcrc.common.exception.ErrorCode;
import com.fcrc.common.util.BusinessIds;
import com.fcrc.workflow.domain.WorkflowEventEntity;
import com.fcrc.workflow.domain.WorkflowRunEntity;
import com.fcrc.workflow.dto.OnboardingRequest;
import com.fcrc.workflow.dto.ReviewRequest;
import com.fcrc.workflow.dto.WorkflowEventResponse;
import com.fcrc.workflow.dto.WorkflowRunDetail;
import com.fcrc.workflow.dto.WorkflowRunResponse;
import com.fcrc.workflow.dto.WorkflowStats;
import com.fcrc.workflow.event.WorkflowEventRecorder;
import com.fcrc.workflow.graph.WorkflowState;
import com.fcrc.workflow.repository.WorkflowEventRepository;
import com.fcrc.workflow.repository.WorkflowRunRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * 工作流运行服务。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>创建/加载运行记录，维护业务状态；</li>
 *   <li>调用编译后的 Graph，处理暂停与恢复；</li>
 *   <li>把 Graph 状态快照同步为可查询的运行视图；</li>
 *   <li>记录运行级事件，供 SSE 与审计页面使用。</li>
 * </ul>
 *
 * <p>图节点本身不直接改运行表——节点只写 Graph 状态，由本服务在每次调用结束后
 * 统一同步。这样“流程状态”只有一个权威来源，避免节点与数据库双写不一致。</p>
 *
 * <p><b>并发控制：</b>同一 runNo 的确认/恢复是串行操作。运行表带 {@code @Version}
 * 乐观锁；恢复前会检查状态，重复提交会被拒绝。生产环境还应增加分布式锁，
 * 防止两个实例同时恢复同一个 threadId。</p>
 */
@Service
public class WorkflowRunService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRunService.class);

    private final WorkflowRunRepository runRepository;
    private final WorkflowEventRepository eventRepository;
    private final WorkflowEventRecorder recorder;
    private final CompiledGraph graph;
    private final ObjectMapper objectMapper;

    public WorkflowRunService(WorkflowRunRepository runRepository,
                              WorkflowEventRepository eventRepository,
                              WorkflowEventRecorder recorder,
                              CompiledGraph onboardingGraph,
                              ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.recorder = recorder;
        this.graph = onboardingGraph;
        this.objectMapper = objectMapper;
    }

    /**
     * 发起一次开户编排。
     *
     * <p>方法刻意不加 {@code @Transactional}：图执行包含多次远程调用，可能持续数秒；
     * 把数据库事务跨在网络调用上会长时间占用连接，且并行节点运行在不同线程，
     * 事务上下文不会自动传播。每次仓储写入使用自己的短事务。</p>
     */
    public WorkflowRunDetail start(OnboardingRequest request) {
        String runNo = BusinessIds.next("WF");
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setRunNo(runNo);
        run.setThreadId(runNo);
        run.setCustomerId(request.customerId());
        run.setStatus("RUNNING");
        run.setCurrentNode("load_customer");
        // 保存后必须使用返回的受管实例：@Version 会在这里自增，继续使用旧实例会导致后续保存乐观锁失败。
        run = runRepository.save(run);

        recorder.record(runNo, "WORKFLOW_STARTED", "load_customer",
                "开户编排已创建，客户 ID=" + request.customerId(),
                Map.of("customerId", request.customerId(),
                        "transactionType", String.valueOf(request.transactionType()),
                        "crossBorder", request.crossBorder()));

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(WorkflowState.RUN_NO, runNo);
        inputs.put(WorkflowState.CUSTOMER_ID, request.customerId());
        inputs.put(WorkflowState.ONBOARDING_REQUEST, request);

        RunnableConfig config = RunnableConfig.builder().threadId(runNo).build();
        execute(run, config, inputs, false);
        return detail(runNo);
    }

    /**
     * 提交人工复核意见并恢复执行。
     */
    public WorkflowRunDetail review(String runNo, ReviewRequest request) {
        WorkflowRunEntity run = requireRun(runNo);
        if (!"WAITING_REVIEW".equals(run.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE,
                    "当前运行不是等待复核状态，实际为 " + run.getStatus());
        }

        run.setReviewDecision(request.decision());
        run.setReviewer(request.reviewer());
        run.setReviewComment(request.comment());
        run.setStatus("RUNNING");
        run.setCurrentNode("manual_review");
        run = runRepository.save(run);

        recorder.record(runNo, "HUMAN_REVIEW_SUBMITTED", "manual_review",
                "收到人工复核意见：" + request.decision() + "，复核人 " + request.reviewer(),
                Map.of("decision", request.decision(),
                        "reviewer", request.reviewer(),
                        "comment", request.comment() == null ? "" : request.comment()));

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(WorkflowState.REVIEW_DECISION, request.decision());
        inputs.put(WorkflowState.REVIEWER, request.reviewer());
        inputs.put(WorkflowState.REVIEW_COMMENT, request.comment());

        RunnableConfig config = RunnableConfig.builder()
                .threadId(runNo)
                // 关键：告诉执行器“这是恢复请求”，才会从最新检查点继续，而不是从 START 重跑。
                .resume()
                .build();

        execute(run, config, inputs, true);
        return detail(runNo);
    }

    /**
     * 执行/恢复图，并把结果同步到运行表。
     *
     * @param resuming 是否恢复执行；恢复失败时保留已提交的复核信息，便于审计
     */
    private void execute(WorkflowRunEntity run, RunnableConfig config, Map<String, Object> inputs,
                         boolean resuming) {
        String runNo = run.getRunNo();
        try {
            // 重新加载，确保拿到最新 @Version；本方法会在图中断/恢复后再次保存，使用旧快照会触发乐观锁。
            run = requireRun(runNo);
            Optional<OverAllState> result = graph.invoke(inputs, config);
            Optional<StateSnapshot> snapshot = graph.stateOf(config);
            String nextNode = snapshot.map(StateSnapshot::next).orElse(null);
            OverAllState finalState = result.orElseGet(() -> snapshot.map(StateSnapshot::state).orElse(null));

            if (finalState == null) {
                throw new IllegalStateException("图执行结束但未返回状态");
            }

            if (isWaitingReview(nextNode)) {
                sync(run, finalState.data(), "manual_review");
                run.setStatus("WAITING_REVIEW");
                run.setOnboardingStatus("MANUAL_REVIEW");
                run = runRepository.save(run);
                recorder.record(runNo, "HUMAN_REVIEW_REQUIRED", "manual_review",
                        "流程已暂停，等待人工复核；案件号=" + run.getCaseNo(),
                        Map.of("caseNo", String.valueOf(run.getCaseNo()),
                                "riskScore", String.valueOf(run.getRiskScore()),
                                "riskLevel", String.valueOf(run.getRiskLevel()),
                                "complianceResult", String.valueOf(run.getComplianceResult())));
                return;
            }

            sync(run, finalState.data(), null);
            String decision = run.getDecision();
            run.setStatus("REJECTED".equals(decision) ? "REJECTED" : "COMPLETED");
            run.setCurrentNode("REJECTED".equals(decision) ? "finalize_rejection" : "finalize_approval");
            run = runRepository.save(run);

            recorder.record(runNo, "WORKFLOW_COMPLETED", run.getCurrentNode(),
                    "流程结束，结论=" + decision + "，报告号=" + run.getReportNo(),
                    Map.of("decision", String.valueOf(decision),
                            "reportNo", String.valueOf(run.getReportNo()),
                            "onboardingStatus", String.valueOf(run.getOnboardingStatus())));
        } catch (Exception ex) {
            String message = rootMessage(ex);
            log.error("Workflow run {} failed", runNo, ex);
            run.setStatus("FAILED");
            run.setErrorMessage(truncate(message, 1000));
            run = runRepository.save(run);
            recorder.record(runNo, "WORKFLOW_FAILED", run.getCurrentNode(),
                    "流程执行失败: " + message,
                    Map.of("error", ex.getClass().getSimpleName(),
                            "resuming", resuming));
        }
    }

    private boolean isWaitingReview(String nextNode) {
        return "manual_review".equals(nextNode);
    }

    /**
     * 把 Graph 状态复制到运行表。
     *
     * <p>只同步“展示与查询需要”的字段；完整状态仍在检查点表中。这样列表查询
     * 不必反序列化大对象，也避免业务表跟着图结构频繁变更。</p>
     */
    private void sync(WorkflowRunEntity run, Map<String, Object> data, String currentNode) {
        run.setCustomerNo(WorkflowState.string(data, WorkflowState.CUSTOMER_NO));
        run.setCustomerName(WorkflowState.string(data, WorkflowState.CUSTOMER_NAME));
        run.setRiskAssessmentNo(WorkflowState.string(data, WorkflowState.RISK_ASSESSMENT_NO));
        run.setRiskScore(intValue(data.get(WorkflowState.RISK_SCORE)));
        run.setRiskLevel(WorkflowState.string(data, WorkflowState.RISK_LEVEL));
        run.setRiskFactorsJson(json(WorkflowState.stringList(data, WorkflowState.RISK_FACTORS)));
        run.setComplianceScreeningNo(WorkflowState.string(data, WorkflowState.COMPLIANCE_SCREENING_NO));
        run.setComplianceResult(WorkflowState.string(data, WorkflowState.COMPLIANCE_RESULT));
        run.setComplianceFindingsJson(json(WorkflowState.stringList(data, WorkflowState.COMPLIANCE_FINDINGS)));
        run.setPreliminaryDecision(WorkflowState.string(data, WorkflowState.PRELIMINARY_DECISION));
        run.setDecision(WorkflowState.string(data, WorkflowState.DECISION));
        run.setDecisionReason(WorkflowState.string(data, WorkflowState.DECISION_REASON));
        run.setReviewDecision(WorkflowState.string(data, WorkflowState.REVIEW_DECISION));
        run.setReviewer(WorkflowState.string(data, WorkflowState.REVIEWER));
        run.setReviewComment(WorkflowState.string(data, WorkflowState.REVIEW_COMMENT));
        run.setCaseNo(WorkflowState.string(data, WorkflowState.CASE_NO));
        run.setReportNo(WorkflowState.string(data, WorkflowState.REPORT_NO));
        run.setOnboardingStatus(WorkflowState.string(data, WorkflowState.ONBOARDING_STATUS));
        run.setAuditTrailJson(json(WorkflowState.stringList(data, WorkflowState.AUDIT_TRAIL)));
        if (currentNode != null) {
            run.setCurrentNode(currentNode);
        }
    }

    public PageResult<WorkflowRunResponse> search(String keyword, String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Specification<WorkflowRunEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("runNo")), like),
                        cb.like(cb.lower(root.get("customerNo")), like),
                        cb.like(cb.lower(root.get("customerName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<WorkflowRunEntity> result = runRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.of(result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    public WorkflowRunDetail detail(String runNo) {
        WorkflowRunEntity run = requireRun(runNo);
        List<WorkflowEventResponse> events = eventRepository.findByRunNoOrderByIdAsc(runNo).stream()
                .map(this::toEvent)
                .toList();
        return new WorkflowRunDetail(toResponse(run), events);
    }

    public List<WorkflowEventResponse> events(String runNo) {
        requireRun(runNo);
        return eventRepository.findByRunNoOrderByIdAsc(runNo).stream().map(this::toEvent).toList();
    }

    public WorkflowStats stats() {
        return new WorkflowStats(
                runRepository.count(),
                runRepository.countByStatus("RUNNING"),
                runRepository.countByStatus("WAITING_REVIEW"),
                runRepository.countByStatus("COMPLETED"),
                runRepository.countByStatus("REJECTED"),
                runRepository.countByStatus("FAILED"));
    }

    public WorkflowRunResponse get(String runNo) {
        return toResponse(requireRun(runNo));
    }

    public String graphDiagram() {
        return graph.getGraph(com.alibaba.cloud.ai.graph.GraphRepresentation.Type.MERMAID,
                "FCRC 开户审批流程", true).content();
    }

    private WorkflowRunEntity requireRun(String runNo) {
        return runRepository.findByRunNo(runNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工作流不存在: " + runNo));
    }

    private WorkflowRunResponse toResponse(WorkflowRunEntity run) {
        return new WorkflowRunResponse(
                run.getRunNo(),
                run.getCustomerId(),
                run.getCustomerNo(),
                run.getCustomerName(),
                run.getStatus(),
                run.getCurrentNode(),
                run.getRiskAssessmentNo(),
                run.getRiskScore(),
                run.getRiskLevel(),
                readList(run.getRiskFactorsJson()),
                run.getComplianceScreeningNo(),
                run.getComplianceResult(),
                readList(run.getComplianceFindingsJson()),
                run.getPreliminaryDecision(),
                run.getDecision(),
                run.getDecisionReason(),
                run.getReviewDecision(),
                run.getReviewer(),
                run.getReviewComment(),
                run.getCaseNo(),
                run.getReportNo(),
                run.getOnboardingStatus(),
                readList(run.getAuditTrailJson()),
                "WAITING_REVIEW".equals(run.getStatus()),
                run.getErrorMessage(),
                run.getCreatedAt(),
                run.getUpdatedAt());
    }

    private WorkflowEventResponse toEvent(WorkflowEventEntity event) {
        return new WorkflowEventResponse(event.getId(), event.getType(), event.getNode(),
                event.getMessage(), event.getPayload(), event.getCreatedAt());
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse JSON list: {}", json, ex);
            return List.of();
        }
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
