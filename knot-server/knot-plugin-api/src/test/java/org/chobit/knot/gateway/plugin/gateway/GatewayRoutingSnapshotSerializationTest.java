package org.chobit.knot.gateway.plugin.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GatewayRoutingSnapshot} 的序列化测试：覆盖三类边界——
 * 无 app/department、空候选列表、以及固定字段为 null 时仍照旧输出。
 */
class GatewayRoutingSnapshotSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldOmitAppAndDepartmentWhenRoutingInfoAbsent() throws Exception {
        JsonNode json = mapper.valueToTree(
                new GatewayRoutingSnapshot(7L, "RULE-1", 3L, false, 0, null, null));

        assertEquals(7L, json.get("ruleId").asLong());
        assertEquals("RULE-1", json.get("ruleCode").asText());
        assertEquals(3L, json.get("consumerId").asLong());
        assertFalse(json.get("returnUsageDetail").asBoolean());
        assertEquals(0, json.get("candidateCount").asInt());
        assertFalse(json.has("appId"), "无 app 时不应输出该 key（与原先不 put 一致）");
        assertFalse(json.has("department"), "无 department 时不应输出该 key");
    }

    @Test
    void shouldKeepFixedFieldsWithNullValues() throws Exception {
        JsonNode json = mapper.valueToTree(
                new GatewayRoutingSnapshot(null, null, null, true, 0, null, null));

        // 固定字段原先是无条件 put 的，即使为 null 也应继续出现
        assertTrue(json.has("ruleId"));
        assertTrue(json.get("ruleId").isNull());
        assertTrue(json.has("ruleCode"));
        assertTrue(json.get("ruleCode").isNull());
        assertTrue(json.has("consumerId"));
        assertTrue(json.get("consumerId").isNull());
    }

    @Test
    void shouldSerializeFullSnapshot() throws Exception {
        JsonNode json = mapper.valueToTree(
                new GatewayRoutingSnapshot(7L, "RULE-1", 3L, true, 4, "APP-1", "研发"));

        assertEquals(4, json.get("candidateCount").asInt());
        assertEquals("APP-1", json.get("appId").asText());
        assertEquals("研发", json.get("department").asText());
    }
}
