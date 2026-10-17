package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * detail.cost / detail.price 的对外序列化形态钉桩：JSON 字符串 + 十进制普通表示。
 *
 * <p>hazard 复现：{@code new BigDecimal("0.00000001").toString()} 是 {@code "1E-8"}
 * （调整指数 -8 &lt; -6），Jackson 默认沿用 toString——字段还是 BigDecimal 时，
 * 下游收到的是科学计数法。改 String 契约后由 {@code toPlainString()} 定档，
 * 本测试同时钉住正向输出与 hazard 本身（反向证据）。</p>
 *
 * <p>字符串同样不设小数位上限：单价 6 位小数除百万量级需要 12 位以上才不归零，
 * 精度由 {@code TokenBillingModeCalculatorTest} 的探针用例钉住。</p>
 */
class NormalizedUsageDetailSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void costSerializesAsQuotedPlainString() throws Exception {
        NormalizedUsageDetail detail =
                new NormalizedUsageDetail("uncachedInput", 1L, "0.00000001", "10");

        String json = mapper.writeValueAsString(detail);

        assertTrue(json.contains("\"cost\":\"0.00000001\""), json);
        assertFalse(json.contains("E-"), json);
    }

    @Test
    void priceSerializesAsQuotedPlainString() throws Exception {
        // price 与 cost 同契约：一律十进制普通字符串（类型统一，下游不用猜 number/string）。
        // 注意：科学计数法只在 BigDecimal 调整指数 < -6 时触发（见下方 1E-8 反向证据），
        // 6 位小数单价（1.5e-6，调整指数恰为 -6）留 BigDecimal 也是普通表示——
        // 真正会被咬的是 cost 侧 1.5e-9 级小额，不是 price。
        NormalizedUsageDetail detail =
                new NormalizedUsageDetail("uncachedInput", 1_000L, "0.0000000015", "0.0000015");

        String json = mapper.writeValueAsString(detail);

        assertTrue(json.contains("\"price\":\"0.0000015\""), json);
        assertFalse(json.contains("E"), json);
    }

    @Test
    void bigDecimalWouldStillEmitScientificNotation() throws Exception {
        // 反向证据：字段若维持 BigDecimal，默认 Jackson 输出即科学计数法
        assertEquals("1E-8", mapper.writeValueAsString(new BigDecimal("0.00000001")));
    }
}
