package com.fcrc.common.api;

import java.util.List;

/**
 * 通用分页结果。
 *
 * <p>分页参数由各服务自行限制上限（例如 size 最大 100），防止恶意请求拖垮数据库。
 * 这里只负责承载结果，不依赖 Spring Data 的 Page，避免公共契约被框架类型污染。</p>
 *
 * @param items      当前页数据
 * @param total      总记录数
 * @param page       从 0 开始的页码
 * @param size       每页大小
 * @param totalPages 总页数
 * @param <T>        元素类型
 */
public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResult<>(List.copyOf(items), total, page, size, totalPages);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0L, page, size, 0);
    }
}
