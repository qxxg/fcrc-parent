package com.fcrc.customer.service;

import com.fcrc.common.api.PageResult;
import com.fcrc.common.exception.BusinessException;
import com.fcrc.common.exception.ErrorCode;
import com.fcrc.common.util.BusinessIds;
import com.fcrc.customer.domain.CustomerEntity;
import com.fcrc.customer.dto.CreateCustomerRequest;
import com.fcrc.customer.dto.CustomerResponse;
import com.fcrc.customer.dto.CustomerStats;
import com.fcrc.customer.dto.InternalCustomerProfile;
import com.fcrc.customer.dto.UpdateKycStatusRequest;
import com.fcrc.customer.dto.UpdateOnboardingRequest;
import com.fcrc.customer.repository.CustomerRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户领域服务。
 *
 * <p>业务规则集中在这里，而不是散落在 Controller：
 * <ul>
 *   <li>客户编号由系统生成，调用方不可指定；</li>
 *   <li>KYC 状态必须按状态机流转；</li>
 *   <li>对外数据统一脱敏；</li>
 *   <li>流程回写与人工审批分离，权限模型不同。</li>
 * </ul>
 */
@Service
public class CustomerService {

    private static final Map<String, Set<String>> KYC_TRANSITIONS = Map.of(
            "PENDING", Set.of("IN_REVIEW", "REJECTED"),
            "IN_REVIEW", Set.of("APPROVED", "REJECTED"),
            "REJECTED", Set.of("IN_REVIEW"),
            "APPROVED", Set.of("IN_REVIEW"));

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        CustomerEntity entity = new CustomerEntity();
        entity.setCustomerNo(BusinessIds.next("CUS"));
        entity.setName(request.name().trim());
        entity.setCustomerType(request.customerType());
        entity.setIdNumber(blankToNull(request.idNumber()));
        entity.setPhone(blankToNull(request.phone()));
        entity.setEmail(blankToNull(request.email()));
        entity.setCountry(request.country());
        entity.setIndustry(blankToNull(request.industry()));
        entity.setAnnualIncome(request.annualIncome());
        entity.setOccupation(blankToNull(request.occupation()));
        entity.setPep(request.pep());
        entity.setChannel(request.channel() == null ? "WEB" : request.channel());
        entity.setKycStatus("PENDING");
        entity.setOnboardingStatus("NOT_STARTED");
        return CustomerResponse.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public InternalCustomerProfile getInternalProfile(Long id) {
        CustomerEntity entity = require(id);
        return new InternalCustomerProfile(entity.getCustomerNo(), entity.toProfile());
    }

    @Transactional(readOnly = true)
    public PageResult<CustomerResponse> search(String keyword, String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        String normalizedKeyword = blankToNull(keyword);
        String normalizedStatus = blankToNull(status);
        Page<CustomerEntity> result = repository.search(normalizedKeyword, normalizedStatus,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.of(result.getContent().stream().map(CustomerResponse::from).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional
    public CustomerResponse updateKycStatus(Long id, UpdateKycStatusRequest request) {
        CustomerEntity entity = require(id);
        String current = entity.getKycStatus();
        if (!current.equals(request.status())
                && !KYC_TRANSITIONS.getOrDefault(current, Set.of()).contains(request.status())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE,
                    "不允许从 " + current + " 变更为 " + request.status());
        }
        entity.setKycStatus(request.status());
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }
        return CustomerResponse.from(entity);
    }

    @Transactional
    public CustomerResponse updateOnboarding(Long id, UpdateOnboardingRequest request) {
        CustomerEntity entity = require(id);
        entity.setOnboardingStatus(request.onboardingStatus());
        if (request.riskLevel() != null) {
            entity.setRiskLevel(request.riskLevel());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }
        return CustomerResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public CustomerStats stats() {
        return new CustomerStats(
                repository.count(),
                repository.countByKycStatus("PENDING"),
                repository.countByKycStatus("IN_REVIEW"),
                repository.countByKycStatus("APPROVED"),
                repository.countByKycStatus("REJECTED"),
                repository.countByPepTrue());
    }

    private CustomerEntity require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "客户不存在: " + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
