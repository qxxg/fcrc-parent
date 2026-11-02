package com.fcrc.customer.controller;

import com.fcrc.common.api.ApiResponse;
import com.fcrc.common.api.PageResult;
import com.fcrc.customer.dto.CreateCustomerRequest;
import com.fcrc.customer.dto.CustomerResponse;
import com.fcrc.customer.dto.CustomerStats;
import com.fcrc.customer.dto.InternalCustomerProfile;
import com.fcrc.customer.dto.UpdateKycStatusRequest;
import com.fcrc.customer.dto.UpdateOnboardingRequest;
import com.fcrc.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户中心 REST API。
 *
 * <p>路由约定：/api/** 面向网关和前端；/internal/** 面向集群内服务。
 * Controller 只做协议转换，业务状态机放在 CustomerService。</p>
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ApiResponse.ok("客户建档成功", service.create(request));
    }

    @GetMapping
    public ApiResponse<PageResult<CustomerResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(keyword, status, page, size));
    }

    @GetMapping("/stats")
    public ApiResponse<CustomerStats> stats() {
        return ApiResponse.ok(service.stats());
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/{id}/kyc-status")
    public ApiResponse<CustomerResponse> updateKycStatus(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateKycStatusRequest request) {
        return ApiResponse.ok("KYC 状态已更新", service.updateKycStatus(id, request));
    }
}
