package com.fcrc.workflow.client;

import com.fcrc.common.api.ApiResponse;
import com.fcrc.common.domain.CustomerProfile;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * 客户域客户端。
 *
 * <p>只调用 {@code /internal/**} 接口，因为客户档案包含姓名、证件号等 PII。
 * 编排服务不直接查客户库，而是通过内部接口获取“已脱敏/已授权”的视图，
 * 这样客户域日后拆分数据库、加密存储或接入统一隐私中心时，编排服务无须改动。</p>
 */
@Component
public class CustomerClient {

    private final DownstreamRestClient http;
    private final WorkflowClientProperties properties;

    public CustomerClient(DownstreamRestClient http, WorkflowClientProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    /** 读取内部客户档案。 */
    public InternalProfile profile(Long customerId) {
        return http.get(properties.getCustomer(), "/internal/customers/" + customerId + "/profile",
                new ParameterizedTypeReference<ApiResponse<InternalProfile>>() {
                });
    }

    /** 回写开户流程状态。该接口是幂等的 PUT，允许安全重试。 */
    public void updateOnboarding(Long customerId, String onboardingStatus, String riskLevel, String notes) {
        http.put(properties.getCustomer(), "/internal/customers/" + customerId + "/onboarding",
                new OnboardingUpdate(onboardingStatus, riskLevel, notes),
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                });
    }

    /** 客户内部档案响应；profile 是跨服务共享的契约。 */
    public record InternalProfile(String customerNo, CustomerProfile profile) {
    }

    /** 开户状态回写请求。 */
    public record OnboardingUpdate(String onboardingStatus, String riskLevel, String notes) {
    }
}
