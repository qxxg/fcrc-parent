package com.fcrc.compliance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcrc.common.api.PageResult;
import com.fcrc.common.domain.ComplianceResult;
import com.fcrc.common.exception.BusinessException;
import com.fcrc.common.exception.ErrorCode;
import com.fcrc.common.util.BusinessIds;
import com.fcrc.compliance.domain.ScreeningEntity;
import com.fcrc.compliance.dto.ComplianceScreenRequest;
import com.fcrc.compliance.dto.ComplianceStats;
import com.fcrc.compliance.dto.ScreeningResponse;
import com.fcrc.compliance.repository.ScreeningRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 合规筛查应用服务。 */
@Service
public class ComplianceService {

    private final ComplianceScreeningEngine engine;
    private final ScreeningRepository repository;
    private final SanctionsList sanctionsList;
    private final ObjectMapper objectMapper;

    public ComplianceService(ComplianceScreeningEngine engine, ScreeningRepository repository,
                             SanctionsList sanctionsList, ObjectMapper objectMapper) {
        this.engine = engine;
        this.repository = repository;
        this.sanctionsList = sanctionsList;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScreeningResponse screen(ComplianceScreenRequest request) {
        ComplianceResult result = engine.screen(request);
        ScreeningEntity entity = new ScreeningEntity();
        entity.setScreeningNo(BusinessIds.next("SCR"));
        entity.setCustomerNo(request.customerNo());
        entity.setResult(result.result());
        entity.setRiskPoints(result.riskPoints());
        entity.setRecommendation(result.recommendation());
        entity.setFindingsJson(writeFindings(result.findings()));
        repository.save(entity);
        return new ScreeningResponse(entity.getScreeningNo(), entity.getCustomerNo(),
                entity.getResult(), entity.getRiskPoints(), result.findings(),
                entity.getRecommendation(), entity.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PageResult<ScreeningResponse> search(String customerNo, String result, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Page<ScreeningEntity> pageResult = repository.search(blankToNull(customerNo), blankToNull(result),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.of(pageResult.getContent().stream().map(this::toResponse).toList(),
                pageResult.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public ScreeningResponse get(String screeningNo) {
        return repository.findByScreeningNo(screeningNo)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "筛查记录不存在: " + screeningNo));
    }

    @Transactional(readOnly = true)
    public ComplianceStats stats() {
        return new ComplianceStats(repository.count(), repository.countByResult("PASS"),
                repository.countByResult("REVIEW"), repository.countByResult("FAIL"),
                sanctionsList.size());
    }

    private ScreeningResponse toResponse(ScreeningEntity entity) {
        return new ScreeningResponse(entity.getScreeningNo(), entity.getCustomerNo(), entity.getResult(),
                entity.getRiskPoints(), readFindings(entity.getFindingsJson()),
                entity.getRecommendation(), entity.getCreatedAt());
    }

    private String writeFindings(List<ComplianceResult.Finding> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (Exception ex) {
            throw new IllegalStateException("合规发现项序列化失败", ex);
        }
    }

    private List<ComplianceResult.Finding> readFindings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
