package org.chobit.knot.svc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.svc.dto.model.ExternalModelItemQuery;
import org.chobit.knot.svc.dto.model.ExternalModelSyncResult;
import org.chobit.knot.svc.dto.model.LogicalModelDto;
import org.chobit.knot.common.constants.enums.LogicalModelPublishStatusEnum;
import org.chobit.knot.dal.entity.ExternalModelItemEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.ExternalModelMapper;
import org.chobit.knot.svc.converter.ExternalModelConverter;
import org.chobit.knot.svc.service.support.ModelFamilyResolver;
import org.chobit.knot.svc.vo.model.ExternalModelItem;
import org.chobit.knot.svc.vo.model.ExternalModelSourceItem;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.external.ExternalModelSyncProvider;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ExternalModelService {

    private final ExternalModelMapper externalModelMapper;
    private final LogicalModelService logicalModelService;
    private final Map<String, ExternalModelSyncProvider> providers;
    private final ModelFamilyResolver modelFamilyResolver;
    private final ExternalModelConverter converter;

    /**
     * Constructs a new instance.
     */
    public ExternalModelService(ExternalModelMapper externalModelMapper,
                                LogicalModelService logicalModelService,
                                List<ExternalModelSyncProvider> providers,
                                ModelFamilyResolver modelFamilyResolver,
                                ExternalModelConverter converter) {
        this.externalModelMapper = externalModelMapper;
        this.logicalModelService = logicalModelService;
        this.providers = providers.stream().collect(Collectors.toMap(ExternalModelSyncProvider::sourceCode, Function.identity()));
        this.modelFamilyResolver = modelFamilyResolver;
        this.converter = converter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<ExternalModelSourceItem> listSources() {
        return converter.toSourceItemList(externalModelMapper.listSources());
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ExternalModelItem> listItems(ExternalModelItemQuery query) {
        PageRequest pageRequest = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<ExternalModelItemEntity> pageInfo = new PageInfo<>(externalModelMapper.listItems(
                    query != null ? query.sourceCode() : null,
                    query != null ? query.syncStatus() : null,
                    StringUtils.trimToNull(query != null ? query.keyword() : null),
                    StringUtils.trimToNull(query != null ? query.modelType() : null),
                    query != null ? query.ids() : null
            ));
            return PageResult.fromPage(pageInfo, converter::toItemList, pageRequest);
        }
    }

    /**
     * 对外投影：条目的 {@code rawJson} / {@code description} / {@code contextLength} 可能由供应商
     * 详情接口补全，{@link #requireEntity} 已经把补全结果落库，这里只负责换载体。
     *
     * <p>同时被 {@code @OperationLog(oldValueSpel = "@externalModelService.getItem(#p0)")} 使用，
     * 所以老值快照也是这个 record——新旧快照同载体，审计里不会出现「一半实体一半 record」。</p>
     */
    @Transactional
    public ExternalModelItem getItem(Long id) {
        return converter.toItem(requireEntity(id));
    }

    /**
     * 内部取实体：缺失即 404，且命中供应商 provider 时把 enrich 出来的详情回写数据库。
     *
     * <p>⚠ 这是唯一允许吐 {@code ExternalModelItemEntity} 的出口，且只在 svc 内部用——
     * 谁要往外传，必须走 {@link #getItem}。</p>
     *
     * <p>⚠ 这里<b>不标</b> {@code @Transactional}：它是内部自调用，Spring 代理不会介入，标了也只是
     * 一个假的承诺。事务由两个调用方（{@link #getItem} / {@link #createLogicalModel}）提供。</p>
     */
    private ExternalModelItemEntity requireEntity(Long id) {
        ExternalModelItemEntity item = externalModelMapper.getItemById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "external model item not found");
        }
        ExternalModelSyncProvider provider = providers.get(item.getSourceCode());
        if (provider == null) {
            return item;
        }
        String rawJson = item.getRawJson();
        String description = item.getDescription();
        Integer contextLength = item.getContextLength();
        ExternalModelItemEntity enriched = provider.enrichDetail(item);
        if (!same(rawJson, enriched.getRawJson())
                || !same(description, enriched.getDescription())
                || !same(contextLength, enriched.getContextLength())) {
            externalModelMapper.updateItem(enriched);
        }
        return enriched;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Transactional
    public ExternalModelSyncResult sync(String sourceCode) {
        ExternalModelSyncProvider provider = providers.get(sourceCode);
        if (provider == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "external model source provider not found: " + sourceCode);
        }
        return provider.sync();
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public LogicalModelDto createLogicalModel(Long itemId) {
        ExternalModelItemEntity item = requireEntity(itemId);
        String code = generateModelCode(item);
        LogicalModelDto created = logicalModelService.create(new LogicalModelDto(
                null,
                code,
                item.getModelName(),
                item.getModelType() != null ? item.getModelType() : "CHAT",
                modelFamilyResolver.resolve(item.getModelId(), item.getModelName()),
                item.getDescription(),
                readStringList(item.getTagsJson()),
                List.of(),
                item.getContextLength(),
                item.getMaxCompletionTokens(),
                readStringList(item.getInputModalitiesJson()),
                readStringList(item.getOutputModalitiesJson()),
                List.of(),
                "PUBLIC",
                LogicalModelPublishStatusEnum.defaultStatus().code(),
                false,
                false,
                0,
                false,
                "Imported from " + item.getSourceCode() + ": " + item.getModelId(),
                null,
                null,
                List.of(),
                null
        ));
        externalModelMapper.updateItemLogicalModel(itemId, created.id());
        return created;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public ExternalModelSyncResult createLogicalModels(ExternalModelItemQuery query) {
        List<Long> ids = query == null || query.ids() == null
                ? List.of()
                : query.ids().stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "external model item ids are required");
        }
        List<ExternalModelItemEntity> items = externalModelMapper.listItems(
                null,
                null,
                null,
                null,
                ids
        );
        int inserted = 0;
        int skipped = 0;
        int failed = 0;
        for (ExternalModelItemEntity item : items) {
            if (item.getLogicalModelId() != null) {
                skipped++;
                continue;
            }
            try {
                createLogicalModel(item.getId());
                inserted++;
            } catch (Exception ignored) {
                failed++;
            }
        }
        return new ExternalModelSyncResult(
                items.size(), inserted, 0, skipped, failed,
                "total=" + items.size() + ", created=" + inserted + ", skipped=" + skipped + ", failed=" + failed
        );
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public void deleteItem(Long itemId) {
        int affected = externalModelMapper.deleteItem(itemId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "external model item not found");
        }
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public int deleteItems(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "external model item ids are required");
        }
        return externalModelMapper.deleteItems(itemIds);
    }

    @Transactional
    public void setIgnored(Long itemId, boolean ignored) {
        if (externalModelMapper.updateItemIgnored(itemId, ignored) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "external model item not found");
        }
    }

    private String generateModelCode(ExternalModelItemEntity item) {
        String base = slug(item.getNormalizedName() != null ? item.getNormalizedName() : item.getModelName());
        if (base.isBlank()) {
            base = "external-model";
        }
        if (logicalModelService.isModelCodeAvailable(base, null)) {
            return base;
        }
        String suffix = item.getModelId();
        suffix = suffix != null && suffix.length() > 6 ? suffix.substring(0, 6) : suffix;
        String code = base + "-" + suffix;
        int i = 2;
        while (!logicalModelService.isModelCodeAvailable(code, null)) {
            code = base + "-" + suffix + "-" + i++;
        }
        return code;
    }

    private String slug(String value) {
        String v = value == null ? "" : value.trim().toLowerCase();
        v = Pattern.compile("[^a-z0-9]+").matcher(v).replaceAll("-");
        v = Pattern.compile("(^-+|-+$)").matcher(v).replaceAll("");
        return v.length() > 96 ? v.substring(0, 96) : v;
    }

    private List<String> readStringList(String json) {
        List<String> list = JsonKit.fromJson(json, new TypeReference<>() {});
        return list != null ? list : List.of();
    }

    private boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private boolean same(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }
}
