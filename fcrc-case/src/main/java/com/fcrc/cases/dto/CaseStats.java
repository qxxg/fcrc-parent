package com.fcrc.cases.dto;

/** 案件看板统计。 */
public record CaseStats(long total, long open, long inProgress, long pendingInfo, long resolved,
                        long closed, long overdue, long critical) {
}
