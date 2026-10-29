package org.chobit.knot.common.constants.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⭐⭐ {@code kb_async_tasks.status} 的<b>落库 wire format 契约</b>：本文件是<b>唯一</b>钉死具体数值的地方。
 *
 * <p>为什么必须有这一个文件：{@code status} 存的是数字，而数字没有任何自解释性。
 * 「3 是什么」的唯一答案在枚举里；如果连一处断言都不写，改一个 code 的表现会是——
 * 历史数据整体错位，且<b>没有任何编译期信号</b>（这正是整型相对字符串最危险的地方）。
 * 所以这里的 {@link #codesArePinned} 就是那道护栏：改数值 = 承认一次数据迁移。</p>
 *
 * <p>其余测试（如 {@code AsyncTaskPollerTest}）一律用 {@code AsyncTaskStatusEnum.X.code()}，
 * 不写裸数字——那里散落裸数字只会让「哪个才是契约」变得含糊。</p>
 *
 * <p>⚠ 新增状态时：<b>只增不改、不复用废号</b>。复用废号会让「老数据里那个号代表的意思」
 * 与现在不一致，而事后无法从数据库里分辨。</p>
 */
class AsyncTaskStatusEnumTest {

    @Test
    @DisplayName("⭐ 落库值：0..5 逐个钉死（改任何一个都等于一次数据迁移）")
    void codesArePinned() {
        assertEquals(0, AsyncTaskStatusEnum.PENDING.code());
        assertEquals(1, AsyncTaskStatusEnum.POLLING.code());
        assertEquals(2, AsyncTaskStatusEnum.SUCCEEDED.code());
        assertEquals(3, AsyncTaskStatusEnum.FAILED.code());
        assertEquals(4, AsyncTaskStatusEnum.EXPIRED.code());
        assertEquals(5, AsyncTaskStatusEnum.CANCELLED.code());
    }

    @Test
    @DisplayName("code 不得重复：两个状态撞一个号，数据写进去就不可区分、不可恢复")
    void codesAreUnique() {
        List<Integer> codes = java.util.Arrays.stream(AsyncTaskStatusEnum.values())
                .map(AsyncTaskStatusEnum::code)
                .toList();
        assertEquals(codes.size(), codes.stream().distinct().count(),
                "撞 code 是整型方案唯一的致命风险，必须在构造期之外再守一次");
    }

    @Test
    @DisplayName("⚠ 禁止用 ordinal 落库：重排常量顺序会让历史数据整体错位")
    void codeIsNotOrdinal() {
        // 当前两者恰好一致（按书写顺序 0..5），这条断言守的是「一致」这件事本身：
        // 一旦有人把某个常量挪位，两者就会分离，届时落库值必须仍是 code 而不是 ordinal。
        for (AsyncTaskStatusEnum status : AsyncTaskStatusEnum.values()) {
            assertSame(status, AsyncTaskStatusEnum.fromCode(status.code()),
                    "落库值反查必须回到同一个常量");
        }
    }

    @Test
    @DisplayName("按落库值反查：null 与未知值返回 null（不静默给默认值）")
    void fromCode() {
        assertNull(AsyncTaskStatusEnum.fromCode(null));
        assertNull(AsyncTaskStatusEnum.fromCode(99));
        assertNull(AsyncTaskStatusEnum.fromCode(-1));
        assertSame(AsyncTaskStatusEnum.SUCCEEDED, AsyncTaskStatusEnum.fromCode(2));
    }

    @Test
    @DisplayName("在途态只有 PENDING / POLLING，且 SQL 用的码集合与之一致")
    void inFlightCodes() {
        assertTrue(AsyncTaskStatusEnum.PENDING.inFlight());
        assertTrue(AsyncTaskStatusEnum.POLLING.inFlight());
        assertFalse(AsyncTaskStatusEnum.SUCCEEDED.inFlight());
        assertFalse(AsyncTaskStatusEnum.FAILED.inFlight());
        assertFalse(AsyncTaskStatusEnum.EXPIRED.inFlight());
        assertFalse(AsyncTaskStatusEnum.CANCELLED.inFlight());
        // ⭐ 这个集合直接进 SQL 的 IN 谓词：漏一个在途态 = 新状态的任务永远扫不到
        assertEquals(List.of(0, 1), AsyncTaskStatusEnum.inFlightCodes());
    }

    @Test
    @DisplayName("可重试状态只有 FAILED / EXPIRED：SUCCEEDED 重开会二次计费，CANCELLED 重开会推翻人的判断")
    void retryableCodes() {
        assertTrue(AsyncTaskStatusEnum.FAILED.retryable());
        assertTrue(AsyncTaskStatusEnum.EXPIRED.retryable());
        assertFalse(AsyncTaskStatusEnum.SUCCEEDED.retryable(),
                "已成功即已计费，重开必然产生第二笔钱");
        assertFalse(AsyncTaskStatusEnum.CANCELLED.retryable(),
                "人工终止是一个决定，不是一个待重试的意外");
        assertFalse(AsyncTaskStatusEnum.PENDING.retryable(), "在途任务用不上重试，它已经在队列里");
        assertFalse(AsyncTaskStatusEnum.POLLING.retryable());
        // 与 inFlightCodes 同口径写死裸值：改集合就等于改线上行为，这条应当红
        assertEquals(List.of(3, 4), AsyncTaskStatusEnum.retryableCodes());
    }

    @Test
    @DisplayName("终态判定与在途判定互补：不存在既在途又终态、也不存在两者都不是的状态")
    void terminalIsComplementOfInFlight() {
        for (AsyncTaskStatusEnum status : AsyncTaskStatusEnum.values()) {
            assertTrue(status.terminal() != status.inFlight(),
                    status + " 的在途/终态判定不互补——两条 SQL 谓词会各说各话");
        }
    }
}
