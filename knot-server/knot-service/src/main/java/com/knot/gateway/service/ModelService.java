package com.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.converter.ModelConverter;
import com.knot.gateway.dto.model.ModelDto;
import com.knot.gateway.dto.model.ModelTestResultDto;
import com.knot.gateway.dto.model.ModelVersionSwitchResultDto;
import com.knot.gateway.entity.ModelEntity;
import com.knot.gateway.entity.ModelVersionEntity;
import com.knot.gateway.mapper.ModelMapper;
import com.knot.gateway.mapper.ModelVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ModelService {
    private final ModelMapper modelMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final ModelConverter modelConverter;

    public ModelService(ModelMapper modelMapper, ModelVersionMapper modelVersionMapper, ModelConverter modelConverter) {
        this.modelMapper = modelMapper;
        this.modelVersionMapper = modelVersionMapper;
        this.modelConverter = modelConverter;
    }

    public PageResult<ModelDto> list(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<ModelEntity> pageInfo = new PageInfo<>(modelMapper.list());
        return PageResult.fromPage(pageInfo, modelConverter::toDtoList, pageRequest);
    }

    public ModelDto getById(Long id) {
        ModelEntity entity = modelMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model not found");
        }
        return modelConverter.toDto(entity);
    }

    @Transactional
    public ModelDto create(ModelDto request) {
        ModelEntity entity = modelConverter.toEntity(request);
        if (entity.getModelCode() == null || entity.getModelCode().isBlank()) {
            entity.setModelCode("model_" + System.currentTimeMillis());
        }
        modelMapper.insert(entity);
        // 自动创建初始版本
        if (entity.getVersion() != null) {
            ModelVersionEntity version = new ModelVersionEntity();
            version.setModelId(entity.getId());
            version.setVersion(entity.getVersion());
            version.setGrayPercent(100);
            version.setStatus("ACTIVE");
            modelVersionMapper.insert(version);
        }
        return modelConverter.toDto(entity);
    }

    @Transactional
    public ModelDto update(Long id, ModelDto request) {
        ModelEntity existing = modelMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model not found");
        }
        ModelEntity entity = modelConverter.toEntity(request);
        entity.setId(id);
        entity.setModelCode(existing.getModelCode());
        modelMapper.update(entity);
        return modelConverter.toDto(entity);
    }

    // ==================== 模型测试 ====================

    public ModelTestResultDto testModel(Long id, String prompt) {
        ModelDto model = getById(id);
        // 当前为模拟测试，后续对接真实模型调用
        String output = "[test] " + model.name() + ": " + prompt;
        int latencyMs = 100 + (int) (Math.random() * 200);
        int tokenUsage = Math.max(1, prompt.length() / 2);
        return new ModelTestResultDto(output, latencyMs, tokenUsage);
    }

    // ==================== 版本切换 ====================

    @Transactional
    public ModelVersionSwitchResultDto switchVersion(Long id, String targetVersion) {
        getById(id); // ensure exists
        // deactivate current active version
        ModelVersionEntity current = modelVersionMapper.getActiveVersion(id);
        if (current != null) {
            current.setStatus("INACTIVE");
            modelVersionMapper.updateStatus(current);
        }
        // create or activate target version
        ModelVersionEntity newVersion = new ModelVersionEntity();
        newVersion.setModelId(id);
        newVersion.setVersion(targetVersion);
        newVersion.setGrayPercent(100);
        newVersion.setStatus("ACTIVE");
        modelVersionMapper.insert(newVersion);
        return new ModelVersionSwitchResultDto(id, targetVersion, "ACTIVE");
    }

}
