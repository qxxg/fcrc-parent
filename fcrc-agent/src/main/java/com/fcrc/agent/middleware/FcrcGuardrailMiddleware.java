package com.fcrc.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 合规护栏中间件。
 *
 * <p><b>教学点：Middleware 是 Agent 的"洋葱模型"。</b> 它可以在不修改 Agent 核心代码的前提下，
 * 拦截整个调用、推理、工具执行和模型调用四个阶段。本中间件做三件事：</p>
 * <ol>
 *   <li><b>系统提示词加固：</b> 每次调用都追加合规红线，防止模型"越权承诺"；</li>
 *   <li><b>行为审计：</b> 记录用户/会话/工具调用，满足可追溯要求；</li>
 *   <li><b>可观测性：</b> 记录事件数量与耗时，便于定位慢工具。</li>
 * </ol>
 *
 * <p>注意：日志里只打印会话标识和工具名，不打印客户 PII。这是"最小必要"原则。</p>
 */
public class FcrcGuardrailMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(FcrcGuardrailMiddleware.class);

    private static final String GUARDRAIL = """

            ## 不可违反的合规红线
            1. 你只能提供信息、分析与建议，不能代替人工做出审批、复核、放行、上报等决定。
            2. 工作流进入 MANUAL_REVIEW 时，必须明确告知用户"需要真人复核"，不得声称已自动通过。
            3. 不得编造客户、风险分、合规结果、案件或报告；所有结论必须来自工具返回的数据。
            4. 涉及制度解释时，优先调用 search_fcrc_knowledge，并注明引用文件。
            5. 不要输出完整证件号、手机号等敏感信息；引用时使用脱敏形式。
            """;

    @Override
    public int order() {
        return 100;
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        String base = currentPrompt == null ? "" : currentPrompt;
        return Mono.just(base + GUARDRAIL);
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        String userId = ctx == null ? "-" : ctx.getUserId();
        String sessionId = ctx == null ? "-" : ctx.getSessionId();
        long start = System.currentTimeMillis();
        AtomicInteger events = new AtomicInteger();
        log.info("Agent 调用开始 user={} session={} messages={}",
                userId, sessionId, input.msgs() == null ? 0 : input.msgs().size());
        return next.apply(input)
                .doOnEach(signal -> {
                    if (signal.isOnNext()) {
                        events.incrementAndGet();
                    }
                })
                .doOnComplete(() -> log.info("Agent 调用完成 user={} session={} events={} cost={}ms",
                        userId, sessionId, events.get(), System.currentTimeMillis() - start))
                .doOnError(ex -> log.warn("Agent 调用失败 user={} session={} cost={}ms reason={}",
                        userId, sessionId, System.currentTimeMillis() - start, ex.getMessage()));
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        if (input.toolCalls() != null) {
            input.toolCalls().forEach(call ->
                    log.info("工具调用 user={} session={} tool={}",
                            ctx == null ? "-" : ctx.getUserId(),
                            ctx == null ? "-" : ctx.getSessionId(),
                            call.getName()));
        }
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        return next.apply(input);
    }
}
