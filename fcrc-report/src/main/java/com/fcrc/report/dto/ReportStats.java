package com.fcrc.report.dto;

/** 报送看板统计。 */
public record ReportStats(long total, long draft, long finalized, long submitted, long sar, long ctr) {
}
