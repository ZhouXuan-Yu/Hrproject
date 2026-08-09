package com.hr.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页结果包装，对齐 Flask 分页响应结构。
 */
@Data
public class PageResult<T> {
    private List<T> data;
    private long total;
    private int page;
    private int pageSize;

    public PageResult() {
    }

    public PageResult(List<T> data, long total, int page, int pageSize) {
        this.data = data;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> data, long total, int page, int pageSize) {
        return new PageResult<>(data, total, page, pageSize);
    }
}
