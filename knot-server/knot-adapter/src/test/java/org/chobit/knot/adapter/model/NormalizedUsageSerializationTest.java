package org.chobit.knot.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code knot_extend.billing} 的对外空值语义钉桩：<b>无值字段整项省略，0 值照常输出</b>。
 *
 * <p>hazard 复现：{@code billingVersion} 来自规则版本号 {@code trimToNull}、
 * {@code currency} 亦可能因存量数据为空。类上不声明 {@code @JsonInclude} 时 Jackson 默认
 * ALWAYS，会写出 {@code "billingVersion": null}；而同一 {@code knot_extend} 里的
 * {@code usage} 走 {@link org.chobit.knot.adapter.usage.ModelUsage} 的 {@code NON_DEFAULT}
 * （空值省略）——一段响应两套空值口径，下游按「字段缺席」判空的逻辑在 {@code billing} 段失效。</p>
 *
 * <p>选 {@code NON_NULL} 而不是 {@code NON_DEFAULT} / {@code NON_EMPTY}：
 * 视频按秒计费时 {@code total_tokens} 合法就是 {@code 0}，被「看起来为空」吞掉会让数仓
 * 误判成上游未上报。本测试第二个用例专钉这一点。</p>
 */
class NormalizedUsageSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void nullFieldsShouldBeOmittedRatherThanSerializedAsNull() throws Exception {
        NormalizedUsage billing = new NormalizedUsage(100L, "0.002", null, null, List.of());

        String json = mapper.writeValueAsString(billing);

        assertFalse(json.contains("currency"), json);
        assertFalse(json.contains("billingVersion"), json);
        // 反向证据：有值字段必须照常输出，防止把「省略空值」写成「省略一切」
        assertTrue(json.contains("\"totalTokens\":100"), json);
        assertTrue(json.contains("\"totalCost\":\"0.002\""), json);
    }

    @Test
    void zeroTotalTokensShouldSurviveSerialization() throws Exception {
        // 0 是合法值：视频按秒计费时 token 量恒为 0，不能被 NON_EMPTY 吞掉
        NormalizedUsage billing = new NormalizedUsage(0L, "0", "CNY", "v1", List.of());

        String json = mapper.writeValueAsString(billing);

        assertTrue(json.contains("\"totalTokens\":0"), json);
        assertTrue(json.contains("\"currency\":\"CNY\""), json);
    }
}
