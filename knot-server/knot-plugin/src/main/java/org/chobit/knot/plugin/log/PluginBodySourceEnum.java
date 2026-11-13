package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 日志正文所处的层次：同一次请求在不同阶段有三份不同的 body，标记标明手上这份是哪一份。
 *
 * 流式请求没有整包正文，标记不因此改变：值照旧按阶段给，正文为空由 {@code streaming} 说明。
 */
public enum PluginBodySourceEnum {

    /** 客户端发来的体：网关收到的原样，不是供应商实际收到的 */
    CLIENT("client"),

    /** 适配器转换后的体：客户端响应与计费吃这份，字段可能是网关重建的 */
    ADAPTED("adapted"),

    /** 供应商 wire 原文：对账与工单取证只能看这份 */
    WIRE("wire");

    private final String code;

    PluginBodySourceEnum(String code) {
        this.code = code;
    }

    /**
     * 日志里的取值：小写短词。
     */
    @JsonValue
    public String code() {
        return code;
    }
}
