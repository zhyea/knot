package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * detail.cost 的对外序列化形态钉桩：JSON 字符串 + 十进制普通表示。
 *
 * <p>hazard 复现：{@code new BigDecimal("0.00000001").toString()} 是 {@code "1E-8"}
 * （调整指数 -8 &lt; -6），Jackson 默认沿用 toString——cost 还是 BigDecimal 时，
 * 下游收到的是科学计数法。改 String 契约后由 {@code toPlainString()} 定档，
 * 本测试同时钉住正向输出与 hazard 本身（反向证据）。</p>
 */
class NormalizedUsageDetailSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void costSerializesAsQuotedPlainString() throws Exception {
        NormalizedUsageDetail detail =
                new NormalizedUsageDetail("uncachedInput", 1L, "0.00000001", new BigDecimal("10"));

        String json = mapper.writeValueAsString(detail);

        assertTrue(json.contains("\"cost\":\"0.00000001\""), json);
        assertFalse(json.contains("E-"), json);
    }

    @Test
    void bigDecimalWouldStillEmitScientificNotation() throws Exception {
        // 反向证据：cost 若维持 BigDecimal，默认 Jackson 输出即科学计数法
        assertEquals("1E-8", mapper.writeValueAsString(new BigDecimal("0.00000001")));
    }
}
