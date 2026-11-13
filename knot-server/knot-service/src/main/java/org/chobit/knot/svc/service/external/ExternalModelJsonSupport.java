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
 * 上游目录字段类型不稳定：同一字段可能是字符串、数字、布尔或嵌套对象，缺失为 {@code null}
 * 或空串，时间戳可能用 {@code 0} 表示「没有」。
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
     * 秒级 epoch 转本地时间；{@code null} 与 {@code <= 0} 都返回 {@code null}。
     */
    public static LocalDateTime epochSeconds(Long value) {
        if (value == null || value <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(value), ZoneId.systemDefault());
    }

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

    public static void add(Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value.trim());
        }
    }
}
