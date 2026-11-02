package com.fcrc.agent.client.dto;

import java.time.Instant;

/** 合规案件视图（AI 只读）。 */
public record CaseView(
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
}
