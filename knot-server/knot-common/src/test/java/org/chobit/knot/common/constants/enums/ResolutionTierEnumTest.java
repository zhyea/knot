package org.chobit.knot.common.constants.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：档位归一的映射表与「**不是白名单**」这条边界。
 *
 * <p>⚠ 最后一组用例是本类的重点：厂商会随时新增档位（768P、540P 乃至自造名），
 * 归一函数必须原样大写放行，不能因为「不在枚举里」就返回 null 或拒绝——那会让新档位永远配不上价。</p>
 */
class ResolutionTierEnumTest {

    @Test
    @DisplayName("标准档位名：原样归一（大小写不敏感）")
    void canonicalKeepsKnownTierCodes() {
        assertEquals("720P", ResolutionTierEnum.canonical("720P"));
        assertEquals("720P", ResolutionTierEnum.canonical("720p"));
        assertEquals("720P", ResolutionTierEnum.canonical(" 720P "));
        assertEquals("1080P", ResolutionTierEnum.canonical("1080p"));
        assertEquals("2K", ResolutionTierEnum.canonical("2K"));
        assertEquals("4K", ResolutionTierEnum.canonical("4k"));
        assertEquals("480P", ResolutionTierEnum.canonical("480P"));
        assertEquals("768P", ResolutionTierEnum.canonical("768P"));
    }

    @Test
    @DisplayName("尺寸写法与裸数字：收敛到标准档位名（这是枚举存在的真正理由）")
    void canonicalMapsAliases() {
        assertEquals("720P", ResolutionTierEnum.canonical("1280x720"));
        assertEquals("1080P", ResolutionTierEnum.canonical("1920X1080"));
        assertEquals("480P", ResolutionTierEnum.canonical("854x480"));
        assertEquals("2K", ResolutionTierEnum.canonical("2560x1440"));
        assertEquals("2K", ResolutionTierEnum.canonical("qhd"));
        assertEquals("4K", ResolutionTierEnum.canonical("uhd"));
        assertEquals("4K", ResolutionTierEnum.canonical("3840x2160"));
        assertEquals("720P", ResolutionTierEnum.canonical("720"));
        assertEquals("1080P", ResolutionTierEnum.canonical("1080"));
        assertEquals("768P", ResolutionTierEnum.canonical("768"));
    }

    @Test
    @DisplayName("分隔符清洗：空格 / 下划线 / 连字符都忽略（但不删 x，否则 1280x720 会被拼成一串数字）")
    void canonicalIgnoresSeparators() {
        assertEquals("720P", ResolutionTierEnum.canonical("720 P"));
        assertEquals("720P", ResolutionTierEnum.canonical("720_P"));
        assertEquals("720P", ResolutionTierEnum.canonical("720-P"));
    }

    @Test
    @DisplayName("⭐ 不是白名单：未识别的写法原样大写放行，绝不拒绝")
    void canonicalPassesThroughUnknownTiers() {
        assertEquals("AUTO", ResolutionTierEnum.canonical("auto"));
        assertEquals("540P", ResolutionTierEnum.canonical("540p"));
        // 枚举外的自造档位：归一后仍是它自己，配了价就能命中
        assertEquals("999P", ResolutionTierEnum.canonical("999p"));
        // ⚠ 未识别时只做大写，**不**改写分隔符：分隔符清洗只用于「查别名表」那一步。
        // 若这里把 "super hd" 改写成 "SUPER-HD"，配置键写的是哪个都无法保证对上；
        // 反过来保持原样，至少「配置写 super hd / 请求给 super hd」这种正常情形能命中。
        assertEquals("SUPER HD", ResolutionTierEnum.canonical("super hd"));
        assertEquals("SUPER-HD", ResolutionTierEnum.canonical("super-hd"));
        assertEquals("MYTIER", ResolutionTierEnum.canonical("mytier"));
    }

    @Test
    @DisplayName("null 与空白：返回 null（调用方按「没有档位名」处理，不等于未命中）")
    void canonicalRejectsEmpty() {
        assertNull(ResolutionTierEnum.canonical(null));
        assertNull(ResolutionTierEnum.canonical(""));
        assertNull(ResolutionTierEnum.canonical("   "));
    }

    @Test
    @DisplayName("ofCode 只认标准档位名，不做别名推断（推断是 canonical 的职责）")
    void ofCodeOnlyMatchesCanonicalCodes() {
        assertEquals(ResolutionTierEnum.P720, ResolutionTierEnum.ofCode("720P"));
        assertEquals(ResolutionTierEnum.P720, ResolutionTierEnum.ofCode("720p"));
        assertNull(ResolutionTierEnum.ofCode("1280x720"));
        assertNull(ResolutionTierEnum.ofCode("auto"));
        assertNull(ResolutionTierEnum.ofCode(null));
    }

    @Test
    @DisplayName("codes() 按声明顺序给出全部标准档位（前端建议下拉的数据源）")
    void codesFollowDeclarationOrder() {
        assertEquals(
                List.of("480P", "540P", "720P", "768P", "1080P", "2K", "4K"),
                ResolutionTierEnum.codes());
        // 每个 code 都必须能反查回自己，否则下拉给出的值配不上价
        for (ResolutionTierEnum tier : ResolutionTierEnum.values()) {
            assertTrue(ResolutionTierEnum.ofCode(tier.code()) != null, tier.code());
        }
    }
}
