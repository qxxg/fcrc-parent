package com.fcrc.agent.config;

import com.fcrc.agent.middleware.FcrcGuardrailMiddleware;
import com.fcrc.agent.model.MockFcrcChatModel;
import com.fcrc.agent.permission.FcrcPermissionRules;
import com.fcrc.agent.subagent.SubagentConfiguration;
import com.fcrc.agent.tool.FcrcBusinessTools;
import com.fcrc.agent.tool.FcrcKnowledgeTools;
import com.fcrc.agent.workspace.WorkspaceInitializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 核心装配。
 *
 * <p>本类是整个 AI 服务的"总装车间"，把零散能力组装成一个可运行的智能体：</p>
 * <pre>
 * Model（mock / qwen）
 *   + Toolkit（业务工具 + 知识库工具 + Harness 自带文件/记忆/技能工具）
 *   + PermissionContext（默认拒绝 + 白名单）
 *   + Middleware（合规护栏 + 审计）
 *   + AgentStateStore（JSON 文件，重启可恢复）
 *   + Workspace（AGENTS.md / knowledge / skills / MEMORY.md）
 *   + MemoryConfig（长期记忆）
 *   + CompactionConfig（上下文压缩）
 *   + Subagent（report-writer）
 *   + Plan Mode（只读调研）
 *   = HarnessAgent
 * </pre>
 *
 * <p>然后由 {@link GatewayBootstrap} 把 Agent 暴露成 {@link ChatUiChannel}，
 * Controller 通过 {@code SendOptions.of(userId, sessionId)} 实现多用户多会话隔离。</p>
 */
@Configuration
public class FcrcAgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FcrcAgentConfiguration.class);

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是 FCRC 合规风控平台的 AI Copilot，服务于银行/支付机构的合规、风控与运营人员。

            你的工作方式：
            1. 先理解用户意图，再决定是否调用工具；不要凭空回答业务数据问题。
            2. 工具返回的数据是唯一事实来源；引用时说明来自哪个工具。
            3. 给出结论时要解释依据，尤其是风险分、合规结果、是否转人工。
            4. 如果工具报错，如实说明并给出下一步建议，不要编造结果。
            5. 回答使用简体中文，结构清晰，避免空话。

            你必须在涉及审批、复核、放行、上报时明确说明：这些决定只能由真人完成。
            """;

    // ------------------------------------------------------------------
    // 基础设施
    // ------------------------------------------------------------------

    @Bean
    public AgentStateStore agentStateStore(AgentProperties properties) {
        Path dir = properties.getStateDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception ex) {
            throw new IllegalStateException("创建 Agent 状态目录失败：" + dir, ex);
        }
        log.info("Agent 会话状态目录：{}", dir);
        return new JsonFileAgentStateStore(dir);
    }

    /**
     * 模型选择：mock 零依赖演示；dashscope 使用真实 qwen。
     *
     * <p>生产环境推荐通过环境变量注入 Key，不要把 Key 写进配置文件。</p>
     */
    @Bean
    public Model fcrcChatModel(AgentProperties properties, ObjectMapper objectMapper) {
        if ("dashscope".equalsIgnoreCase(properties.getModel())) {
            if (properties.getDashscopeApiKey() == null || properties.getDashscopeApiKey().isBlank()) {
                throw new IllegalStateException(
                        "fcrc.agent.model=dashscope 时必须配置 DASHSCOPE_API_KEY");
            }
            log.info("使用 DashScope 真实模型：{}", properties.getDashscopeModel());
            return DashScopeChatModel.builder()
                    .apiKey(properties.getDashscopeApiKey())
                    .modelName(properties.getDashscopeModel())
                    .stream(true)
                    .build();
        }
        log.info("使用本地确定性演示模型（不调用外部 API）");
        return new MockFcrcChatModel(objectMapper);
    }

    @Bean
    public Toolkit fcrcToolkit(FcrcBusinessTools businessTools, FcrcKnowledgeTools knowledgeTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(businessTools);
        toolkit.registerTool(knowledgeTools);
        return toolkit;
    }

    // ------------------------------------------------------------------
    // Agent
    // ------------------------------------------------------------------

    @Bean
    public HarnessAgent fcrcHarnessAgent(
            AgentProperties properties,
            Model fcrcChatModel,
            Toolkit fcrcToolkit,
            AgentStateStore agentStateStore,
            WorkspaceInitializer workspaceInitializer,
            SubagentDeclaration reportWriterSubagent) {

        workspaceInitializer.initialize();
        Path workspace = workspaceInitializer.workspace();

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .agentId(properties.getAgentId())
                .name(properties.getName())
                .description("FCRC 合规风控智能助手")
                .sysPrompt(systemPrompt(properties))
                .model(fcrcChatModel)
                .toolkit(fcrcToolkit)
                .stateStore(agentStateStore)
                .workspace(workspace)
                .maxIters(properties.getMaxIters())
                .permissionContext(FcrcPermissionRules.build())
                .middleware(new FcrcGuardrailMiddleware())
                .subagent(reportWriterSubagent)
                .enablePlanMode(properties.isPlanModeEnabled())
                .skillsEnabled(properties.isSkillsEnabled())
                // 安全加固：即使权限规则被误改，也不注册 shell / web 工具
                .disableShellTool()
                .disableWebTools();

        if (properties.isCompactionEnabled()) {
            builder.compaction(CompactionConfig.builder()
                    .triggerMessages(properties.getCompactionTriggerMessages())
                    .keepMessages(12)
                    .build());
        } else {
            builder.disableCompaction();
        }

        if (properties.isMemoryEnabled()) {
            builder.memory(MemoryConfig.builder()
                    .model(fcrcChatModel)
                    .build());
        } else {
            builder.disableMemoryHooks();
        }

        if (properties.isSandboxEnabled()) {
            // 可选能力：把文件系统放进 Docker 沙箱，实现强隔离。
            // 默认关闭，因为演示环境通常没有 Docker；开启后仍需权限白名单。
            builder.filesystem(new DockerFilesystemSpec()
                    .image(properties.getSandboxImage()));
            log.info("Docker 沙箱已启用，镜像={}", properties.getSandboxImage());
        }

        HarnessAgent agent = builder.build();
        log.info("HarnessAgent 构建完成：id={} workspace={} planMode={} skills={}",
                properties.getAgentId(), workspace, properties.isPlanModeEnabled(), properties.isSkillsEnabled());
        return agent;
    }

    private String systemPrompt(AgentProperties properties) {
        String custom = properties.getSystemPrompt();
        return custom == null || custom.isBlank() ? DEFAULT_SYSTEM_PROMPT : custom;
    }

    // ------------------------------------------------------------------
    // Gateway / Channel
    // ------------------------------------------------------------------

    @Bean
    public GatewayBootstrap fcrcGatewayBootstrap(HarnessAgent fcrcHarnessAgent, AgentProperties properties) {
        GatewayBootstrap gateway = GatewayBootstrap.builder()
                .agent(properties.getAgentId(), fcrcHarnessAgent)
                .mainAgent(properties.getAgentId())
                .build();
        log.info("GatewayBootstrap 已就绪，主智能体={}", properties.getAgentId());
        return gateway;
    }

    @Bean
    public ChatUiChannel fcrcChatUiChannel(GatewayBootstrap gatewayBootstrap) {
        ChatUiChannel channel = gatewayBootstrap.chatUiChannel();
        channel.start();
        return channel;
    }
}
