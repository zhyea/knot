package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.mapper.OperationLogMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationLogService {
    
    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 异步保存日志（不阻塞主流程）
     */
    @Async
    public void saveAsync(OperationLogEntity entity) {
        try {
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            // 日志记录失败不影响主业务
            System.err.println("Failed to save operation log: " + e.getMessage());
        }
    }

    /**
     * 同步保存日志（用于测试或特殊场景）
     */
    public void save(OperationLogEntity entity) {
        operationLogMapper.insert(entity);
    }

    public List<OperationLogEntity> listByModule(String module, String entityType, Long entityId, Long operatorId) {
        return operationLogMapper.listByModule(module, entityType, entityId, operatorId);
    }

    public OperationLogEntity getById(Long id) {
        return operationLogMapper.getById(id);
    }

    /**
     * 某枚举分类下的配置变更操作日志（module=enum，entity_name 为分类或 分类/编码）
     */
    public List<OperationLogEntity> listForEnumCategory(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        return operationLogMapper.listByModuleAndEntityNamePrefix("enum", category.trim());
    }
}
