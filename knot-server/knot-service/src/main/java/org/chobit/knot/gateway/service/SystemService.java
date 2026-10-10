package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.SystemConverter;
import org.chobit.knot.gateway.dto.system.OperationLogDto;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.mapper.SystemMapper;
import org.springframework.stereotype.Service;

@Service
public class SystemService {
    private final SystemMapper systemMapper;
    private final SystemConverter systemConverter;

    /**
     * Constructs a new instance.
     */
    public SystemService(SystemMapper systemMapper, SystemConverter systemConverter) {
        this.systemMapper = systemMapper;
        this.systemConverter = systemConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<OperationLogDto> listOperationLogs(PageRequest pageRequest) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<OperationLogEntity> pageInfo = new PageInfo<>(systemMapper.listOperationLogs());
            return PageResult.fromPage(pageInfo, systemConverter::toOperationLogDtoList, pageRequest);
        }
    }

}
