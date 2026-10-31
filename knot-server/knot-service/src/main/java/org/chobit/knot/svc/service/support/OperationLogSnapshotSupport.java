package org.chobit.knot.svc.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chobit.knot.common.util.JsonKit;

import java.util.Set;

/**
 * 审计快照脱敏的唯一出口。
 *
 * <p>此前 {@code OperationLogAspect}（写侧）与 {@code OperationLogService}（读侧）各存一份
 * 忽略字段名单、各写一份逐字相同的递归实现——内容目前一致，但没有任何东西钉住它们。
 * 任一侧增删字段，审计快照的口径就会静默分叉：写进去的与读出来的不是同一份数据视图，
 * 且编译与单测全绿。收口到一处后，名单只有一份事实。</p>
 *
 * <p>忽略的是「更新时间」这类高噪音、无审计价值的字段。</p>
 */
public final class OperationLogSnapshotSupport {

    private static final Set<String> IGNORED_SNAPSHOT_FIELDS = Set.of(
            "updatedAt", "updated_at", "updateTime", "update_time");

    private OperationLogSnapshotSupport() {
    }

    /**
     * 文本形式脱敏：解析 -> 删字段 -> 回写。非法 JSON 原样返回，不因脱敏失败丢数据。
     */
    public static String redact(String json) {
        JsonNode root = JsonKit.parse(json);
        if (root == null) {
            return json;
        }
        removeIgnoredFields(root);
        return JsonKit.toJson(root);
    }

    /**
     * 就地删除忽略字段，递归整棵树（对象与数组都覆盖）。
     */
    public static void removeIgnoredFields(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            IGNORED_SNAPSHOT_FIELDS.forEach(objectNode::remove);
            objectNode.elements().forEachRemaining(OperationLogSnapshotSupport::removeIgnoredFields);
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.elements().forEachRemaining(OperationLogSnapshotSupport::removeIgnoredFields);
        }
    }
}
