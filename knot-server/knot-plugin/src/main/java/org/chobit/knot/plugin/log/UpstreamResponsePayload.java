package org.chobit.knot.plugin.log;

import org.chobit.knot.plugin.PluginBodySourceEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 上游调用响应日志 payload：固定字段 + 响应快照（原文与适配结果各一份）。
 *
 * {@code responseBody} 是 {@code adapter.handleResponse} 之后的适配后响应，客户端收到的响应与计费输入都来自它；
 * {@code upstreamResponseBody} 是供应商 wire 原文：非流式为整包，流式是流结束时回填的头尾双留有界快照，与供应商对账 / 提工单看这一份。
 * 两个 body 都是字符串而非 JSON 树，按字段寻址需二次解析；流式请求（{@code streaming=true}）的 {@code responseBody} 恒为 {@code null}（适配逐事件进行，没有整包可留）。
 * {@code bodySource} 标的是正文实际来源：整包响应为 {@link PluginBodySourceEnum#ADAPTED}，
 * 流式收尾的 tee 快照与异步任务查询响应为 {@link PluginBodySourceEnum#WIRE}。
 */
public record UpstreamResponsePayload(String providerCode,
                                      String modelCode,
                                      String protocol,
                                      String upstreamRequestId,
                                      @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode response,
                                      String upstreamUrl,
                                      PluginBodySourceEnum bodySource) implements PluginLogPayload {
}
