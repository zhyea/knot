package org.chobit.knot.svc.service.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ModelTypeEnum;
import org.chobit.knot.svc.dto.model.ModelApiBindingDto;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;
import org.chobit.knot.dal.mapper.AsyncTaskProfileMapper;
import org.chobit.knot.dal.mapper.ModelApiBindingMapper;
import org.chobit.knot.adapter.usage.extractor.DefaultTokenUsageExtractor;
import org.springframework.stereotype.Component;


/**
 * 供应商模型 ↔ 接口协议绑定（{@code kb_model_api_bindings}）的读写与增量 diff。
 * 纯粹的「多行子表存取」，与模型主表生命周期无关，只有本类自己需要 {@link ModelApiBindingMapper}。
 */
@Component
public class ModelApiBindingSupport {

    private final ModelApiBindingMapper modelApiBindingMapper;

    /**
     * 档案读取，只用于「这个 {@code async_profile_code} 现在能不能用」这一条校验。
     * 下拉 / 展示所需的档案信息走 options 端点一次取全。
     */
    private final AsyncTaskProfileMapper asyncTaskProfileMapper;

    public ModelApiBindingSupport(ModelApiBindingMapper modelApiBindingMapper,
                                  AsyncTaskProfileMapper asyncTaskProfileMapper) {
        this.modelApiBindingMapper = modelApiBindingMapper;
        this.asyncTaskProfileMapper = asyncTaskProfileMapper;
    }

    /**
     * 批量装载绑定，按模型主键分组。列表页用：一次查询取回整页模型的绑定。
     */
    public Map<Long, List<ModelApiBindingDto>> mapByModelIds(List<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Map.of();
        }
        return modelApiBindingMapper.listByModelIds(modelIds).stream()
                .map(this::toDto)
                .collect(Collectors.groupingBy(
                        BindingWithModelId::modelId,
                        LinkedHashMap::new,
                        Collectors.mapping(BindingWithModelId::dto, Collectors.toList())
                ));
    }

    /**
     * 单个模型的绑定列表，无绑定时返回空列表。
     */
    public List<ModelApiBindingDto> list(Long modelId) {
        if (modelId == null) {
            return List.of();
        }
        return modelApiBindingMapper.listByModelId(modelId).stream()
                .map(this::toDto)
                .map(BindingWithModelId::dto)
                .toList();
    }

    /**
     * 按绑定行 id 做增量同步：命中已有行则原地更新，新增行插入，请求中缺失的行删除；
     * 主键稳定，无实质变化时不写库、也不触发审计。
     *
     * @param modelCode 由调用方带入：本类不持有 {@code ModelMapper}，避免为解析一次业务码
     *                  把模型主表依赖拖进子表组件
     */
    public void save(Long modelId, String modelCode, List<ModelApiBindingDto> bindings, String modelType) {
        List<ModelApiBindingEntity> desired = new ArrayList<>();
        if (bindings != null && !bindings.isEmpty()) {
            ModelTypeEnum type = ModelTypeEnum.fromCodeOrNull(modelType);
            if (type == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的模型类型，请重新选择");
            }
            for (ModelApiBindingDto binding : bindings) {
                desired.add(toEntity(modelCode, binding, type));
            }
        }
        Map<Long, ModelApiBindingEntity> existingById = modelApiBindingMapper.listByModelId(modelId).stream()
                .collect(Collectors.toMap(ModelApiBindingEntity::getId, entity -> entity,
                        (left, right) -> left, LinkedHashMap::new));
        Set<Long> retainedIds = new HashSet<>();
        for (ModelApiBindingEntity desiredEntity : desired) {
            Long id = desiredEntity.getId();
            ModelApiBindingEntity existing = id == null ? null : existingById.get(id);
            if (existing == null) {
                desiredEntity.setId(null);
                modelApiBindingMapper.insert(desiredEntity);
                continue;
            }
            retainedIds.add(id);
            if (changed(existing, desiredEntity)) {
                modelApiBindingMapper.update(desiredEntity);
            }
        }
        for (ModelApiBindingEntity existing : existingById.values()) {
            if (!retainedIds.contains(existing.getId())) {
                modelApiBindingMapper.deleteById(existing.getId());
            }
        }
    }

    private BindingWithModelId toDto(ModelApiBindingEntity entity) {
        ModelApiBindingDto dto = new ModelApiBindingDto(
                entity.getId(),
                entity.getProtocol(),
                entity.getApiPath(),
                entity.getRequestAdapter(),
                entity.getUsageExtractor(),
                entity.getStreamUsageExtractor(),
                EnabledStatusEnum.isEnabled(entity.getStatus()),
                entity.getRemark(),
                entity.getAsyncProfileCode()
        );
        return new BindingWithModelId(entity.getModelId(), dto);
    }

    /**
     * 校验并构建单条 API 绑定实体（含请求带回来的行 id，用于 diff 命中已有行）。
     */
    private ModelApiBindingEntity toEntity(String modelCode, ModelApiBindingDto binding, ModelTypeEnum type) {
        String protocolCode = blankToNull(binding.protocol());
        if (protocolCode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择接口协议");
        }
        protocolCode = protocolCode.toUpperCase();
        ModelApiProtocolEnum protocol = ModelApiProtocolEnum.fromCode(protocolCode);
        if (protocol == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的接口协议：" + protocolCode);
        }
        if (!type.supportsProtocol(protocol.code())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "模型类型“" + type.displayName() + "”不支持接口协议：" + protocolCode
            );
        }
        ModelApiBindingEntity entity = new ModelApiBindingEntity();
        entity.setId(binding.id());
        entity.setModelCode(modelCode);
        entity.setProtocol(protocol.code());
        entity.setApiPath(blankToNull(binding.apiPath()));
        entity.setRequestAdapter(blankToNull(binding.requestAdapter()));
        entity.setUsageExtractor(blankToDefault(binding.usageExtractor(), DefaultTokenUsageExtractor.CODE));
        entity.setStreamUsageExtractor(blankToNull(binding.streamUsageExtractor()));
        entity.setAsyncProfileCode(asyncProfileCodeOf(protocol, binding.asyncProfileCode()));
        entity.setStatus(EnabledStatusEnum.codeOf(binding.enabled()));
        entity.setRemark(blankToNull(binding.remark()));
        return entity;
    }

    /**
     * 仅比较可编辑字段，判断已有行是否需要真正写库。
     */
    private static boolean changed(ModelApiBindingEntity existing, ModelApiBindingEntity desired) {
        return !Objects.equals(existing.getProtocol(), desired.getProtocol())
                || !Objects.equals(existing.getApiPath(), desired.getApiPath())
                || !Objects.equals(existing.getRequestAdapter(), desired.getRequestAdapter())
                || !Objects.equals(existing.getUsageExtractor(), desired.getUsageExtractor())
                || !Objects.equals(existing.getStreamUsageExtractor(), desired.getStreamUsageExtractor())
                || !Objects.equals(existing.getAsyncProfileCode(), desired.getAsyncProfileCode())
                || !Objects.equals(existing.getStatus(), desired.getStatus())
                || !Objects.equals(existing.getRemark(), desired.getRemark());
    }

    /**
     * 任务协议（异步档案）编码的校验与归一：空 → {@code null}（同步），非空则四条都要过。
     * 流式协议直接拒（流式与异步互斥）；该事实只在 {@code ModelApiProtocolEnum#streamSupported} 判定，前端不维护第二份常量。
     */
    private String asyncProfileCodeOf(ModelApiProtocolEnum protocol, String raw) {
        String profileCode = blankToNull(raw);
        if (profileCode == null) {
            return null;
        }
        if (protocol.streamSupported()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "协议「" + protocol.displayName() + "」是流式协议，不能配置异步任务档案");
        }
        AsyncTaskProfileEntity profile = asyncTaskProfileMapper.getByProfileCode(profileCode);
        if (profile == null || !EnabledStatusEnum.isEnabled(profile.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "任务协议（异步档案）不存在或已停用：" + profileCode);
        }
        if (!protocol.matches(profile.getProtocol())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "任务协议 " + profileCode + " 属于协议 " + profile.getProtocol()
                            + "，与当前绑定的 " + protocol.code() + " 不一致");
        }
        return profileCode;
    }

    private static String blankToNull(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String blankToDefault(String value, String defaultValue) {
        String text = blankToNull(value);
        return text == null ? defaultValue : text;
    }

    private record BindingWithModelId(Long modelId, ModelApiBindingDto dto) {
    }
}
