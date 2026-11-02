package com.fcrc.workflow.controller;

import com.fcrc.common.api.ApiResponse;
import com.fcrc.common.api.PageResult;
import com.fcrc.workflow.dto.OnboardingRequest;
import com.fcrc.workflow.dto.ReviewRequest;
import com.fcrc.workflow.dto.WorkflowEventResponse;
import com.fcrc.workflow.dto.WorkflowRunDetail;
import com.fcrc.workflow.dto.WorkflowRunResponse;
import com.fcrc.workflow.dto.WorkflowStats;
import com.fcrc.workflow.service.WorkflowRunService;
import com.fcrc.workflow.service.WorkflowStreamService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 开户编排 REST API。
 *
 * <p>API 设计遵循“一次请求对应一次业务动作”：发起、查询、复核、订阅事件相互独立。
 * 复核接口使用 POST 而不是 PUT，因为它推进流程状态并产生新事件，不是幂等覆盖。</p>
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowRunService service;
    private final WorkflowStreamService streamService;

    public WorkflowController(WorkflowRunService service, WorkflowStreamService streamService) {
        this.service = service;
        this.streamService = streamService;
    }

    /** 发起开户审批。返回运行详情（含事件时间线）。 */
    @PostMapping
    public ApiResponse<WorkflowRunDetail> start(@Valid @RequestBody OnboardingRequest request) {
        WorkflowRunDetail detail = service.start(request);
        String message = switch (detail.run().status()) {
            case "WAITING_REVIEW" -> "流程已进入人工复核";
            case "FAILED" -> "流程执行失败，请查看事件时间线";
            default -> "流程已完成";
        };
        return ApiResponse.ok(message, detail);
    }

    /** 分页查询运行。 */
    @GetMapping
    public ApiResponse<PageResult<WorkflowRunResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(keyword, status, page, size));
    }

    /** 看板统计。 */
    @GetMapping("/stats")
    public ApiResponse<WorkflowStats> stats() {
        return ApiResponse.ok(service.stats());
    }

    /** 返回 Graph 自动生成的 Mermaid 图，前端可直接渲染。 */
    @GetMapping("/graph")
    public ApiResponse<Map<String, String>> graph() {
        return ApiResponse.ok(Map.of("mermaid", service.graphDiagram()));
    }

    /** 运行详情：业务结果 + 完整事件时间线。 */
    @GetMapping("/{runNo}")
    public ApiResponse<WorkflowRunDetail> detail(@PathVariable String runNo) {
        return ApiResponse.ok(service.detail(runNo));
    }

    /** 提交人工复核意见，恢复图执行。 */
    @PostMapping("/{runNo}/review")
    public ApiResponse<WorkflowRunDetail> review(@PathVariable String runNo,
                                                 @Valid @RequestBody ReviewRequest request) {
        WorkflowRunDetail detail = service.review(runNo, request);
        String message = "FAILED".equals(detail.run().status())
                ? "复核已提交，但后续节点执行失败"
                : "复核已完成，流程结束";
        return ApiResponse.ok(message, detail);
    }

    /** 查询事件时间线。 */
    @GetMapping("/{runNo}/events")
    public ApiResponse<List<WorkflowEventResponse>> events(@PathVariable String runNo) {
        return ApiResponse.ok(service.events(runNo));
    }

    /**
     * 订阅事件流。前端使用 {@code EventSource} 消费；
     * 事件名称为 {@code workflow-event}，结束时发送 {@code done}。
     */
    @GetMapping(value = "/{runNo}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runNo) {
        return streamService.stream(runNo);
    }
}
