package com.fcrc.cases.dto;

import java.util.List;

/** 案件详情：聚合主记录与不可变备注时间线。 */
public record CaseDetail(CaseResponse caseInfo, List<NoteResponse> notes) {
}
