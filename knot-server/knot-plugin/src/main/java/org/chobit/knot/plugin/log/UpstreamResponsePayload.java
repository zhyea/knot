package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 上游调用响应日志 payload：固定字段 + 响应快照（<b>原文与适配结果各一份</b>）。
 *
 * <p>⭐ {@code response} 里有两个 body 键，语义不同、<b>不许混用</b>：</p>
 * <ul>
 *   <li>{@code responseBody} —— {@code adapter.handleResponse} 之后的<b>适配后响应</b>：
 *       客户端收到的响应就源自它（客户端还会再叠加 {@code knot_extend}），计费输入也来自它。
 *       跨协议绑定（如 Anthropic Messages → OpenAI Chat）时，{@code id} / {@code created} /
 *       {@code finish_reason} / {@code usage} 都是网关重建的。</li>
 *   <li>{@code upstreamResponseBody} —— 供应商 <b>wire 原文</b>（非流式才有值）。
 *       要跟供应商后台对账、提工单，看这个，不要看上面那个。</li>
 * </ul>
 *
 * <p>⚠ 两个 body 都是字符串而非 JSON 树：{@code JsonKit.toTree} 只转结构、不解析内层字符串，
 * 故日志里是转义字符串，要按字段寻址需二次解析（已知限制，非缺陷）。</p>
 *
 * <p>⚠ 流式请求两者均无正文（{@code streaming=true}）：上游响应是 {@code Flux}，
 * 没有「整包字符串」这种形态。看到 {@code streaming=true} 就别找 body，不是丢了。</p>
 *
 * <p>⚠ 默认 {@code knot.plugin.log.body=truncated} 时 512 字符预算由整份快照共享，
 * 长响应下只有一个 body 能留正文 —— 留的是 {@code upstreamResponseBody}（原文不可再生，
 * 适配结果在 {@code gateway-response-log} 另有记录）。要两个都完整，临时切 {@code full}。</p>
 *
 * <p>{@code upstreamRequestId} 是供应商侧请求 id（响应头白名单归一）：提工单时
 * 供应商唯一认的检索键；无命中为 null。</p>
 *
 * <p>{@code upstreamUrl} 是本次上游调用的完整请求地址（scheme + host + path）：
 * 响应阶段与请求同值，便于三阶段日志按同一端点串联。</p>
 */
public record UpstreamResponsePayload(String providerCode,
                                      String modelCode,
                                      String protocol,
                                      String upstreamRequestId,
                                      @JsonSerialize(using = PluginLogBodySerializer.class) JsonNode response,
                                      String upstreamUrl) implements PluginLogPayload {
}
