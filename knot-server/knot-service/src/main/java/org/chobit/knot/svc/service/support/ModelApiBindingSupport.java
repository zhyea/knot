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
import org.chobit.knot.dal.mapper.ModelApiBindingMapper;
import org.chobit.knot.adapter.usage.extractor.DefaultTokenUsageExtractor;
import org.springframework.stereotype.Component;


/**
 * 供应商模型 ↔ 接口协议绑定（{@code kb_model_api_bindings}）的读写与增量 diff。
 *
 * <p>从 {@code ModelService} 抽出（改进清单 §4.7 拆分第二批）：这一段是纯粹的「多行子表存取」，
 * 与模型主表的生命周期无关，只有本类自己需要 {@link ModelApiBindingMapper}。</p>
 *
 * <p><b>保存策略刻意与 {@code RoutingRuleService#saveTargets} 的「先删后插」不同，不要统一</b>
 * （4.7 决议）：绑定行 id 会进操作日志与后续引用，每次保存整批换 id 是噪音；而路由目标的提交顺序
 * 不承载语义、也没被按 id 引用，全量替换更省心。两者共用的只是「编码改码守卫」
 * （{@link CodeBindingSupport}），不是明细保存策略。</p>
 */
@Component
public class ModelApiBindingSupport {

    private final ModelApiBindingMapper modelApiBindingMapper;

    /**
     * Constructs a new instance.
     */
    public ModelApiBindingSupport(ModelApiBindingMapper modelApiBindingMapper) {
        this.modelApiBindingMapper = modelApiBindingMapper;
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
     * 按绑定行 id 做增量同步：命中已有行则原地更新，新增行插入，请求中缺失的行删除。
     *
     * <p>旧实现「先按 model_id 整表删除、再全量插入」会让每次保存都重排 binding 主键。即使用户
     * 未做任何调整，行 id 也会变化：一是产生无意义的物理变更，二是 {@code modelAuditSnapshot}
     * 会带出 binding id，导致前后快照不等而误记一条「更新模型」审计日志。改为 diff 后主键稳定，
     * 无实质变化时不写库、也不触发审计。</p>
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
                entity.getRemark()
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
                || !Objects.equals(existing.getStatus(), desired.getStatus())
                || !Objects.equals(existing.getRemark(), desired.getRemark());
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
