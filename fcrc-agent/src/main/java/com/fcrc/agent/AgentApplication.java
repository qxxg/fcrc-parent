package com.fcrc.agent;

import com.fcrc.agent.config.AgentClientProperties;
import com.fcrc.agent.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * FCRC AgentScope AI Copilot 服务入口。
 *
 * <p>本服务是"AI 助手层"，不直接访问数据库，而是通过 HTTP 调用
 * 客户/风险/合规/工作流/案件/报告六个业务微服务。这样做的原因：</p>
 * <ol>
 *   <li>权限边界清晰：AI 只能看到业务服务允许它看到的接口；</li>
 *   <li>审计简单：所有 AI 行为最终都落到业务服务的操作日志上；</li>
 *   <li>可替换模型：换模型不需要改任何业务代码。</li>
 * </ol>
 *
 * <p>教学点：AgentScope 的 {@code HarnessAgent} 负责模型推理、工具调用、
 * 会话状态、记忆、工作区、技能、子智能体等能力；本类只负责启动 Spring 容器。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties({AgentProperties.class, AgentClientProperties.class})
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
