package org.chobit.knot.gateway.model.vo.response;

import com.fasterxml.jackson.databind.JsonNode;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.controller.GatewayTaskController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 202 回执的<b>对外契约</b>：字段名、取值、轮询地址。
 *
 * <p>⭐ 为什么这一份必须单独存在：回执从 {@code Map} 换成 record 之后，字段名不再由
 * 「调用方写了什么字符串」决定，而由 {@code @JsonProperty} 决定。<b>删掉注解不会有任何
 * 编译或测试信号</b>（Jackson 会老老实实按组件名输出 {@code taskCode} / {@code queryUrl}），
 * 坏掉的却是客户端解析——它只看线上那一份 JSON。所以这里断言<b>序列化结果</b>，
 * 不是断言 Java 字段名。</p>
 *
 * <p>探针（双向）：①删掉任一 {@code @JsonProperty} → 本类必红；②把 {@link
 * AsyncTaskAcceptedResponse#TASKS_PATH} 改掉而不同步控制器 → {@code tasksPathIsSingleSource} 必红。</p>
 */
class AsyncTaskAcceptedResponseTest {

    @Test
    @DisplayName("回执 wire format：恰好三个蛇形键，值由 queued() 一次定死")
    void wireFormatIsPinned() {
        JsonNode json = JsonKit.toTree(AsyncTaskAcceptedResponse.queued("abc123"));

        assertNotNull(json);
        assertEquals(Set.of("task_id", "status", "query_url"), fieldNames(json),
                "键名是客户端解析依据，多一个少一个都是破坏契约");
        assertEquals("abc123", json.get("task_id").asText(), "task_id 是 knot 自己的句柄，不是上游 ID");
        assertEquals("queued", json.get("status").asText());
        assertEquals("/openai/v1/tasks/abc123", json.get("query_url").asText());
    }

    /**
     * 回执里的 {@code query_url} 与查询端点的映射必须同源。
     *
     * <p>分叉的后果很隐蔽：提交回 202 一切正常，客户端拿着 query_url 去轮询得到 404，
     * 而服务端两个端点的日志都干净。</p>
     */
    @Test
    @DisplayName("query_url 前缀与 GatewayTaskController 的映射同源")
    void tasksPathIsSingleSource() {
        RequestMapping mapping = GatewayTaskController.class.getAnnotation(RequestMapping.class);

        assertNotNull(mapping, "查询端点必须挂在 @RequestMapping 上");
        assertEquals(AsyncTaskAcceptedResponse.TASKS_PATH, mapping.value()[0]);
    }

    private static Set<String> fieldNames(JsonNode json) {
        Set<String> names = new LinkedHashSet<>();
        for (Iterator<String> it = json.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }
}
