package com.fcrc.cases.dto;

import com.fcrc.cases.domain.CaseNoteEntity;
import java.time.Instant;

/** 案件备注视图。 */
public record NoteResponse(Long id, String author, String content, Instant createdAt) {

    public static NoteResponse from(CaseNoteEntity entity) {
        return new NoteResponse(entity.getId(), entity.getAuthor(), entity.getContent(), entity.getCreatedAt());
    }
}
