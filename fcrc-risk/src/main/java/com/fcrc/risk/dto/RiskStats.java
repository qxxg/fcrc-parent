package com.fcrc.risk.dto;

/** 风控看板统计。 */
public record RiskStats(long total, long low, long medium, long high, long critical, double averageScore) {
}
