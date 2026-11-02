package com.fcrc.cases.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 追加处置备注。 */
public record AddNoteRequest(
        @NotBlank(message = "记录人不能为空") @Size(max = 64) String author,
        @NotBlank(message = "备注内容不能为空") @Size(max = 2000) String content) {
}
