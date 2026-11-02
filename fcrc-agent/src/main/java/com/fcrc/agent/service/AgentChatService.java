package com.fcrc.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcrc.agent.config.AgentProperties;
import com.fcrc.agent.dto.ChatReply;
import com.fcrc.agent.permission.FcrcPermissionRules;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * AI 对话服务：把 AgentScope 的 Channel 包装成 Spring MVC 可直接使用的 API。
 *
 * <p><b>教学点 1：Channel 是"消息入口"。</b> {@link ChatUiChannel} 负责把一条用户消息
 * 路由到指定 Agent，并返回 {@link Msg} 或事件流。Controller 不需要知道 ReAct 循环细节。</p>
 *
 * <p><b>教学点 2：会话隔离靠 (userId, sessionId)。</b> 通过 {@link SendOptions#of} 传入，
 * 底层 HarnessAgent 会为每个组合维护独立的 AgentState，并持久化到
 * {@link AgentStateStore}。</p>
 *
 * <p><b>教学点 3：同步 vs 流式。</b> 同步适合脚本/测试；流式适合前端实时展示
 * "模型思考 → 调工具 → 工具结果 → 最终回答"的全过程。</p>
 */
@Service
public class AgentChatService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);
    private static final String DEFAULT_SESSION = "main";

    private final ChatUiChannel channel;
    private final HarnessAgent agent;
    private final AgentStateStore stateStore;
    private final Toolkit toolkit;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public AgentChatService(
            ChatUiChannel channel,
            HarnessAgent agent,
            AgentStateStore stateStore,
            Toolkit toolkit,
            AgentProperties properties,
            ObjectMapper objectMapper) {
        this.channel = channel;
        this.agent = agent;
        this.stateStore = stateStore;
        this.toolkit = toolkit;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步对话：等待 Agent 完整跑完 ReAct 循环后返回最终文本。
     */
    public ChatReply chat(String userId, String sessionId, String message) {
        String user = normalizeUser(userId);
        String session = normalizeSession(sessionId);
        long start = System.currentTimeMillis();
        Msg reply = channel.send(SendOptions.of(user, session), message)
                .block(properties.getRequestTimeout());
        String text = reply == null ? "" : reply.getTextContent();
        log.info("同步对话完成 user={} session={} cost={}ms",
                user, session, System.currentTimeMillis() - start);
        return new ChatReply(user, session, text, properties.getModel());
    }

    /**
     * 流式对话：把 AgentEvent 逐条写入 SSE，前端可以实时渲染。
     *
     * <p>事件类型包括 AGENT_START / MODEL_CALL_* / TOOL_CALL_* / TOOL_RESULT_* / AGENT_END 等，
     * 前端按 {@code type} 字段分类展示即可。</p>
     */
    public SseEmitter stream(String userId, String sessionId, String message) {
        String user = normalizeUser(userId);
        String session = normalizeSession(sessionId);
        long timeoutMs = properties.getRequestTimeout().toMillis();
        SseEmitter emitter = new SseEmitter(timeoutMs);

        Flux<AgentEvent> flux = channel.sendStream(SendOptions.of(user, session), message)
                .timeout(properties.getRequestTimeout());

        Disposable disposable = flux.subscribe(
                event -> sendEvent(emitter, event),
                error -> {
                    log.warn("流式对话失败 user={} session={}", user, session, error);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(Map.of("message", safeMessage(error))));
                    } catch (IOException ignored) {
                        // 客户端可能已经断开
                    }
                    emitter.completeWithError(error);
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data(Map.of(
                                "userId", user,
                                "sessionId", session)));
                    } catch (IOException ignored) {
                        // 客户端断开
                    }
                    emitter.complete();
                });

        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(() -> {
            disposable.dispose();
            emitter.complete();
        });
        emitter.onError(ex -> disposable.dispose());
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, AgentEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().name("agent-event").data(json));
        } catch (Exception ex) {
            log.debug("发送 SSE 事件失败：{}", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 会话管理
    // ------------------------------------------------------------------

    public List<Map<String, Object>> listSessions(String userId) {
        String user = normalizeUser(userId);
        Set<String> ids = stateStore.listSessionIds(user);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", id);
            item.put("planMode", agent.isPlanModeActive(user, id));
            item.put("permissionMode", agent.getPermissionMode(user, id).getValue());
            result.add(item);
        }
        return result;
    }

    public void deleteSession(String userId, String sessionId) {
        String user = normalizeUser(userId);
        String session = normalizeSession(sessionId);
        stateStore.delete(user, session);
        agent.clearStateCache(user, session);
        log.info("已删除会话 user={} session={}", user, session);
    }

    public void clearContext(String userId, String sessionId) {
        agent.clearContext(normalizeUser(userId), normalizeSession(sessionId));
    }

    // ------------------------------------------------------------------
    // Plan Mode / 权限模式
    // ------------------------------------------------------------------

    public boolean enterPlanMode(String userId, String sessionId) {
        agent.enterPlanMode(normalizeUser(userId), normalizeSession(sessionId));
        return true;
    }

    public boolean exitPlanMode(String userId, String sessionId) {
        agent.exitPlanMode(normalizeUser(userId), normalizeSession(sessionId));
        return false;
    }

    public boolean planModeActive(String userId, String sessionId) {
        return agent.isPlanModeActive(normalizeUser(userId), normalizeSession(sessionId));
    }

    public String permissionMode(String userId, String sessionId) {
        return agent.getPermissionMode(normalizeUser(userId), normalizeSession(sessionId)).getValue();
    }

    public String setPermissionMode(String userId, String sessionId, String mode) {
        PermissionMode parsed = PermissionMode.fromString(mode);
        agent.setPermissionMode(normalizeUser(userId), normalizeSession(sessionId), parsed);
        return parsed.getValue();
    }

    public void interrupt(String userId, String sessionId) {
        agent.interrupt(normalizeUser(userId), normalizeSession(sessionId));
    }

    // ------------------------------------------------------------------
    // 能力信息
    // ------------------------------------------------------------------

    public Map<String, Object> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentId", properties.getAgentId());
        result.put("name", properties.getName());
        result.put("modelMode", properties.getModel());
        result.put("model", "dashscope".equalsIgnoreCase(properties.getModel())
                ? properties.getDashscopeModel() : "fcrc-mock-model");
        result.put("workspace", properties.getWorkspace().toAbsolutePath().normalize().toString());
        result.put("planModeEnabled", properties.isPlanModeEnabled());
        result.put("memoryEnabled", properties.isMemoryEnabled());
        result.put("compactionEnabled", properties.isCompactionEnabled());
        result.put("skillsEnabled", properties.isSkillsEnabled());
        result.put("sandboxEnabled", properties.isSandboxEnabled());
        result.put("maxIters", properties.getMaxIters());
        result.put("businessTools", FcrcPermissionRules.ALLOWED_TOOLS.stream()
                .filter(name -> toolkit.getToolNames().contains(name))
                .toList());
        result.put("deniedTools", FcrcPermissionRules.DENIED_TOOLS);
        result.put("registeredToolCount", toolkit.getToolNames().size());
        result.put("humanInTheLoop", "工作流进入 MANUAL_REVIEW 后必须由真人调用 /api/workflows/{runNo}/review，AI 无此工具");
        return result;
    }

    public Map<String, Object> listWorkspaceFiles() {
        Path root = properties.getWorkspace().toAbsolutePath().normalize();
        List<String> files = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> stream = Files.walk(root, 3)) {
                stream.filter(Files::isRegularFile)
                        .limit(200)
                        .forEach(path -> files.add(root.relativize(path).toString().replace('\\', '/')));
            } catch (IOException ex) {
                log.warn("列出工作区文件失败", ex);
            }
        }
        return Map.of("workspace", root.toString(), "files", files);
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? "anonymous" : userId.trim();
    }

    private String normalizeSession(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? DEFAULT_SESSION : sessionId.trim();
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    /** 供健康检查使用。 */
    public Duration requestTimeout() {
        return properties.getRequestTimeout();
    }
}
