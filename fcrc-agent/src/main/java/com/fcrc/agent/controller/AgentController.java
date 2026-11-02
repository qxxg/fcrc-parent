package com.fcrc.agent.controller;

import com.fcrc.agent.dto.ChatReply;
import com.fcrc.agent.dto.ChatRequest;
import com.fcrc.agent.service.AgentChatService;
import com.fcrc.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI Copilot REST API。
 *
 * <p>路由约定：{@code /api/agent/**} 对前端开放。所有接口都需要 userId / sessionId，
 * 生产环境应由网关注入登录态，而不是信任前端传值。</p>
 *
 * <p><b>没有"审批"接口。</b> AI 只能查询、分析、建议、启动工作流；
 * 人工复核必须走工作流服务的 {@code /api/workflows/{runNo}/review}，
 * 由真人携带 reviewer 身份提交。这是本项目的合规红线。</p>
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentChatService chatService;

    public AgentController(AgentChatService chatService) {
        this.chatService = chatService;
    }

    /** 同步对话：等待完整回答。 */
    @PostMapping("/chat")
    public ApiResponse<ChatReply> chat(@Valid @RequestBody ChatRequest request) {
        ChatReply reply = chatService.chat(request.userId(), request.sessionId(), request.message());
        return ApiResponse.ok("对话完成", reply);
    }

    /** 流式对话：SSE，事件名 agent-event / done / error。 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request.userId(), request.sessionId(), request.message());
    }

    /** Agent 能力清单：模型、工作区、工具白名单、安全策略。 */
    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Object>> capabilities() {
        return ApiResponse.ok(chatService.capabilities());
    }

    /** 列出某个用户的会话（来自持久化状态库）。 */
    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(
            @RequestParam(defaultValue = "anonymous") String userId) {
        return ApiResponse.ok(chatService.listSessions(userId));
    }

    /** 删除会话及其持久化状态。 */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "anonymous") String userId) {
        chatService.deleteSession(userId, sessionId);
        return ApiResponse.ok();
    }

    /** 清空上下文但保留会话记录（用于"重新开始"）。 */
    @PostMapping("/sessions/{sessionId}/clear")
    public ApiResponse<Void> clearContext(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "anonymous") String userId) {
        chatService.clearContext(userId, sessionId);
        return ApiResponse.ok();
    }

    /** 查询 Plan Mode 状态。 */
    @GetMapping("/plan/status")
    public ApiResponse<Map<String, Object>> planStatus(
            @RequestParam(defaultValue = "anonymous") String userId,
            @RequestParam(defaultValue = "main") String sessionId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("sessionId", sessionId);
        body.put("planMode", chatService.planModeActive(userId, sessionId));
        return ApiResponse.ok(body);
    }

    /** 进入 Plan Mode：只读调研，不执行写操作。 */
    @PostMapping("/plan/enter")
    public ApiResponse<Map<String, Object>> enterPlan(
            @RequestParam(defaultValue = "anonymous") String userId,
            @RequestParam(defaultValue = "main") String sessionId) {
        chatService.enterPlanMode(userId, sessionId);
        return ApiResponse.ok(Map.of("planMode", true));
    }

    /** 退出 Plan Mode。 */
    @PostMapping("/plan/exit")
    public ApiResponse<Map<String, Object>> exitPlan(
            @RequestParam(defaultValue = "anonymous") String userId,
            @RequestParam(defaultValue = "main") String sessionId) {
        chatService.exitPlanMode(userId, sessionId);
        return ApiResponse.ok(Map.of("planMode", false));
    }

    /** 查询当前会话权限模式。 */
    @GetMapping("/permission-mode")
    public ApiResponse<Map<String, Object>> permissionMode(
            @RequestParam(defaultValue = "anonymous") String userId,
            @RequestParam(defaultValue = "main") String sessionId) {
        return ApiResponse.ok(Map.of("mode", chatService.permissionMode(userId, sessionId)));
    }

    /**
     * 切换权限模式。
     *
     * <p>生产环境不建议开放 BYPASS；这里保留接口是为了教学演示不同模式的差异。</p>
     */
    @PutMapping("/permission-mode")
    public ApiResponse<Map<String, Object>> setPermissionMode(
            @RequestParam(defaultValue = "anonymous") String userId,
            @RequestParam(defaultValue = "main") String sessionId,
            @RequestParam String mode) {
        return ApiResponse.ok(Map.of("mode", chatService.setPermissionMode(userId, sessionId, mode)));
    }

    /** 中断当前会话正在执行的 Agent 循环。 */
    @PostMapping("/interrupt")
    public ApiResponse<Void> interrupt(
            @RequestParam(defaultValue = "anonymous") String userId,
            @RequestParam(defaultValue = "main") String sessionId) {
        chatService.interrupt(userId, sessionId);
        return ApiResponse.ok();
    }

    /** 查看工作区文件（只读，便于教学观察 AGENTS.md / skills / knowledge 结构）。 */
    @GetMapping("/workspace")
    public ApiResponse<Map<String, Object>> workspace() {
        return ApiResponse.ok(chatService.listWorkspaceFiles());
    }

    /** 轻量健康检查：确认 Agent 与 Channel 已装配。 */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "timeoutSeconds", chatService.requestTimeout().toSeconds()));
    }
}
