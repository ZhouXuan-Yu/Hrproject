package com.hr.common.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void successList_keepsTotalAndPagination() {
        ApiResponse<List<String>> r = ApiResponse.successList(List.of("a", "b"), 42, 3, 10);
        assertEquals(2, r.getData().size());
        assertEquals(42, r.getTotal());
        assertEquals(3, r.getPage());
        assertEquals(10, r.getPageSize());
        assertEquals("ok", r.getMessage());
        assertNull(r.getError());
    }

    @Test
    void successList_defaultPageParams() {
        ApiResponse<List<Integer>> r = ApiResponse.successList(List.of(1), 99);
        assertEquals(99, r.getTotal());
        assertEquals(1, r.getPage());
        assertEquals(20, r.getPageSize());
    }

    @Test
    void success_basicShape() {
        ApiResponse<String> r = ApiResponse.success("hello");
        assertEquals("hello", r.getData());
        assertEquals("ok", r.getMessage());
        assertNull(r.getTotal());
        assertNull(r.getError());
    }

    @Test
    void error_basicShape() {
        ApiResponse<Void> r = ApiResponse.error("NOT_FOUND", "资源不存在");
        assertNotNull(r.getError());
        assertEquals("NOT_FOUND", r.getError().getCode());
        assertEquals("资源不存在", r.getError().getMessage());
        assertNull(r.getData());
    }
}
