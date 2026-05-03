package com.knot.gateway.common.model;

/**
 * 分页请求参数
 */
public record PageRequest(
        int pageNum,
        int pageSize
) {
    public PageRequest {
        if (pageNum <= 0) pageNum = 1;
        if (pageSize <= 0) pageSize = 20;
        if (pageSize > 500) pageSize = 500;
    }

    public static PageRequest of(Integer pageNum, Integer pageSize) {
        return new PageRequest(
                pageNum != null ? pageNum : 1,
                pageSize != null ? pageSize : 20
        );
    }
}
