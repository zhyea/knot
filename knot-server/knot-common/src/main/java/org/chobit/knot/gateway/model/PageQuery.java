package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 通用 POST 分页查询请求体
 * <p>
 * 前端 postQuery 统一发送此结构，后端控制器用 @RequestBody 接收。
 * 各模块如需额外筛选参数，可直接在请求体中携带，Controller 自行读取。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageQuery(
        Integer pageNum,
        Integer pageSize,
        String category
) {
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
