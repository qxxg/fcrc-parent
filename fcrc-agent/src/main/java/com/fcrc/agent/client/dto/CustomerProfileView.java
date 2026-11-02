package com.fcrc.agent.client.dto;

import com.fcrc.common.domain.CustomerProfile;

/**
 * 内部客户画像视图：客户号 + 完整画像。
 *
 * <p>{@link CustomerProfile} 来自 {@code fcrc-common}，保证工作流、风险、合规、
 * AI 四个服务看到的是同一份契约，不会出现"字段名各写各的"。</p>
 */
public record CustomerProfileView(String customerNo, CustomerProfile profile) {
}
