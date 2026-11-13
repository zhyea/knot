package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 日志正文所处的层次：同一次请求在不同阶段有三份不同的 body，读日志的人必须知道手上这份是哪份。
 *
 * <p>⭐ 存在的理由不是「多一个字段」：键名层面已经区分了 {@code responseBody}（适配后）与
 * {@code upstreamResponseBody}（wire 原文），但键名靠人去记，标记可以被检索、被断言、
 * 被下游规则直接消费。排查「这个 id 到底是谁生成的」时，看标记比记约定快。</p>
 *
 * <p>流式请求没有整包正文，标记不因此改变：值照旧按阶段给，正文为空由 {@code streaming} 说明。</p>
 */
public enum PluginBodySourceEnum {

    /** 客户端发来的体：网关收到的原样，<b>不是</b>供应商实际收到的 */
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
     * 日志里的取值：小写短词，避免枚举名全大写在检索与告警规则里被大小写绊住。
     */
    @JsonValue
    public String code() {
        return code;
    }
}
