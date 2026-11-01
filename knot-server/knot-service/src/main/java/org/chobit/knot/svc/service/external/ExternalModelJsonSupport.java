package org.chobit.knot.svc.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import org.chobit.knot.common.util.JsonKit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 外部模型目录 JSON 的取值与归一化工具。
 *
 * <p>上游目录接口的字段类型很不老实：同一个字段在不同条目里可能是字符串、数字、布尔、
 * 甚至嵌套对象，缺失时给 {@code null} 或空串，时间戳还可能用 {@code 0} 表示「没有」。
 * 这些「取值口径」如果散在各个 provider 里各写一套，同步出来的条目就会因为某个源多判了
 * 一种类型而长得不一样，所以收在这里。</p>
 *
 * <p>⚠ <b>已知重复</b>：{@code OpenRouterModelSyncProvider} 里有一份同款的 private 副本
 * （{@code text / integer / longValue / epochSeconds / normalizeName / shortHash / stringList / add}），
 * 是 2026-11-01 接入 XFusionAI 之前就存在的。本次不动它——那个类没有测试护体，机械收口
 * 的收益低于改坏它的风险，列为 follow-up：等它补上测试后把副本删掉，统一调这里。</p>
 */
public final class ExternalModelJsonSupport {

    private ExternalModelJsonSupport() {
    }

    /**
     * 取字段的文本形式：文本/数字/布尔直接取，其他类型（对象、数组）序列化成 JSON。
     * 空串与 {@code null} 一律返回 {@code null}——「没给」和「给了空串」对外是同一件事。
     */
    public static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText().isBlank() ? null : value.asText();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return JsonKit.toJson(value);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static Integer integer(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        try {
            return Integer.parseInt(node.asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static Long longValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        try {
            return Long.parseLong(node.asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 秒级 epoch 转本地时间。
     *
     * <p>{@code null} 与 {@code <= 0} 都返回 {@code null}：部分源（XFusionAI 实测 7 条）
     * 用 {@code 0} 表示「没有创建时间」，直接转换会落出 1970-01-01 这种假数据。</p>
     */
    public static LocalDateTime epochSeconds(Long value) {
        if (value == null || value <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(value), ZoneId.systemDefault());
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static String normalizeName(String name) {
        return name == null ? null : name.trim().replaceAll("\\s+", " ");
    }

    /**
     * 条目内容指纹：取 SHA-256 前 8 字节，用于判断这次抓回来的条目有没有变化。
     * 拿不到 SHA-256 时退回 {@code hashCode}——那只是让「变了/没变」偶尔判错，不至于同步失败。
     */
    public static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static void add(Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value.trim());
        }
    }
}
