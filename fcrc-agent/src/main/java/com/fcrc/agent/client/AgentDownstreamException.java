package com.fcrc.agent.client;

/**
 * 下游调用异常。
 *
 * <p>工具层捕获它并把可读错误返回给模型，让模型可以"解释失败原因"而不是直接 500。
 * 这也是 Agent 工程里的重要原则：工具错误是对话的一部分。</p>
 */
public class AgentDownstreamException extends RuntimeException {

    public AgentDownstreamException(String message) {
        super(message);
    }

    public AgentDownstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
