package org.chobit.knot.gateway.constants.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计费单位归一化（{@link BillingModeEnum#normalizeUnit(String)}）的口径测试。
 *
 * <p>盯的是**单位基数的分母安全性**：金额 = 用量 × 单价 ÷ 基数，
 * {@code 1K_TOKENS}(1000) 与 {@code PER_SECOND}(1) 之间差 1000 倍。
 * 本测试存在的理由是一个真实缺陷——「单位缺失就兜底成 1K_TOKENS」曾被抄在三处
 * （计算器热路径 / 管理端落库 / DB 列 DEFAULT），而模式自己声明的默认值却是
 * {@code VIDEO→PER_SECOND}、{@code TOKEN→1M_TOKENS}；两个口径并存时，
 * 绕过管理端表单的写入会静默拿到 1000 倍错的金额，且编译与既有单测全绿。
 * 修复后三处一律改调本方法，这里守住「口径只有一份」。</p>
 */
class BillingModeUnitNormalizationTest {

    @Test
    void blankUnitFallsBackToModeDefaultNotTo1KTokens() {
        // 缺陷核心：VIDEO / AUDIO 按秒计费，兜底成 1K_TOKENS 会把每秒价再除 1000
        assertEquals(BillingUnitEnum.PER_SECOND.code(), BillingModeEnum.VIDEO.normalizeUnit(null));
        assertEquals(BillingUnitEnum.PER_SECOND.code(), BillingModeEnum.VIDEO.normalizeUnit(""));
        assertEquals(BillingUnitEnum.PER_SECOND.code(), BillingModeEnum.VIDEO.normalizeUnit("   "));
        assertEquals(BillingUnitEnum.PER_SECOND.code(), BillingModeEnum.AUDIO.normalizeUnit(null));
        assertEquals(BillingUnitEnum.PER_IMAGE.code(), BillingModeEnum.IMAGE.normalizeUnit(null));
        assertEquals(BillingUnitEnum.PER_REQUEST.code(), BillingModeEnum.REQUEST.normalizeUnit(null));
        assertEquals(BillingUnitEnum.ONE_M_TOKENS.code(), BillingModeEnum.TOKEN.normalizeUnit(null));
        assertEquals(BillingUnitEnum.ONE_K_TOKENS.code(), BillingModeEnum.FREE.normalizeUnit(null));
    }

    @Test
    void unknownUnitCodeFallsBackToModeDefault() {
        // 常量名 ONE_K_TOKENS 不是合法 code（合法是 1K_TOKENS）——写错时不能按字面接受
        assertEquals(BillingUnitEnum.ONE_M_TOKENS.code(), BillingModeEnum.TOKEN.normalizeUnit("ONE_K_TOKENS"));
        assertEquals(BillingUnitEnum.PER_SECOND.code(), BillingModeEnum.VIDEO.normalizeUnit("1K_TOKENS"));
    }

    @Test
    void unitNotSupportedByModeFallsBackToModeDefault() {
        // 1K_TOKENS 是合法单位，但 AUDIO / REQUEST 模式并不支持
        assertEquals(BillingUnitEnum.PER_SECOND.code(), BillingModeEnum.AUDIO.normalizeUnit("1M_TOKENS"));
        assertEquals(BillingUnitEnum.PER_REQUEST.code(), BillingModeEnum.REQUEST.normalizeUnit("1K_TOKENS"));
        assertEquals(BillingUnitEnum.PER_IMAGE.code(), BillingModeEnum.IMAGE.normalizeUnit("PER_SECOND"));
    }

    @Test
    void supportedUnitIsKeptAndCaseInsensitive() {
        assertEquals(BillingUnitEnum.ONE_K_TOKENS.code(), BillingModeEnum.TOKEN.normalizeUnit("1k_tokens"));
        assertEquals(BillingUnitEnum.ONE_K_TOKENS.code(), BillingModeEnum.TOKEN.normalizeUnit(" 1K_TOKENS "));
        assertEquals(BillingUnitEnum.ONE_M_TOKENS.code(), BillingModeEnum.TOKEN.normalizeUnit("1M_TOKENS"));
    }

    /**
     * 不变量：归一化结果一定是该模式支持的合法单位编码。
     *
     * <p>这条是分母安全的底线——任何输入（含 null / 空串 / 乱码）都不能产生
     * 「模式不支持」或「解析不出来」的单位，否则 {@code BillingUnitEnum.unitSizeOf}
     * 会在热路径上直接抛异常打断计费。</p>
     */
    @ParameterizedTest
    @EnumSource(BillingModeEnum.class)
    void normalizedUnitIsAlwaysSupportedByMode(BillingModeEnum mode) {
        for (String input : new String[]{null, "", "  ", "GARBAGE", "ONE_K_TOKENS", "1K_TOKENS", "PER_SECOND"}) {
            String normalized = mode.normalizeUnit(input);
            assertTrue(mode.supportsUnit(normalized),
                    mode + " 归一化 " + input + " 得到 " + normalized + "，但该模式不支持");
            if (mode.supportsUnit(input)) {
                assertEquals(BillingUnitEnum.fromCode(input).code(), normalized,
                        mode + " 支持的单位 " + input + " 应原样保留");
            } else {
                assertEquals(mode.defaultUnit().code(), normalized,
                        mode + " 不支持 " + input + "，应回落到模式默认单位");
            }
        }
    }

    @Test
    void unitSizeOfNormalizedUnitMatchesModeDefaultWhenInputIsBlank() {
        // 数值证据：VIDEO 规则漏配单位时，分母必须是 1（每秒），不是 1000（每千）
        assertEquals(1, BillingUnitEnum.unitSizeOf(BillingModeEnum.VIDEO.normalizeUnit(null)));
        assertEquals(1_000_000, BillingUnitEnum.unitSizeOf(BillingModeEnum.TOKEN.normalizeUnit(null)));
    }
}
