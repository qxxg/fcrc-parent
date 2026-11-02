package com.fcrc.agent.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 助手配置（前缀 {@code fcrc.agent}）。
 *
 * <p>所有可调参数集中在这里，避免把"魔法值"散落到代码里。教学项目尤其强调：
 * 模型、工作区、记忆、压缩、Plan Mode、沙箱这些能力的开关都应该是显式配置。</p>
 */
@ConfigurationProperties(prefix = "fcrc.agent")
public class AgentProperties {

    /** 智能体唯一 ID，会写入会话状态与审计日志。 */
    private String agentId = "fcrc-copilot";

    /** 展示名称。 */
    private String name = "FCRC 合规风控 Copilot";

    /** 工作区目录：AGENTS.md、skills、knowledge、MEMORY.md 都在这里。 */
    private Path workspace = Path.of("./data/agent-workspace");

    /** 会话状态持久化目录；重启后仍可恢复上下文。 */
    private Path stateDir = Path.of("./data/agent-state");

    /** 模型模式：{@code mock} 使用确定性本地模型，{@code dashscope} 使用真实 qwen。 */
    private String model = "mock";

    /** DashScope API Key；仅 model=dashscope 时必填。 */
    private String dashscopeApiKey;

    /** DashScope 模型名，例如 qwen-plus、qwen-max。 */
    private String dashscopeModel = "qwen-plus";

    /** 单次对话最大推理轮数，防止模型死循环调用工具。 */
    private int maxIters = 8;

    /** 是否启用长期记忆（flush + consolidation）。 */
    private boolean memoryEnabled = true;

    /** 是否启用上下文压缩，避免长会话把上下文窗口撑爆。 */
    private boolean compactionEnabled = true;

    /** 触发压缩的消息条数阈值。 */
    private int compactionTriggerMessages = 40;

    /** 是否启用 Plan Mode（只读调研 + 计划文件）。 */
    private boolean planModeEnabled = true;

    /** 是否启用工作区技能（skills 目录下的 SKILL.md）。 */
    private boolean skillsEnabled = true;

    /** 是否启用 Docker 沙箱文件系统；默认关闭，因为本地通常没有 Docker。 */
    private boolean sandboxEnabled = false;

    /** 沙箱镜像。 */
    private String sandboxImage = "eclipse-temurin:21-jdk";

    /** 单次对话超时时间。 */
    private Duration requestTimeout = Duration.ofSeconds(180);

    /** 允许前端看到的工具白名单说明，用于 /api/agent/capabilities。 */
    private String systemPrompt;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Path getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Path workspace) {
        this.workspace = workspace;
    }

    public Path getStateDir() {
        return stateDir;
    }

    public void setStateDir(Path stateDir) {
        this.stateDir = stateDir;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getDashscopeApiKey() {
        return dashscopeApiKey;
    }

    public void setDashscopeApiKey(String dashscopeApiKey) {
        this.dashscopeApiKey = dashscopeApiKey;
    }

    public String getDashscopeModel() {
        return dashscopeModel;
    }

    public void setDashscopeModel(String dashscopeModel) {
        this.dashscopeModel = dashscopeModel;
    }

    public int getMaxIters() {
        return maxIters;
    }

    public void setMaxIters(int maxIters) {
        this.maxIters = maxIters;
    }

    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    public void setMemoryEnabled(boolean memoryEnabled) {
        this.memoryEnabled = memoryEnabled;
    }

    public boolean isCompactionEnabled() {
        return compactionEnabled;
    }

    public void setCompactionEnabled(boolean compactionEnabled) {
        this.compactionEnabled = compactionEnabled;
    }

    public int getCompactionTriggerMessages() {
        return compactionTriggerMessages;
    }

    public void setCompactionTriggerMessages(int compactionTriggerMessages) {
        this.compactionTriggerMessages = compactionTriggerMessages;
    }

    public boolean isPlanModeEnabled() {
        return planModeEnabled;
    }

    public void setPlanModeEnabled(boolean planModeEnabled) {
        this.planModeEnabled = planModeEnabled;
    }

    public boolean isSkillsEnabled() {
        return skillsEnabled;
    }

    public void setSkillsEnabled(boolean skillsEnabled) {
        this.skillsEnabled = skillsEnabled;
    }

    public boolean isSandboxEnabled() {
        return sandboxEnabled;
    }

    public void setSandboxEnabled(boolean sandboxEnabled) {
        this.sandboxEnabled = sandboxEnabled;
    }

    public String getSandboxImage() {
        return sandboxImage;
    }

    public void setSandboxImage(String sandboxImage) {
        this.sandboxImage = sandboxImage;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
