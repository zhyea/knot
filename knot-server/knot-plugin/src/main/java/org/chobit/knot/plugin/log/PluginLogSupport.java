package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * 插件日志「正文」的唯一出口：决定请求 / 响应体能以什么形态进日志。
 *
 * <h3>为什么必须收口</h3>
 * <p>AI 网关的正文里是 prompt、模型输出与业务数据，可能含 PII、密钥或内部资料；
 * 日志系统的访问面、留存期与备份策略通常弱于数据库。六个日志插件原本直接
 * {@code JsonKit.toJson(payload)} 整篇输出——一旦在管理端启用，完整正文就以 INFO 落盘，
 * 且每个阶段各写一遍。插件本身已是 opt-in（默认停用），但「启用即全量泄露」不能靠运维自觉避免。</p>
 *
 * <h3>为何保留 JSON 结构而不是压成字符串</h3>
 * <p>payload 的序列化形态是被消费方依赖的契约（未知字段必须原样保留、字段可寻址）。
 * 所以截断发生在<b>文本值</b>层面：结构照旧，过长的字符串值按预算截断并打标记。
 * 压成字符串会让 {@code requestBody.vendor_only_field} 这类寻址整体失效。</p>
 *
 * <h3>策略：系统属性 {@code knot.plugin.log.body}</h3>
 * <ul>
 *   <li>{@code none}：只留字节数与字段数，正文内容一律不透出（最安全）。</li>
 *   <li>{@code truncated}（默认）：保留结构，文本值按 {@value #BODY_BUDGET} 字符总预算截断。</li>
 *   <li>{@code full}：原样输出，仅供短时排障——会落完整 prompt / 模型输出，用完必须改回。</li>
 * </ul>
 *
 * <p>未知取值一律回落 {@code truncated}：宁可少落，不可多落。</p>
 */
public final class PluginLogSupport {

    /** 截断模式下的文本总预算（字符数，整份正文共享） */
    private static final int BODY_BUDGET = 512;

    /** 正文落盘策略开关 */
    private static final String PROPERTY = "knot.plugin.log.body";

    private static final String TRUNCATED_MARK = "...(truncated)";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private enum BodyMode {
        NONE, TRUNCATED, FULL
    }

    private static final BodyMode MODE = parseMode(System.getProperty(PROPERTY, "truncated"));

    private PluginLogSupport() {
    }

    /**
     * 把正文转成可入日志的形态：结构与寻址能力保持不变，只收敛文本值的长度。
     *
     * @return 脱敏后的节点（可能为 null，调用方按 null 处理）
     */
    public static JsonNode body(JsonNode node) {
        if (node == null) {
            return null;
        }
        return switch (MODE) {
            case NONE -> summary(node);
            case FULL -> node;
            case TRUNCATED -> truncateTree(node, new int[]{BODY_BUDGET});
        };
    }

    /** none 模式：只留规模元信息，内容一律不透出 */
    private static JsonNode summary(JsonNode node) {
        ObjectNode out = MAPPER.createObjectNode();
        out.put("bytes", node.toString().getBytes(StandardCharsets.UTF_8).length);
        out.put("fields", node.size());
        out.put("redacted", true);
        return out;
    }

    /**
     * 预算式截断：整份正文共享一个字符预算，先到先得，耗尽后其余文本只留标记。
     * 这样既保住结构寻址，又保证日志体积有上界。
     */
    private static JsonNode truncateTree(JsonNode node, int[] budget) {
        if (node.isObject()) {
            ObjectNode out = MAPPER.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                out.set(entry.getKey(), truncateTree(entry.getValue(), budget));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = MAPPER.createArrayNode();
            for (JsonNode child : node) {
                out.add(truncateTree(child, budget));
            }
            return out;
        }
        if (node.isTextual() && budget[0] > 0) {
            String value = node.asText();
            if (value.length() <= budget[0]) {
                budget[0] -= value.length();
                return node;
            }
            int limit = Math.min(budget[0], value.codePointCount(0, value.length()));
            String cut = value.substring(0, value.offsetByCodePoints(0, limit));
            budget[0] = 0;
            return TextNode.valueOf(cut + TRUNCATED_MARK);
        }
        if (node.isTextual()) {
            return TextNode.valueOf(TRUNCATED_MARK);
        }
        return node;
    }

    private static BodyMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return BodyMode.TRUNCATED;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "none" -> BodyMode.NONE;
            case "full" -> BodyMode.FULL;
            default -> BodyMode.TRUNCATED;
        };
    }
}
