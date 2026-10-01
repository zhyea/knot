package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.ErrorCode;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.converter.TestRequestPresetConverter;
import org.chobit.knot.gateway.dto.routing.TestRequestPresetDto;
import org.chobit.knot.gateway.entity.TestRequestPresetEntity;
import org.chobit.knot.gateway.exception.BusinessException;
import org.chobit.knot.gateway.mapper.TestRequestPresetMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 路由调试预设请求用例服务。用例替代 {@code RoutingRuleService#defaultRequestBody} 的硬编码骨架，
 * 由用户维护完整、具体的请求体 JSON，按协议归类、全局共享复用。
 */
@Service
public class TestRequestPresetService {
    private final TestRequestPresetMapper presetMapper;
    private final TestRequestPresetConverter presetConverter;

    /**
     * Constructs a new instance.
     */
    public TestRequestPresetService(TestRequestPresetMapper presetMapper, TestRequestPresetConverter presetConverter) {
        this.presetMapper = presetMapper;
        this.presetConverter = presetConverter;
    }

    public PageResult<TestRequestPresetDto> list(PageRequest pageRequest, String keyword, String protocolCode) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<TestRequestPresetEntity> pageInfo = new PageInfo<>(
                    presetMapper.listPresets(normalizeKeyword(keyword), normalizeProtocol(protocolCode))
            );
            return PageResult.fromPage(pageInfo, presetConverter::toDtoList, pageRequest);
        }
    }

    /** 调试面板下拉用：仅返回启用中的用例（id / name / protocolCode / requestBody）。 */
    public List<TestRequestPresetDto> listActiveOptions() {
        return presetConverter.toDtoList(presetMapper.listActive());
    }

    public TestRequestPresetDto get(Long id) {
        TestRequestPresetEntity entity = presetMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预设请求不存在");
        }
        return presetConverter.toDto(entity);
    }

    @Transactional
    public TestRequestPresetDto create(TestRequestPresetDto request) {
        validate(request);
        if (presetMapper.countByCode(request.code(), null) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "编码已存在");
        }
        TestRequestPresetEntity entity = toEntity(request);
        entity.setStatus(EntityStatusEnum.ACTIVE.code());
        presetMapper.insertPreset(entity);
        return presetConverter.toDto(entity);
    }

    @Transactional
    public TestRequestPresetDto update(Long id, TestRequestPresetDto request) {
        TestRequestPresetEntity existing = presetMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预设请求不存在");
        }
        validate(request);
        if (presetMapper.countByCode(request.code(), id) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "编码已存在");
        }
        TestRequestPresetEntity entity = toEntity(request);
        entity.setId(id);
        presetMapper.updatePreset(entity);
        return presetConverter.toDto(presetMapper.getById(id));
    }

    @Transactional
    public void delete(Long id) {
        if (presetMapper.getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预设请求不存在");
        }
        presetMapper.deletePreset(id);
    }

    @Transactional
    public TestRequestPresetDto updateStatus(Long id, boolean enabled) {
        if (presetMapper.getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预设请求不存在");
        }
        presetMapper.updateStatus(id, enabled ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.INACTIVE.code());
        return presetConverter.toDto(presetMapper.getById(id));
    }

    private void validate(TestRequestPresetDto request) {
        if (request.code() == null || request.code().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "编码不能为空");
        }
        if (request.name() == null || request.name().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "名称不能为空");
        }
        if (request.requestBody() == null || request.requestBody().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体不能为空");
        }
        ModelApiProtocolEnum protocol = ModelApiProtocolEnum.fromCode(request.protocolCode());
        if (protocol == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "协议不支持或不存在");
        }
    }

    private TestRequestPresetEntity toEntity(TestRequestPresetDto request) {
        TestRequestPresetEntity entity = new TestRequestPresetEntity();
        entity.setCode(request.code().trim());
        entity.setName(request.name().trim());
        // 协议归一为 canonical code，保证跨别名一致
        entity.setProtocolCode(ModelApiProtocolEnum.fromCode(request.protocolCode()).canonical().code());
        entity.setRequestBody(request.requestBody());
        entity.setRemark(request.remark() == null ? null : request.remark().trim());
        entity.setStatus(request.status() == null ? EntityStatusEnum.ACTIVE.code() : request.status());
        return entity;
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        return value.isEmpty() ? null : value;
    }

    private static String normalizeProtocol(String protocolCode) {
        String value = protocolCode == null ? "" : protocolCode.trim();
        return value.isEmpty() ? null : value;
    }
}
