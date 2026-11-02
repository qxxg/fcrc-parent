package com.fcrc.cases.dto;

import com.fcrc.cases.domain.ComplianceCaseEntity;
import java.time.Instant;

/** 案件列表/详情视图。 */
public record CaseResponse(
        Long id,
        String caseNo,
        String customerNo,
        String customerName,
        String type,
        String priority,
        String status,
        String title,
        String description,
        String assignee,
        String resolution,
        String sourceRef,
        Instant dueAt,
        boolean overdue,
        Instant createdAt,
        Instant updatedAt) {

    public static CaseResponse from(ComplianceCaseEntity entity) {
        boolean overdue = entity.getDueAt() != null
                && entity.getDueAt().isBefore(Instant.now())
                && !"RESOLVED".equals(entity.getStatus())
                && !"CLOSED".equals(entity.getStatus());
        return new CaseResponse(entity.getId(), entity.getCaseNo(), entity.getCustomerNo(),
                entity.getCustomerName(), entity.getType(), entity.getPriority(), entity.getStatus(),
                entity.getTitle(), entity.getDescription(), entity.getAssignee(), entity.getResolution(),
                entity.getSourceRef(), entity.getDueAt(), overdue,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
