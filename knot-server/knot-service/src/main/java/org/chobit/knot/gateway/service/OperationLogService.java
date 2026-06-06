package org.chobit.knot.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.mapper.OperationLogMapper;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
@Slf4j
@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    /**
     * Constructs a new instance.
     */
    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    /**
     * 寮傛淇濆瓨鏃ュ織锛堜笉闃诲涓绘祦绋嬶級
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
     * Executes the public operation. Executes the public operation.
     */
    /**
     * 鍚屾淇濆瓨鏃ュ織锛堢敤浜庢祴璇曟垨鐗规畩鍦烘櫙锛?     */
    public void save(OperationLogEntity entity) {
        operationLogMapper.insert(entity);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<OperationLogEntity> listByModule(String module, String entityType, Long entityId, Long operatorId) {
        List<OperationLogEntity> list = operationLogMapper.listByModule(module, entityType, entityId, operatorId);
        list.forEach(this::retainOnlyChangedJsonFields);
        return list;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public OperationLogEntity getById(Long id) {
        OperationLogEntity entity = operationLogMapper.getById(id);
        if (entity != null) {
            retainOnlyChangedJsonFields(entity);
        }
        return entity;
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public int deleteBefore(LocalDateTime beforeTime) {
        operationLogMapper.deleteDetailsByLogCreatedBefore(beforeTime);
        return operationLogMapper.deleteByCreatedBefore(beforeTime);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    /**
     * 鏌愭灇涓惧垎绫讳笅鐨勯厤缃彉鏇存搷浣滄棩蹇楋紙module=enum锛宔ntity_name 涓哄垎绫绘垨 鍒嗙被/缂栫爜锛?     */
    public List<OperationLogEntity> listForEnumCategory(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        List<OperationLogEntity> list = operationLogMapper.listByModuleAndEntityNamePrefix("enum", category.trim());
        list.forEach(this::retainOnlyChangedJsonFields);
        return list;
    }

    /**
     * 鑻?old_value銆乶ew_value 鍧囦负 JSON 瀵硅薄锛屽垯浠呬繚鐣欎簩鑰呬腑鍊间笉涓€鑷寸殑瀛楁锛堜究浜庡垪琛ㄥ睍绀猴級銆?     * 浠呬竴鏂规湁鍊兼垨闈炲璞＄粨鏋勬椂涓嶅仛瑁佸壀銆?     */
    private void retainOnlyChangedJsonFields(OperationLogEntity e) {
        String oldV = e.getOldValue();
        String newV = e.getNewValue();
        if (oldV == null || oldV.isBlank() || newV == null || newV.isBlank()) {
            return;
        }
        try {
            JsonNode oldRoot = JsonKit.parse(oldV);
            JsonNode newRoot = JsonKit.parse(newV);
            if (oldRoot == null || newRoot == null || !oldRoot.isObject() || !newRoot.isObject()) {
                return;
            }
            ObjectNode oldOut = JsonKit.createObjectNode();
            ObjectNode newOut = JsonKit.createObjectNode();
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
                e.setOldValue(oldOut.isEmpty() ? null : JsonKit.toJson(oldOut));
                e.setNewValue(newOut.isEmpty() ? null : JsonKit.toJson(newOut));
            }
        } catch (Exception ex) {
            log.debug("Skip old/new diff trim for operation log: {}", ex.toString());
        }
    }
}
