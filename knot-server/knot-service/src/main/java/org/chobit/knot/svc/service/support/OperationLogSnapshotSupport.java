package org.chobit.knot.svc.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chobit.knot.common.util.JsonKit;

import java.util.Set;

/**
 * 审计快照脱敏的唯一出口；忽略「更新时间」这类高噪音、无审计价值的字段。
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
