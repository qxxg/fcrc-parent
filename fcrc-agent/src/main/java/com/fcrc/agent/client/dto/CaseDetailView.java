package com.fcrc.agent.client.dto;

import java.time.Instant;
import java.util.List;

/** 案件详情：案件信息 + 不可变备注时间线。 */
public record CaseDetailView(CaseView caseInfo, List<NoteView> notes) {

    /** 案件备注视图。 */
    public record NoteView(Long id, String author, String content, Instant createdAt) {
    }
}
