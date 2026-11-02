package com.fcrc.agent.client.dto;

import java.util.List;

/** 通用分页视图，与 {@code fcrc-common} 的 PageResult 字段保持一致。 */
public record PageView<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages) {
}
