package org.chobit.knot.plugin;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 日志正文所处的层次：同一次请求在不同阶段有三份不同的 body，标记标明手上这份是哪一份。
 *
 * 由分发侧按「正文实际从哪来」给出，插件不自行推断：整包响应给 {@link #ADAPTED}，
 * 流式收尾补记的 tee 快照与异步任务查询响应给 {@link #WIRE}。
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
