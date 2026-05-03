package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.AppConverter;
import org.chobit.knot.gateway.dto.app.AppDto;
import org.chobit.knot.gateway.dto.app.AppMetricsDto;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppService {
    private final AppMapper appMapper;
    private final AppConverter appConverter;

    public AppService(AppMapper appMapper, AppConverter appConverter) {
        this.appMapper = appMapper;
        this.appConverter = appConverter;
    }

    public PageResult<AppDto> list(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<AppEntity> pageInfo = new PageInfo<>(appMapper.list());
        return PageResult.fromPage(pageInfo, appConverter::toDtoList, pageRequest);
    }

    public AppDto getById(Long id) {
        AppEntity entity = appMapper.getById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "app not found");
        return appConverter.toDto(entity);
    }

    @Transactional
    public AppDto create(AppDto request) {
        AppEntity entity = appConverter.toEntity(request);
        appMapper.insert(entity);
        return appConverter.toDto(entity);
    }

    @Transactional
    public AppDto update(Long id, AppDto request) {
        AppEntity existing = appMapper.getById(id);
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "app not found");
        AppEntity entity = appConverter.toEntity(request);
        entity.setId(id);
        entity.setAppId(existing.getAppId());
        appMapper.update(entity);
        return appConverter.toDto(entity);
    }

    public AppMetricsDto getAppMetrics(Long id) {
        // 从 gateway_requests 聚合统计
        Long total = appMapper.countRequestsByAppId(id);
        Long success = appMapper.countSuccessByAppId(id);
        Long tokens = appMapper.sumTokensByAppId(id);
        long totalVal = total != null ? total : 0L;
        long successVal = success != null ? success : 0L;
        long tokenVal = tokens != null ? tokens : 0L;
        return new AppMetricsDto(id, totalVal, successVal, totalVal - successVal, tokenVal);
    }

}
