package com.fcrc.compliance.dto;

/** 合规看板统计。 */
public record ComplianceStats(long total, long pass, long review, long fail, long watchlistEntries) {
}
