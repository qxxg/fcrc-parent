package com.fcrc.customer.controller;

import com.fcrc.common.api.ApiResponse;
import com.fcrc.customer.dto.CustomerResponse;
import com.fcrc.customer.dto.InternalCustomerProfile;
import com.fcrc.customer.dto.UpdateOnboardingRequest;
import com.fcrc.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部服务接口。
 *
 * <p>生产部署中应通过网关屏蔽 /internal/**，并增加服务间认证。
 * 单独拆 Controller 是为了让安全边界一眼可见，而不是把敏感能力混在公开 API 中。</p>
 */
@RestController
@RequestMapping("/internal/customers")
public class InternalCustomerController {

    private final CustomerService service;

    public InternalCustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/{id}/profile")
    public ApiResponse<InternalCustomerProfile> profile(@PathVariable Long id) {
        return ApiResponse.ok(service.getInternalProfile(id));
    }

    @PutMapping("/{id}/onboarding")
    public ApiResponse<CustomerResponse> updateOnboarding(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateOnboardingRequest request) {
        return ApiResponse.ok("开户流程状态已回写", service.updateOnboarding(id, request));
    }
}
