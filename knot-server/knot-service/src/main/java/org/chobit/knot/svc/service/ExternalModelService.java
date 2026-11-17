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
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.svc.converter.ExternalModelConverter;
import org.chobit.knot.svc.service.support.ExternalModelImportRunner;
import org.chobit.knot.svc.service.support.ModelFamilyResolver;
import org.chobit.knot.svc.vo.model.ExternalModelItem;
import org.chobit.knot.svc.vo.model.ExternalModelSourceItem;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.external.ExternalModelSyncProvider;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExternalModelService {
    /** 点号是版本号的一部分（gpt-4.1），不能像空格/冒号那样折成连字符。 */
    private static final Pattern DISALLOWED = Pattern.compile("[^a-z0-9.]+");
    private static final Pattern REPEATED_DOTS = Pattern.compile("[.]{2,}");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[-.]+|[-.]+$");

    private final ExternalModelMapper externalModelMapper;
    private final LogicalModelService logicalModelService;
    private final Map<String, ExternalModelSyncProvider> providers;
    private final ModelFamilyResolver modelFamilyResolver;
    private final ExternalModelConverter converter;
    private final ExternalModelImportRunner importRunner;

    public ExternalModelService(ExternalModelMapper externalModelMapper,
                                LogicalModelService logicalModelService,
                                List<ExternalModelSyncProvider> providers,
                                ModelFamilyResolver modelFamilyResolver,
                                ExternalModelConverter converter,
                                ExternalModelImportRunner importRunner) {
        this.externalModelMapper = externalModelMapper;
        this.logicalModelService = logicalModelService;
        this.providers = providers.stream().collect(Collectors.toMap(ExternalModelSyncProvider::sourceCode, Function.identity()));
        this.modelFamilyResolver = modelFamilyResolver;
        this.converter = converter;
        this.importRunner = importRunner;
    }

    public List<ExternalModelSourceItem> listSources() {
        return converter.toSourceItemList(externalModelMapper.listSources());
    }

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
     * 对外投影：{@code rawJson} / {@code description} / {@code contextLength} 可能由供应商详情接口补全，
     * 补全由 {@link #requireEntity} 落库，这里只负责换载体。
     * 同时被 {@code @OperationLog(oldValueSpel = "@externalModelService.getItem(#p0)")} 使用，新老快照同载体。
     */
    @Transactional
    public ExternalModelItem getItem(Long id) {
        return converter.toItem(requireEntity(id));
    }

    /**
     * 内部取实体：缺失即 404，命中供应商 provider 时把 enrich 出的详情回写数据库。
     * 唯一允许吐 {@code ExternalModelItemEntity} 的出口，只在 svc 内部用；往外传必须走 {@link #getItem}。
     * 不标 {@code @Transactional}：内部自调用不经过代理，事务由 {@link #getItem} / {@link #createLogicalModel} 提供。
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

    @Transactional
    public ExternalModelSyncResult sync(String sourceCode) {
        ExternalModelSyncProvider provider = providers.get(sourceCode);
        if (provider == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "external model source provider not found: " + sourceCode);
        }
        return provider.sync();
    }

    /**
     * 单条导入：读条目（顺带按供应商补全详情），组装请求体，再交
     * {@link ExternalModelImportRunner} 在独立事务里落库。
     * 读与编码唯一性校验放在事务外，事务只覆盖「建模型 + 回填条目」这对写。
     */
    public LogicalModelDto createLogicalModel(Long itemId) {
        ExternalModelItemEntity item = requireEntity(itemId);
        return importRunner.importOne(toLogicalModelRequest(item), itemId);
    }

    /**
     * 批量导入：逐条独立事务，部分成功即部分落库。
     *
     * <p>失败不连坐：单条异常只记 {@code failed} 与明细（{@code itemId} + {@code modelId} + 原因），
     * 其余条目继续导入。摘要与落库结果因此严格一致。</p>
     */
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
        List<ExternalModelSyncResult.ItemFailure> failures = new ArrayList<>();
        boolean failuresTruncated = false;
        for (ExternalModelItemEntity item : items) {
            if (item.getLogicalModelId() != null) {
                skipped++;
                continue;
            }
            try {
                createLogicalModel(item.getId());
                inserted++;
            } catch (Exception e) {
                failed++;
                log.warn("External model import failed: itemId={}, modelId={}",
                        item.getId(), item.getModelId(), e);
                if (failures.size() < ExternalModelSyncResult.MAX_FAILURES) {
                    failures.add(new ExternalModelSyncResult.ItemFailure(
                            String.valueOf(item.getId()), item.getModelId(), reasonOf(e)));
                } else {
                    failuresTruncated = true;
                }
            }
        }
        return new ExternalModelSyncResult(
                items.size(), inserted, 0, skipped, failed,
                "total=" + items.size() + ", created=" + inserted + ", skipped=" + skipped + ", failed=" + failed,
                failures, failuresTruncated
        );
    }

    /**
     * 把外部条目映射成统一模型请求体：编码在事务外生成并校验唯一，
     * 以便一条失败时其余条目仍能继续（编码冲突属该条自身问题）。
     */
    private LogicalModelDto toLogicalModelRequest(ExternalModelItemEntity item) {
        return new LogicalModelDto(
                null,
                generateModelCode(item),
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
        );
    }

    /**
     * 失败原因归一：异常消息可能为空，至少让明细能定位到异常类型。
     */
    private static String reasonOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    @Transactional
    public void deleteItem(Long itemId) {
        int affected = externalModelMapper.deleteItem(itemId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "external model item not found");
        }
    }

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
        String suffix = modelKeySuffix(item.getModelId());
        String code = base + "-" + suffix;
        int i = 2;
        while (!logicalModelService.isModelCodeAvailable(code, null)) {
            code = base + "-" + suffix + "-" + i++;
        }
        return code;
    }

    /** 冲突后缀与主干同口径，避免 vendor 前缀从后门溜进编码。 */
    static String modelKeySuffix(String modelId) {
        String key = slug(modelId);
        if (key.length() > 6) {
            key = key.substring(0, 6);
        }
        return EDGE_PUNCTUATION.matcher(key).replaceAll("");
    }

    /**
     * 外部模型转统一模型的编码归一：点号是版本号的一部分（{@code gpt-4.1}）必须保留；
     * 斜杠左侧是供应商命名空间（{@code openai/gpt-4.1}）不进编码；其余非法字符归一为连字符。
     * 主干与冲突后缀共用此口径。
     */
    static String slug(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int slash = v.lastIndexOf('/');
        if (slash >= 0) {
            v = v.substring(slash + 1);
        }
        v = DISALLOWED.matcher(v).replaceAll("-");
        v = REPEATED_DOTS.matcher(v).replaceAll(".");
        v = EDGE_PUNCTUATION.matcher(v).replaceAll("");
        v = v.length() > 96 ? v.substring(0, 96) : v;
        return EDGE_PUNCTUATION.matcher(v).replaceAll("");
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
