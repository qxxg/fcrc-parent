package com.fcrc.risk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcrc.common.api.PageResult;
import com.fcrc.common.domain.RiskResult;
import com.fcrc.common.exception.BusinessException;
import com.fcrc.common.exception.ErrorCode;
import com.fcrc.common.util.BusinessIds;
import com.fcrc.risk.domain.RiskAssessmentEntity;
import com.fcrc.risk.dto.RiskAssessmentRequest;
import com.fcrc.risk.dto.RiskAssessmentResponse;
import com.fcrc.risk.dto.RiskStats;
import com.fcrc.risk.repository.RiskAssessmentRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 风险评估应用服务：编排规则、留痕与查询。 */
@Service
public class RiskService {

    private final RiskScoringEngine engine;
    private final RiskAssessmentRepository repository;
    private final ObjectMapper objectMapper;

    public RiskService(RiskScoringEngine engine, RiskAssessmentRepository repository, ObjectMapper objectMapper) {
        this.engine = engine;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RiskAssessmentResponse assess(RiskAssessmentRequest request) {
        RiskResult result = engine.evaluate(request);
        RiskAssessmentEntity entity = new RiskAssessmentEntity();
        entity.setAssessmentNo(BusinessIds.next("RSK"));
        entity.setCustomerNo(request.customerNo());
        entity.setScore(result.score());
        entity.setLevel(result.level());
        entity.setRecommendation(result.recommendation());
        entity.setFactorsJson(writeFactors(result.factors()));
        repository.save(entity);
        return new RiskAssessmentResponse(entity.getAssessmentNo(), entity.getCustomerNo(),
                entity.getScore(), entity.getLevel(), result.factors(),
                entity.getRecommendation(), entity.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PageResult<RiskAssessmentResponse> search(String customerNo, String level, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Page<RiskAssessmentEntity> result = repository.search(blankToNull(customerNo), blankToNull(level),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.of(result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public RiskAssessmentResponse get(String assessmentNo) {
        return repository.findByAssessmentNo(assessmentNo)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "风险评估不存在: " + assessmentNo));
    }

    @Transactional(readOnly = true)
    public RiskStats stats() {
        Double average = repository.averageScore();
        return new RiskStats(
                repository.count(),
                repository.countByLevel("LOW"),
                repository.countByLevel("MEDIUM"),
                repository.countByLevel("HIGH"),
                repository.countByLevel("CRITICAL"),
                average == null ? 0.0 : Math.round(average * 10.0) / 10.0);
    }

    private RiskAssessmentResponse toResponse(RiskAssessmentEntity entity) {
        return new RiskAssessmentResponse(entity.getAssessmentNo(), entity.getCustomerNo(),
                entity.getScore(), entity.getLevel(), readFactors(entity.getFactorsJson()),
                entity.getRecommendation(), entity.getCreatedAt());
    }

    private String writeFactors(List<RiskResult.RiskFactor> factors) {
        try {
            return objectMapper.writeValueAsString(factors);
        } catch (Exception ex) {
            throw new IllegalStateException("风险因子序列化失败", ex);
        }
    }

    private List<RiskResult.RiskFactor> readFactors(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            // 历史数据损坏时不应让列表页整体 500；返回空因子并保留分数与结论。
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
