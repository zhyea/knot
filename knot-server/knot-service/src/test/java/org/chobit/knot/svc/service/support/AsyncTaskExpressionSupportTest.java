package org.chobit.knot.svc.service.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：档案表达式求值（{@link AsyncTaskExpressionSupport}）。
 *
 * <p>本类守两条口径不同的失败语义，这不是风格问题：</p>
 * <ul>
 *   <li><b>取值失败 → null</b>：上游响应还没成形是常态。若这里抛，轮询会因为
 *       「上游少给一个字段」直接崩，而正确行为是「还没出结果，下一拍再看」。</li>
 *   <li><b>判定失败 → 必须抛</b>：表达式写错（尤其旧版的 {@code succeeded,failed} 逗号列表）
 *       若被宽容成 {@code false}，任务会永远 RUNNING 直到超时结单——配置错被伪装成上游慢。</li>
 * </ul>
 *
 * <p>⚠ 下面几条 MVEL 的行为是 jshell 实测得出的，不是照文档猜的：</p>
 * <ul>
 *   <li>逗号列表 <b>能编译</b>（被当成语句序列），求值期才抛 {@code unresolvable property}；</li>
 *   <li>缺失键 / 数组越界抛的是 {@code PropertyAccessException} / {@code StringIndexOutOfBoundsException}
 *       —— 都<b>不</b>返回 null，所以取值路径必须有 catch。</li>
 * </ul>
 */
class AsyncTaskExpressionSupportTest {

    private static Map<String, Object> body() {
        return Map.of("data", List.of(Map.of(
                "status", "succeed",
                "code", 0,
                "outputs", List.of(Map.of("url", "https://x/a.mp4")))));
    }

    @Test
    @DisplayName("取值：可下钻 Map 与 List，返回标量文本")
    void readsNestedScalar() {
        assertEquals("succeed", AsyncTaskExpressionSupport.text(body(), "data[0].status"));
        assertEquals("https://x/a.mp4",
                AsyncTaskExpressionSupport.text(body(), "data[0].outputs[0].url"));
        assertEquals("0", AsyncTaskExpressionSupport.text(body(), "data[0].code"),
                "数字按文本返回，便于统一当作任务 ID / 状态使用");
    }

    @Test
    @DisplayName("取值取不到一律 null：缺字段 / 越界 / 根为 null / 表达式为空")
    void readMissYieldsNull() {
        assertNull(AsyncTaskExpressionSupport.text(body(), "data[0].missing"));
        assertNull(AsyncTaskExpressionSupport.text(body(), "data[9].status"),
                "上游数组还没长出元素时是越界，不是空串");
        assertNull(AsyncTaskExpressionSupport.text(body(), "data[0].outputs[0].missing"));
        assertNull(AsyncTaskExpressionSupport.text(null, "data[0].status"));
        assertNull(AsyncTaskExpressionSupport.text(body(), "  "));
        assertNull(AsyncTaskExpressionSupport.text(body(), null));
    }

    @Test
    @DisplayName("取值只认标量：整包 JSON（Map/List）不算「取到的值」")
    void structureIsNotAScalar() {
        assertNull(AsyncTaskExpressionSupport.text(body(), "data"),
                "若把 List 当值返回，result_path 填错就会把整段 JSON 当视频地址交出去");
        assertNull(AsyncTaskExpressionSupport.text(body(), "data[0]"));
    }

    @Test
    @DisplayName("取值：表达式语法错必须抛，不能被当成「取不到」")
    void syntaxErrorOnReadThrows() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AsyncTaskExpressionSupport.text(body(), "data["));
        assertTrue(error.getMessage().contains("无法编译"), error.getMessage());
    }

    @Test
    @DisplayName("判定：等值、或、contains 三种写法")
    void testExpressions() {
        Map<String, Object> vars = Map.of("status", "succeed", "body", body());
        assertTrue(AsyncTaskExpressionSupport.test(vars, "status == 'succeed'"));
        assertFalse(AsyncTaskExpressionSupport.test(vars, "status == 'failed'"));
        assertTrue(AsyncTaskExpressionSupport.test(vars,
                "status == 'succeed' || status == 'SUCCEEDED'"));
        assertTrue(AsyncTaskExpressionSupport.test(vars,
                "['succeed','SUCCEEDED'] contains status"),
                "MVEL 没有 in (...) 元组语法，多值只能写 contains");
        assertFalse(AsyncTaskExpressionSupport.test(vars, "['failed'] contains status"));
    }

    @Test
    @DisplayName("判定：可用 body 变量读整个响应，支持「状态在一处、成败标志在另一处」")
    void testExpressionReadsWholeBody() {
        Map<String, Object> vars = Map.of("status", "unknown", "body", body());
        assertTrue(AsyncTaskExpressionSupport.test(vars, "body.data[0].code == 0"));
    }

    @Test
    @DisplayName("判定：空表达式回 false（表示这一档没配），不抛")
    void blankTestExpressionIsFalse() {
        assertFalse(AsyncTaskExpressionSupport.test(Map.of("status", "x"), " "));
        assertFalse(AsyncTaskExpressionSupport.test(Map.of("status", "x"), null));
    }

    @Test
    @DisplayName("⭐ 探针：旧版逗号列表（succeeded,failed）必须抛，且报错指路迁移脚本")
    void probeLegacyCommaListThrows() {
        Map<String, Object> vars = Map.of("status", "succeeded");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AsyncTaskExpressionSupport.test(vars, "succeeded,failed"),
                "宽容成 false 的话，任务会永远 RUNNING 到超时——配置错会被当成上游慢");
        assertTrue(error.getMessage().contains("2026-10-28-async-task-mvel-expressions.sql"),
                "报错必须直接告诉运维该跑哪个迁移脚本，否则只能靠读源码反推语法：" + error.getMessage());
    }

    @Test
    @DisplayName("判定：结果不是布尔一律抛（把 status 当表达式写是最常见的手滑）")
    void nonBooleanTestResultThrows() {
        Map<String, Object> vars = Map.of("status", "succeed");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AsyncTaskExpressionSupport.test(vars, "status"));
        assertTrue(error.getMessage().contains("必须返回布尔值"), error.getMessage());
    }

    @Test
    @DisplayName("判定：数组根不受顺序影响——body 用 HashMap 也读得到")
    void worksWithHashMapRoot() {
        Map<String, Object> root = new HashMap<>();
        root.put("status", "succeed");
        assertTrue(AsyncTaskExpressionSupport.test(root, "status == 'succeed'"),
                "生产里响应体来自 Jackson，是 LinkedHashMap 而非不可变 Map");
    }

    @Test
    @DisplayName("取值（多值）：标量得单元素、数组得全集、结构跳过")
    void textsReadsScalarAndArray() {
        Map<String, Object> videos = Map.of("data", Map.of("videos", List.of(
                Map.of("url", "https://x/1.mp4"), Map.of("url", "https://x/2.mp4"))));

        assertEquals(List.of("succeed"), AsyncTaskExpressionSupport.texts(body(), "data[0].status"));
        assertEquals(List.of("https://x/1.mp4", "https://x/2.mp4"),
                AsyncTaskExpressionSupport.texts(videos, "data.videos", "url"),
                "对象数组必须按字段名取：MVEL 2.5.2 的投影 .![url] 在本路径下不可用（实测 PropertyAccessException）");
        assertTrue(AsyncTaskExpressionSupport.texts(videos, "data.videos").isEmpty(),
                "不给字段名时元素是对象 → 不是标量，不许把整段 JSON 当地址");
        assertEquals(List.of("https://x/1.mp4", "https://x/2.mp4"),
                AsyncTaskExpressionSupport.texts(Map.of("urls", List.of("https://x/1.mp4", "https://x/2.mp4")),
                        "urls"), "元素本身就是标量时，字段名留空即可");
    }

    @Test
    @DisplayName("取值（多值）：取不到回空列表，语法错仍然抛")
    void textsMissAndSyntaxError() {
        assertTrue(AsyncTaskExpressionSupport.texts(body(), "data[9].videos").isEmpty());
        assertTrue(AsyncTaskExpressionSupport.texts(body(), null).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> AsyncTaskExpressionSupport.texts(body(), "data["));
    }
}
