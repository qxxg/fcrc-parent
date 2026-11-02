package com.fcrc.agent.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 演示模型的行为契约测试。
 *
 * <p>这些测试保证"无 API Key 也能演示"这条底线不被破坏：
 * 有工具时按关键词调用工具；工具返回后必须给出可读摘要；没有工具时返回帮助文本，
 * 绝不能死循环或抛异常。</p>
 */
class MockFcrcChatModelTest {

    private final MockFcrcChatModel model = new MockFcrcChatModel(new ObjectMapper());

    private final List<ToolSchema> tools = List.of(
            schema("list_customers"),
            schema("get_customer_profile"),
            schema("assess_customer_risk"),
            schema("screen_customer_compliance"),
            schema("search_fcrc_knowledge"));

    @Test
    void userQuestionTriggersMatchingTool() {
        Msg user = Msg.builder().role(MsgRole.USER).textContent("列出客户").build();

        ChatResponse response = model.stream(List.of(user), tools, null).blockFirst();

        assertThat(response).isNotNull();
        assertThat(response.getFinishReason()).isEqualTo("tool_calls");
        ToolUseBlock call = firstToolCall(response);
        assertThat(call.getName()).isEqualTo("list_customers");
    }

    @Test
    void customerIdIsExtractedIntoArguments() {
        Msg user = Msg.builder().role(MsgRole.USER).textContent("评估客户 42 的风险").build();

        ChatResponse response = model.stream(List.of(user), tools, null).blockFirst();

        ToolUseBlock call = firstToolCall(response);
        assertThat(call.getName()).isEqualTo("assess_customer_risk");
        assertThat(call.getInput()).containsEntry("customer_id", 42L);
    }

    @Test
    void toolResultIsSummarisedIntoText() {
        Msg toolResult = Msg.builder()
                .role(MsgRole.TOOL)
                .name("list_customers")
                .textContent("{\"items\":[{\"customerNo\":\"CUS001\"}],\"total\":1}")
                .build();

        ChatResponse response = model.stream(List.of(toolResult), tools, null).blockFirst();

        assertThat(response.getFinishReason()).isEqualTo("stop");
        String text = textOf(response);
        assertThat(text).contains("工具执行结果");
        assertThat(text).contains("CUS001");
        assertThat(text).contains("不会代替人工");
    }

    @Test
    void unknownQuestionReturnsHelpWithoutToolCall() {
        Msg user = Msg.builder().role(MsgRole.USER).textContent("你好").build();

        ChatResponse response = model.stream(List.of(user), tools, null).blockFirst();

        assertThat(response.getFinishReason()).isEqualTo("stop");
        assertThat(textOf(response)).contains("FCRC 合规风控 Copilot");
    }

    private ToolSchema schema(String name) {
        return ToolSchema.builder()
                .name(name)
                .description(name)
                .parameters(Map.of("type", "object"))
                .build();
    }

    private ToolUseBlock firstToolCall(ChatResponse response) {
        return response.getContent().stream()
                .filter(ToolUseBlock.class::isInstance)
                .map(ToolUseBlock.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private String textOf(ChatResponse response) {
        return response.getContent().stream()
                .filter(TextBlock.class::isInstance)
                .map(ContentBlock.class::cast)
                .map(block -> ((TextBlock) block).getText())
                .reduce("", String::concat);
    }
}
