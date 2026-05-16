package org.chobit.knot.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

@Service
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public OperationLogService(OperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步保存日志（不阻塞主流程）
     */
    @Async
    public void saveAsync(OperationLogEntity entity) {
        try {
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("Failed to save operation log: {}", e.getMessage(), e);
        }
    }

    /**
     * 同步保存日志（用于测试或特殊场景）
     */
    public void save(OperationLogEntity entity) {
        operationLogMapper.insert(entity);
    }

    public List<OperationLogEntity> listByModule(String module, String entityType, Long entityId, Long operatorId) {
        List<OperationLogEntity> list = operationLogMapper.listByModule(module, entityType, entityId, operatorId);
        list.forEach(this::retainOnlyChangedJsonFields);
        return list;
    }

    public OperationLogEntity getById(Long id) {
        OperationLogEntity entity = operationLogMapper.getById(id);
        if (entity != null) {
            retainOnlyChangedJsonFields(entity);
        }
        return entity;
    }

    /**
     * 某枚举分类下的配置变更操作日志（module=enum，entity_name 为分类或 分类/编码）
     */
    public List<OperationLogEntity> listForEnumCategory(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        List<OperationLogEntity> list = operationLogMapper.listByModuleAndEntityNamePrefix("enum", category.trim());
        list.forEach(this::retainOnlyChangedJsonFields);
        return list;
    }

    /**
     * 若 old_value、new_value 均为 JSON 对象，则仅保留二者中值不一致的字段（便于列表展示）。
     * 仅一方有值或非对象结构时不做裁剪。
     */
    private void retainOnlyChangedJsonFields(OperationLogEntity e) {
        String oldV = e.getOldValue();
        String newV = e.getNewValue();
        if (oldV == null || oldV.isBlank() || newV == null || newV.isBlank()) {
            return;
        }
        try {
            JsonNode oldRoot = objectMapper.readTree(oldV);
            JsonNode newRoot = objectMapper.readTree(newV);
            if (!oldRoot.isObject() || !newRoot.isObject()) {
                return;
            }
            ObjectNode oldOut = objectMapper.createObjectNode();
            ObjectNode newOut = objectMapper.createObjectNode();
            SortedSet<String> keys = new TreeSet<>();
            oldRoot.fieldNames().forEachRemaining(keys::add);
            newRoot.fieldNames().forEachRemaining(keys::add);
            for (String k : keys) {
                JsonNode ov = oldRoot.path(k);
                JsonNode nv = newRoot.path(k);
                if (ov.equals(nv)) {
                    continue;
                }
                if (!ov.isMissingNode()) {
                    oldOut.set(k, oldRoot.get(k));
                }
                if (!nv.isMissingNode()) {
                    newOut.set(k, newRoot.get(k));
                }
            }
            if (oldOut.isEmpty() && newOut.isEmpty()) {
                e.setOldValue(null);
                e.setNewValue(null);
            } else {
                e.setOldValue(oldOut.isEmpty() ? null : objectMapper.writeValueAsString(oldOut));
                e.setNewValue(newOut.isEmpty() ? null : objectMapper.writeValueAsString(newOut));
            }
        } catch (Exception ex) {
            log.debug("Skip old/new diff trim for operation log: {}", ex.toString());
        }
    }
}
