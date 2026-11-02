package com.fcrc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcrc.agent.dto.ChatReply;
import com.fcrc.agent.service.AgentChatService;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 端到端装配测试。
 *
 * <p>验证在没有外部 API、没有下游服务的情况下，Agent 仍然可以启动并完成一轮
 * 不依赖工具的对话。这保证评审者 clone 下来就能跑通演示。</p>
 */
@SpringBootTest(properties = {
        "fcrc.agent.workspace=./target/test-agent-workspace",
        "fcrc.agent.state-dir=./target/test-agent-state",
        "fcrc.agent.model=mock",
        "fcrc.agent.memory-enabled=false",
        "fcrc.agent.compaction-enabled=false",
        "spring.cloud.nacos.discovery.enabled=false"
})
class AgentApplicationTest {

    @Autowired
    private HarnessAgent harnessAgent;

    @Autowired
    private ChatUiChannel chatUiChannel;

    @Autowired
    private AgentChatService chatService;

    @Autowired
    private AgentStateStore stateStore;

    @Test
    void contextAssemblesAgentAndChannel() {
        assertThat(harnessAgent).isNotNull();
        assertThat(chatUiChannel).isNotNull();
        assertThat(stateStore).isNotNull();
        assertThat(harnessAgent.getName()).isEqualTo("FCRC 合规风控 Copilot");
    }

    @Test
    void capabilitiesExposeGuardrails() {
        Map<String, Object> capabilities = chatService.capabilities();
        assertThat(capabilities.get("agentId")).isEqualTo("fcrc-copilot");
        assertThat(capabilities.get("modelMode")).isEqualTo("mock");
        assertThat((String) capabilities.get("humanInTheLoop")).contains("/review");
        assertThat((Integer) capabilities.get("registeredToolCount")).isGreaterThan(0);
    }

    @Test
    void chatWithoutToolCallReturnsHelp() {
        ChatReply reply = chatService.chat("tester", "smoke", "你好");

        assertThat(reply.sessionId()).isEqualTo("smoke");
        assertThat(reply.reply()).contains("FCRC 合规风控 Copilot");
    }

    @Test
    void sessionIsStableAcrossTurns() {
        chatService.chat("stable-user", "s1", "你好");
        chatService.chat("stable-user", "s1", "你好");

        assertThat(stateStore.listSessionIds("stable-user")).hasSize(1);
    }
}
