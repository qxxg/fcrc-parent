package com.fcrc.customer.dto;

/** 客户看板统计。 */
public record CustomerStats(long total, long pending, long inReview, long approved, long rejected, long pep) {
}
