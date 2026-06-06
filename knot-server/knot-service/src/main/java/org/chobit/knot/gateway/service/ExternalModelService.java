package org.chobit.knot.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.dto.model.ExternalModelItemQuery;
import org.chobit.knot.gateway.dto.model.ExternalModelSyncResult;
import org.chobit.knot.gateway.dto.model.LogicalModelDto;
import org.chobit.knot.gateway.entity.ExternalModelItemEntity;
import org.chobit.knot.gateway.entity.ExternalModelSourceEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.ExternalModelMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.external.ExternalModelSyncProvider;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
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

    /**
     * Constructs a new instance.
     */
    public ExternalModelService(ExternalModelMapper externalModelMapper,
                                LogicalModelService logicalModelService,
                                List<ExternalModelSyncProvider> providers) {
        this.externalModelMapper = externalModelMapper;
        this.logicalModelService = logicalModelService;
        this.providers = providers.stream().collect(Collectors.toMap(ExternalModelSyncProvider::sourceCode, Function.identity()));
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<ExternalModelSourceEntity> listSources() {
        return externalModelMapper.listSources();
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ExternalModelItemEntity> listItems(ExternalModelItemQuery query) {
        PageRequest pageRequest = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<ExternalModelItemEntity> pageInfo = new PageInfo<>(externalModelMapper.listItems(
                query != null ? query.sourceCode() : null,
                query != null ? query.syncStatus() : null,
                normalizeKeyword(query != null ? query.keyword() : null),
                normalizeKeyword(query != null ? query.modelType() : null)
        ));
        return PageResult.of(pageInfo.getList(), pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    @Transactional
    public ExternalModelItemEntity getItem(Long id) {
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
        String capabilitiesJson = item.getCapabilitiesJson();
        Integer contextLength = item.getContextLength();
        ExternalModelItemEntity enriched = provider.enrichDetail(item);
        if (!same(rawJson, enriched.getRawJson())
                || !same(description, enriched.getDescription())
                || !same(capabilitiesJson, enriched.getCapabilitiesJson())
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
        ExternalModelItemEntity item = getItem(itemId);
        String code = generateModelCode(item);
        LogicalModelDto created = logicalModelService.create(new LogicalModelDto(
                null,
                code,
                item.getModelName(),
                item.getModelType() != null ? item.getModelType() : "CHAT",
                item.getModelFamily(),
                null,
                item.getSourceCode(),
                item.getModelId(),
                item.getCanonicalSlug(),
                item.getProviderCode(),
                item.getProviderName(),
                item.getModelName(),
                firstSentence(item.getDescription()),
                item.getDescription(),
                null,
                null,
                readStringList(item.getTagsJson()),
                List.of(),
                readMap(item.getCapabilitiesJson()),
                item.getContextLength(),
                item.getMaxCompletionTokens(),
                readStringList(item.getInputModalitiesJson()),
                readStringList(item.getOutputModalitiesJson()),
                List.of(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                readMap(item.getPricingJson()),
                readStringList(item.getSupportedParametersJson()),
                "PUBLIC",
                "DRAFT",
                false,
                0,
                false,
                null,
                item.getProviderName(),
                null,
                null,
                null,
                pricingSummary(item),
                "Imported from " + item.getSourceCode() + ": " + item.getModelId(),
                null,
                null,
                List.of()
        ));
        externalModelMapper.updateItemLogicalModel(itemId, created.id());
        return created;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public ExternalModelSyncResult createLogicalModels(ExternalModelItemQuery query) {
        List<ExternalModelItemEntity> items = externalModelMapper.listItems(
                query != null ? query.sourceCode() : null,
                query != null ? query.syncStatus() : null,
                normalizeKeyword(query != null ? query.keyword() : null),
                normalizeKeyword(query != null ? query.modelType() : null)
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

    private String firstSentence(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    private List<String> readStringList(String json) {
        List<String> list = JsonKit.fromJson(json, new TypeReference<>() {});
        return list != null ? list : List.of();
    }

    private Map<String, Object> readMap(String json) {
        Map<String, Object> map = JsonKit.fromJson(json, new TypeReference<>() {});
        return map != null ? map : new LinkedHashMap<>();
    }

    private String pricingSummary(ExternalModelItemEntity item) {
        Map<String, Object> pricing = readMap(item.getPricingJson());
        if (pricing.isEmpty()) {
            return null;
        }
        Object prompt = pricing.get("prompt");
        Object completion = pricing.get("completion");
        if (prompt == null && completion == null) {
            return null;
        }
        return "prompt=" + (prompt == null ? "-" : prompt) + ", completion=" + (completion == null ? "-" : completion);
    }

    private boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private boolean same(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String normalizeKeyword(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? null : text;
    }
}
