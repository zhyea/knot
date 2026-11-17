package org.chobit.knot.svc.service.support;

import org.chobit.knot.dal.mapper.ExternalModelMapper;
import org.chobit.knot.svc.dto.model.LogicalModelDto;
import org.chobit.knot.svc.service.LogicalModelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 外部模型条目转统一模型的写边界：{@code kb_logical_models} 新建与
 * {@code kx_model_items.logical_model_id} 回填必须同生共死。
 *
 * <p>独立成 bean 而不是在 {@code ExternalModelService} 内标注解：批量导入在同一个实例里循环调用，
 * {@code this.} 自调用走不到 Spring 代理，注解会静默失效。调用方在循环外组装请求体，
 * 本类只负责落库，所以传播级别取 {@code REQUIRES_NEW}——每条独立提交，一条失败不连坐其余。</p>
 */
@Service
public class ExternalModelImportRunner {

    private final ExternalModelMapper externalModelMapper;
    private final LogicalModelService logicalModelService;

    public ExternalModelImportRunner(ExternalModelMapper externalModelMapper,
                                     LogicalModelService logicalModelService) {
        this.externalModelMapper = externalModelMapper;
        this.logicalModelService = logicalModelService;
    }

    /**
     * 落一条导入：先建统一模型，再把新模型 id 回填到外部条目。
     *
     * @param request 已组装好的统一模型请求体（编码唯一性由调用方在事务外校验）
     * @param itemId  外部模型条目主键
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LogicalModelDto importOne(LogicalModelDto request, Long itemId) {
        LogicalModelDto created = logicalModelService.create(request);
        externalModelMapper.updateItemLogicalModel(itemId, created.id());
        return created;
    }
}
