package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.BillingItemTypes;
import org.chobit.knot.gateway.constants.BillingModes;
import org.chobit.knot.gateway.constants.BillingUnits;
import org.chobit.knot.gateway.constants.CurrencyCodes;
import org.chobit.knot.gateway.constants.EntityStatus;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.BillingConverter;
import org.chobit.knot.gateway.dto.billing.BillingRuleDto;
import org.chobit.knot.gateway.dto.billing.ReconciliationResultDto;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.BillingRuleVersionEntity;
import org.chobit.knot.gateway.entity.BillingRuleVersionItemEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BillingService {
    private final BillingRuleMapper billingRuleMapper;
    private final ModelMapper modelMapper;
    private final BillingConverter billingConverter;

    public BillingService(BillingRuleMapper billingRuleMapper, ModelMapper modelMapper, BillingConverter billingConverter) {
        this.billingRuleMapper = billingRuleMapper;
        this.modelMapper = modelMapper;
        this.billingConverter = billingConverter;
    }

    public PageResult<BillingRuleDto> listRules(PageRequest pageRequest) {
        return listRules(pageRequest, null, null, null);
    }

    public PageResult<BillingRuleDto> listRules(PageRequest pageRequest, String keyword, Long providerId, Long logicalModelId) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<BillingRuleEntity> pageInfo = new PageInfo<>(
                billingRuleMapper.list(normalizeKeyword(keyword), providerId, logicalModelId)
        );
        return PageResult.fromPage(pageInfo, list -> list.stream().map(billingConverter::toRuleDto).toList(), pageRequest);
    }

    public BillingRuleDto getRuleById(Long id) {
        BillingRuleEntity entity = billingRuleMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        return billingConverter.toRuleDto(entity);
    }

    public Map<String, Object> billingRuleAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
            BillingRuleDto dto = getRuleById(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", dto.id());
            m.put("code", dto.code());
            m.put("name", dto.name());
            m.put("providerId", dto.providerId());
            m.put("providerName", dto.providerName());
            m.put("logicalModelId", dto.logicalModelId());
            m.put("logicalModelName", dto.logicalModelName());
            m.put("currentVersionId", dto.currentVersionId());
            m.put("versionNo", dto.versionNo());
            m.put("versionName", dto.versionName());
            m.put("billingMode", dto.billingMode());
            m.put("currency", dto.currency());
            m.put("itemType", dto.itemType());
            m.put("unit", dto.unit());
            m.put("unitPrice", dto.unitPrice());
            m.put("configJson", dto.configJson());
            m.put("ladderJson", dto.ladderJson());
            m.put("enabled", dto.enabled());
            m.put("effectiveFrom", dto.effectiveFrom());
            m.put("effectiveTo", dto.effectiveTo());
            m.put("remark", dto.remark());
            return m;
        } catch (BusinessException e) {
            return null;
        }
    }

    @Transactional
    public BillingRuleDto createRule(BillingRuleDto request) {
        validateRule(request, null);
        BillingRuleEntity e = new BillingRuleEntity();
        applyRule(e, request);
        e.setStatus(request.enabled() ? EntityStatus.ACTIVE : EntityStatus.INACTIVE);
        billingRuleMapper.insert(e);
        BillingRuleVersionEntity version = createVersion(e.getId(), request, true);
        billingRuleMapper.updateCurrentVersion(e.getId(), version.getId());
        return billingConverter.toRuleDto(billingRuleMapper.getById(e.getId()));
    }

    @Transactional
    public BillingRuleDto updateRule(Long id, BillingRuleDto request) {
        BillingRuleEntity existing = billingRuleMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        validateRule(request, id);
        if (!request.enabled()) {
            assertRuleNotBound(id, "billing rule is bound by provider models, cannot disable");
        }
        applyRule(existing, request);
        existing.setId(id);
        existing.setStatus(request.enabled() ? EntityStatus.ACTIVE : EntityStatus.INACTIVE);
        BillingRuleVersionEntity version = createVersion(id, request, request.enabled());
        existing.setCurrentVersionId(version.getId());
        billingRuleMapper.update(existing);
        return billingConverter.toRuleDto(billingRuleMapper.getById(id));
    }

    @Transactional
    public void deleteRule(Long id) {
        BillingRuleEntity existing = billingRuleMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        assertRuleNotBound(id, "billing rule is bound by provider models, cannot delete");
        billingRuleMapper.softDelete(id);
    }

    public ReconciliationResultDto reconcile(String providerCode, String billDate) {
        return new ReconciliationResultDto(providerCode, billDate, 0, 0, "DONE");
    }

    public Map<String, Object> calculateUsageDetail(Long modelId, Map<String, Object> usage) {
        if (modelId == null) {
            return null;
        }
        ModelEntity model = modelMapper.getById(modelId);
        if (model == null || model.getBillingRuleId() == null) {
            return null;
        }
        BillingRuleEntity rule = billingRuleMapper.getActiveByRuleId(model.getBillingRuleId(), LocalDateTime.now());
        if (rule == null) {
            return null;
        }
        Map<String, Object> normalizedUsage = usage == null ? Map.of() : usage;
        BillingAmount amount = calculateAmount(rule, normalizedUsage);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("modelId", modelId);
        detail.put("billingRuleId", rule.getId());
        detail.put("billingRuleCode", rule.getCode());
        detail.put("billingRuleName", rule.getName());
        detail.put("versionNo", rule.getVersionNo());
        detail.put("billingMode", rule.getBillingMode());
        detail.put("currency", rule.getCurrency());
        detail.put("itemType", rule.getItemType());
        detail.put("unit", rule.getUnit());
        detail.put("unitSize", unitSize(rule.getUnit()));
        detail.put("unitPrice", safeMoney(rule.getUnitPrice()));
        detail.put("usage", amount.usage());
        detail.put("totalCost", amount.totalCost());
        return detail;
    }

    private BillingAmount calculateAmount(BillingRuleEntity rule, Map<String, Object> usage) {
        String mode = normalizeBillingMode(rule.getBillingMode());
        String itemType = normalizeItemType(rule.getItemType());
        int unitSize = unitSize(rule.getUnit());
        BigDecimal unitPrice = safeMoney(rule.getUnitPrice());
        if (BillingModes.TOKEN.equals(mode)) {
            long inputTokens = firstLong(usage, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS);
            long outputTokens = firstLong(usage, AiPayloadFields.COMPLETION_TOKENS, AiPayloadFields.OUTPUT_TOKENS);
            long totalTokens = firstLong(usage, AiPayloadFields.TOTAL_TOKENS);
            long cacheReadTokens = nestedLong(usage, "prompt_tokens_details", "cached_tokens")
                    + nestedLong(usage, "input_tokens_details", "cached_tokens");
            BigDecimal inputPrice = jsonDecimal(rule.getConfigJson(), "inputUnitPrice", unitPrice);
            BigDecimal outputPrice = jsonDecimal(rule.getConfigJson(), "outputUnitPrice", unitPrice);
            BigDecimal cacheReadPrice = jsonDecimal(rule.getConfigJson(), "cacheReadUnitPrice", BigDecimal.ZERO);
            BigDecimal inputCost = cost(inputTokens - cacheReadTokens, inputPrice, unitSize);
            BigDecimal outputCost = cost(outputTokens, outputPrice, unitSize);
            BigDecimal cacheReadCost = cost(cacheReadTokens, cacheReadPrice, unitSize);
            Map<String, Object> parts = new LinkedHashMap<>();
            parts.put("inputTokens", inputTokens);
            parts.put("outputTokens", outputTokens);
            parts.put("totalTokens", totalTokens > 0 ? totalTokens : inputTokens + outputTokens);
            parts.put("cacheReadTokens", cacheReadTokens);
            parts.put("inputCost", inputCost);
            parts.put("outputCost", outputCost);
            parts.put("cacheReadCost", cacheReadCost);
            return new BillingAmount(parts, inputCost.add(outputCost).add(cacheReadCost));
        }
        long amount = switch (mode) {
            case BillingModes.EMBEDDING -> firstLong(usage, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS, AiPayloadFields.TOTAL_TOKENS);
            case BillingModes.REQUEST -> 1L;
            case BillingModes.IMAGE -> firstLong(usage, "image_count", "images", "n");
            case BillingModes.AUDIO -> firstLong(usage, "duration_seconds", "audio_seconds", "seconds");
            case BillingModes.VIDEO -> firstLong(usage, "duration_seconds", "video_seconds", "seconds");
            default -> firstLong(usage, AiPayloadFields.TOTAL_TOKENS, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS);
        };
        if (amount <= 0 && (BillingModes.IMAGE.equals(mode) || BillingModes.REQUEST.equals(mode))) {
            amount = 1L;
        }
        Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("amount", amount);
        parts.put("itemType", itemType);
        return new BillingAmount(parts, cost(amount, unitPrice, unitSize));
    }

    private record BillingAmount(Map<String, Object> usage, BigDecimal totalCost) {
    }

    private void applyRule(BillingRuleEntity entity, BillingRuleDto request) {
        entity.setCode(normalizeCode(request.code()));
        entity.setName(trimToEmpty(request.name()));
        entity.setProviderId(request.providerId());
        entity.setLogicalModelId(request.logicalModelId());
        entity.setRemark(blankToNull(request.remark()));
        entity.setStatus(request.enabled() ? EntityStatus.ACTIVE : EntityStatus.INACTIVE);
    }

    private BillingRuleVersionEntity createVersion(Long ruleId, BillingRuleDto request, boolean active) {
        Integer maxVersionNo = billingRuleMapper.maxVersionNo(ruleId);
        BillingRuleVersionEntity version = new BillingRuleVersionEntity();
        version.setRuleId(ruleId);
        version.setVersionNo(maxVersionNo == null ? 1 : maxVersionNo + 1);
        version.setVersionName(blankToNull(request.versionName()) == null
                ? "v" + version.getVersionNo()
                : request.versionName().trim());
        version.setBillingMode(normalizeBillingMode(request.billingMode()));
        version.setCurrency(normalizeCurrency(request.currency()));
        version.setConfigJson(blankToNull(request.configJson()));
        version.setLadderJson(blankToNull(request.ladderJson()));
        version.setEffectiveFrom(request.effectiveFrom() == null ? LocalDateTime.now() : request.effectiveFrom());
        version.setEffectiveTo(request.effectiveTo());
        version.setStatus(active ? EntityStatus.ACTIVE : EntityStatus.DISABLED);
        version.setChangeReason("rule saved");
        billingRuleMapper.insertVersion(version);

        BillingRuleVersionItemEntity item = new BillingRuleVersionItemEntity();
        item.setVersionId(version.getId());
        item.setItemType(normalizeItemType(request.itemType()));
        item.setUnit(normalizeUnit(request.unit()));
        item.setUnitSize(unitSize(item.getUnit()));
        item.setUnitPrice(request.unitPrice() == null ? BigDecimal.ZERO : request.unitPrice());
        item.setMetadataJson(null);
        billingRuleMapper.insertVersionItem(item);
        return version;
    }

    private void validateRule(BillingRuleDto request, Long excludeId) {
        String code = normalizeCode(request.code());
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "please input billing rule code");
        }
        if (billingRuleMapper.countByCode(code, excludeId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "billing rule code already exists");
        }
        if (trimToEmpty(request.name()).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "please input billing rule name");
        }
        BigDecimal unitPrice = request.unitPrice() == null ? BigDecimal.ZERO : request.unitPrice();
        if (unitPrice.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "unit price cannot be negative");
        }
    }

    private void assertRuleNotBound(Long id, String message) {
        Long count = billingRuleMapper.countBoundModels(id);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }

    private static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeKeyword(String keyword) {
        String value = trimToEmpty(keyword);
        return value.isEmpty() ? null : value;
    }

    private static String blankToNull(String value) {
        String normalized = trimToEmpty(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeBillingMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return BillingModes.TOKEN;
        }
        return normalized;
    }

    private static String normalizeCurrency(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? CurrencyCodes.USD : normalized;
    }

    private static String normalizeItemType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? BillingItemTypes.INPUT_TOKEN : normalized;
    }

    private static String normalizeUnit(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? BillingUnits.ONE_K_TOKENS : normalized;
    }

    private static int unitSize(String unit) {
        return switch (normalizeUnit(unit)) {
            case BillingUnits.ONE_M_TOKENS -> 1_000_000;
            case BillingUnits.PER_TOKEN -> 1;
            case BillingUnits.PER_MINUTE -> 60;
            case BillingUnits.PER_REQUEST, BillingUnits.PER_IMAGE, BillingUnits.PER_SECOND, BillingUnits.CUSTOM -> 1;
            default -> 1_000;
        };
    }

    private static BigDecimal cost(long amount, BigDecimal unitPrice, int unitSize) {
        if (amount <= 0 || unitPrice == null || unitPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(amount)
                .multiply(unitPrice)
                .divide(BigDecimal.valueOf(Math.max(1, unitSize)), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static long firstLong(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            Long parsed = longValue(value);
            if (parsed != null && parsed > 0) {
                return parsed;
            }
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private static long nestedLong(Map<String, Object> map, String objectKey, String valueKey) {
        Object nested = map.get(objectKey);
        if (!(nested instanceof Map<?, ?> nestedMap)) {
            return 0L;
        }
        Object value = ((Map<String, Object>) nestedMap).get(valueKey);
        Long parsed = longValue(value);
        return parsed == null ? 0L : parsed;
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal jsonDecimal(String json, String key, BigDecimal fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', keyIndex + pattern.length());
        if (colon < 0) {
            return fallback;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && "-0123456789.".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        if (end <= start) {
            return fallback;
        }
        try {
            return new BigDecimal(json.substring(start, end));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

}
