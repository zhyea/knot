package org.chobit.knot.adapter.support;

import java.util.Map;

/**
 * 协议适配的通用请求字段搬迁原语（跨供应商、跨协议）。
 *
 * <p>「值搬到目标键、目标已存在则不覆盖（调用方显式给出的值优先）、随后删源键」是各协议
 * 转换的公共动作（{@code max_completion_tokens→max_tokens}、{@code stop→stop_sequences} 等），
 * 收在一处避免每个适配器各写一份。</p>
 *
 * <p>⚠ 边界：本类只放**无供应商语义的通用原语**。供应商特有的字段语义（如 Anthropic 的
 * {@code max_tokens} 必填兜底）归各自的 {@code adapter.support.<供应商>.XxxSupport}——
 * 通用能力不住供应商专用类（判例：{@code move} 曾住在 AnthropicMessagesRequestSupport，
 * 2026-10-25 拆出）。</p>
 */
public final class RequestFieldSupport {

    private RequestFieldSupport() {
    }

    /**
     * 字段搬迁：目标键已存在则不覆盖（调用方显式给出的值优先），随后删除源键。
     */
    public static void move(Map<String, Object> body, String from, String to) {
        if (body.containsKey(from) && !body.containsKey(to)) {
            body.put(to, body.get(from));
        }
        body.remove(from);
    }
}
