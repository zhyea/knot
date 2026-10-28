package org.chobit.knot.gateway.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 契约测试：异步任务响应取值（{@link AsyncTaskJsonPath}）。
 *
 * <p>路径是运维在管理端填的配置串，取值错了的表现是「任务永远查不到 / 永远不计费」，
 * 现场极难定位，所以这里把每种形状都钉死。</p>
 */
class AsyncTaskJsonPathTest {

    @Test
    @DisplayName("空输入一律取不到，不抛异常")
    void blankInputYieldsNull() {
        assertNull(AsyncTaskJsonPath.text(null, "data.id"));
        assertNull(AsyncTaskJsonPath.text(Map.of("data", 1), " "));
        assertNull(AsyncTaskJsonPath.text(Map.of(), "data.id"));
    }

    @Test
    @DisplayName("逐段下钻取标量：data.id")
    void readsNestedField() {
        Map<String, Object> body = Map.of("data", Map.of("id", "task-1"));
        assertEquals("task-1", AsyncTaskJsonPath.text(body, "data.id"));
        assertEquals("task-1", AsyncTaskJsonPath.text(body, " data.id "));
    }

    @Test
    @DisplayName("数组下标：data[0].outputs[0].url（可灵查询响应的形状）")
    void readsArrayElement() {
        Map<String, Object> body = Map.of("data", List.of(
                Map.of("status", "succeed",
                        "outputs", List.of(Map.of("url", "https://cdn.example.com/a.mp4")))));
        assertEquals("succeed", AsyncTaskJsonPath.text(body, "data[0].status"));
        assertEquals("https://cdn.example.com/a.mp4", AsyncTaskJsonPath.text(body, "data[0].outputs[0].url"));
    }

    @Test
    @DisplayName("连续下标与越界：越界取不到，不回退到首元素")
    void arrayIndexBounds() {
        Map<String, Object> body = Map.of("rows", List.of(List.of("a", "b")));
        assertEquals("b", AsyncTaskJsonPath.text(body, "rows[0][1]"));
        assertNull(AsyncTaskJsonPath.text(body, "rows[0][2]"), "越界必须取不到，不能静默取首元素");
        assertNull(AsyncTaskJsonPath.text(body, "rows[1][0]"));
    }

    @Test
    @DisplayName("非对象节点继续下钻取不到；非标量按文本返回")
    void nonObjectAndScalar() {
        Map<String, Object> body = Map.of("data", "plain");
        assertNull(AsyncTaskJsonPath.text(body, "data.id"), "字符串上取字段必须取不到");
        // 数字/布尔按文本返回：上游 duration 是字符串 "5"，但也可能直接给数字 5
        assertEquals("5", AsyncTaskJsonPath.text(Map.of("duration", 5), "duration"));
        assertEquals("true", AsyncTaskJsonPath.text(Map.of("ok", true), "ok"));
    }

    @Test
    @DisplayName("空白值视为取不到，避免把空串当成有效状态")
    void blankValueIsNull() {
        assertNull(AsyncTaskJsonPath.text(Map.of("status", "  "), "status"));
    }

    @Test
    @DisplayName("下标段非法（非数字）取不到，不抛")
    void malformedIndexYieldsNull() {
        assertNull(AsyncTaskJsonPath.text(Map.of("rows", List.of("a")), "rows[x]"));
        assertNull(AsyncTaskJsonPath.text(Map.of("rows", List.of("a")), "rows[0"));
    }

    @Test
    @DisplayName("read 返回节点本身（供非标量场景复用）")
    void readReturnsNode() {
        Map<String, Object> node = Map.of("id", "x");
        assertSame(node, AsyncTaskJsonPath.read(Map.of("data", node), "data"));
    }

    @Test
    @DisplayName("探针：路径段拼错应与「取到值」互斥")
    void probeWrongSegmentNeverMatches() {
        Map<String, Object> body = Map.of("data", Map.of("id", "task-1"));
        assertEquals("task-1", AsyncTaskJsonPath.text(body, "data.id"));
        assertNull(AsyncTaskJsonPath.text(body, "data.task_id"),
                "若此断言失败，说明取值在猜字段名——配置填错会被静默兜住");
    }
}
