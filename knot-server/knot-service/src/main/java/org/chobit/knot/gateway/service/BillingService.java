package org.chobit.knot.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.BillingItemTypeEnum;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.BillingUnitEnum;
import org.chobit.knot.gateway.constants.enums.CurrencyCodeEnum;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
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
import org.chobit.knot.gateway.util.MapNumberUtils;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BillingService {
    private static final ObjectMapper OBJECT_MAPPER = JsonKit.mapper();

    private final BillingRuleMapper billingRuleMapper;
    private final ModelMapper modelMapper;
    private final BillingConverter billingConverter;

    /**
     * Constructs a new instance.
     */
    public BillingService(BillingRuleMapper billingRuleMapper, ModelMapper modelMapper, BillingConverter billingConverter) {
        this.billingRuleMapper = billingRuleMapper;
        this.modelMapper = modelMapper;
        this.billingConverter = billingConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<BillingRuleDto> listRules(PageRequest pageRequest) {
        return listRules(pageRequest, null, null, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<BillingRuleDto> listRules(PageRequest pageRequest, String keyword, Long providerId, Long logicalModelId) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<BillingRuleEntity> pageInfo = new PageInfo<>(
                billingRuleMapper.list(normalizeKeyword(keyword), providerId, logicalModelId)
        );
        return PageResult.fromPage(pageInfo, list -> list.stream().map(billingConverter::toRuleDto).toList(), pageRequest);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public BillingRuleDto getRuleById(Long id) {
        BillingRuleEntity entity = billingRuleMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        return billingConverter.toRuleDto(entity);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
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
            m.put("versionCode", dto.versionCode());
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

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public BillingRuleDto createRule(BillingRuleDto request) {
        validateRule(request, null);
        BillingRuleEntity e = new BillingRuleEntity();
        applyRule(e, request);
        e.setStatus(request.enabled() ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.INACTIVE.code());
        billingRuleMapper.insert(e);
        BillingRuleVersionEntity version = createVersion(e.getId(), request, true, buildVersionCode(request));
        billingRuleMapper.updateCurrentVersion(e.getId(), version.getId());
        return billingConverter.toRuleDto(billingRuleMapper.getById(e.getId()));
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
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
        existing.setStatus(request.enabled() ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.INACTIVE.code());
        String versionCode = buildVersionCode(request);
        if (existing.getCurrentVersionId() == null || !versionCode.equals(existing.getVersionCode())) {
            BillingRuleVersionEntity version = createVersion(id, request, request.enabled(), versionCode);
            existing.setCurrentVersionId(version.getId());
        } else if (existing.getCurrentVersionId() != null) {
            billingRuleMapper.updateVersionStatus(
                    existing.getCurrentVersionId(),
                    request.enabled() ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.DISABLED.code()
            );
        }
        billingRuleMapper.update(existing);
        return billingConverter.toRuleDto(billingRuleMapper.getById(id));
    }

    /**
     * Updates the billing rule enabled status only.
     */
    @Transactional
    public BillingRuleDto updateStatus(Long id, boolean enabled) {
        BillingRuleEntity existing = billingRuleMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        BillingRuleDto request = billingConverter.toRuleDto(existing);
        validateRule(request, id);
        if (!enabled) {
            assertRuleNotBound(id, "billing rule is bound by provider models, cannot disable");
        }
        billingRuleMapper.updateStatus(id, enabled ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.INACTIVE.code());
        if (existing.getCurrentVersionId() != null) {
            billingRuleMapper.updateVersionStatus(
                    existing.getCurrentVersionId(),
                    enabled ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.DISABLED.code()
            );
        }
        return billingConverter.toRuleDto(billingRuleMapper.getById(id));
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public void deleteRule(Long id) {
        BillingRuleEntity existing = billingRuleMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        assertRuleNotBound(id, "billing rule is bound by provider models, cannot delete");
        billingRuleMapper.softDelete(id);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public ReconciliationResultDto reconcile(String providerCode, String billDate) {
        return new ReconciliationResultDto(providerCode, billDate, 0, 0, "DONE");
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
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
        BillingModeEnum modeEnum = BillingModeEnum.fromCode(mode);
        if (modeEnum == null) {
            modeEnum = BillingModeEnum.CUSTOM;
        }
        if (BillingModeEnum.TOKEN == modeEnum) {
            long inputTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS);
            long outputTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.COMPLETION_TOKENS, AiPayloadFields.OUTPUT_TOKENS);
            long totalTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.TOTAL_TOKENS);
            long cacheReadTokens = MapNumberUtils.nestedLong(usage, "prompt_tokens_details", "cached_tokens")
                    + MapNumberUtils.nestedLong(usage, "input_tokens_details", "cached_tokens");
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
        long amount = switch (modeEnum) {
            case EMBEDDING -> MapNumberUtils.firstLong(usage, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS, AiPayloadFields.TOTAL_TOKENS);
            case REQUEST -> 1L;
            case IMAGE -> MapNumberUtils.firstLong(usage, "image_count", "images", "n");
            case AUDIO -> MapNumberUtils.firstLong(usage, "duration_seconds", "audio_seconds", "seconds");
            case VIDEO -> MapNumberUtils.firstLong(usage, "duration_seconds", "video_seconds", "seconds");
            default -> MapNumberUtils.firstLong(usage, AiPayloadFields.TOTAL_TOKENS, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS);
        };
        if (amount <= 0 && (BillingModeEnum.IMAGE == modeEnum || BillingModeEnum.REQUEST == modeEnum)) {
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
        entity.setStatus(request.enabled() ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.INACTIVE.code());
    }

    private BillingRuleVersionEntity createVersion(Long ruleId, BillingRuleDto request, boolean active, String versionCode) {
        Integer maxVersionNo = billingRuleMapper.maxVersionNo(ruleId);
        BillingRuleVersionEntity version = new BillingRuleVersionEntity();
        version.setRuleId(ruleId);
        version.setVersionNo(maxVersionNo == null ? 1 : maxVersionNo + 1);
        version.setVersionCode(versionCode);
        version.setBillingMode(normalizeBillingMode(request.billingMode()));
        version.setCurrency(normalizeCurrency(request.currency()));
        version.setConfigJson(blankToNull(request.configJson()));
        version.setLadderJson(blankToNull(request.ladderJson()));
        version.setEffectiveFrom(request.effectiveFrom() == null ? LocalDateTime.now() : request.effectiveFrom());
        version.setEffectiveTo(request.effectiveTo());
        version.setStatus(active ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.DISABLED.code());
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

    private String buildVersionCode(BillingRuleDto request) {
        Map<String, Object> versionPayload = new LinkedHashMap<>();
        versionPayload.put("providerId", request.providerId());
        versionPayload.put("logicalModelId", request.logicalModelId());
        versionPayload.put("billingMode", normalizeBillingMode(request.billingMode()));
        versionPayload.put("currency", normalizeCurrency(request.currency()));
        versionPayload.put("itemType", normalizeItemType(request.itemType()));
        versionPayload.put("unit", normalizeUnit(request.unit()));
        versionPayload.put("unitPrice", normalizeDecimal(request.unitPrice()));
        versionPayload.put("configJson", normalizeJsonText(request.configJson()));
        versionPayload.put("ladderJson", normalizeJsonText(request.ladderJson()));
        try {
            return md5Hex(OBJECT_MAPPER.writeValueAsString(versionPayload));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "failed to build billing version code");
        }
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
            return BillingModeEnum.TOKEN.code();
        }
        return normalized;
    }

    private static String normalizeCurrency(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? CurrencyCodeEnum.USD.code() : normalized;
    }

    private static String normalizeItemType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? BillingItemTypeEnum.INPUT_TOKEN.code() : normalized;
    }

    private static String normalizeUnit(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? BillingUnitEnum.ONE_K_TOKENS.code() : normalized;
    }

    private static String normalizeDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (BigDecimal.ZERO.compareTo(normalized) == 0) {
            return "0";
        }
        return normalized.toPlainString();
    }

    private static Object normalizeJsonText(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return canonicalizeJsonValue(OBJECT_MAPPER.readValue(normalized, Object.class));
        } catch (JsonProcessingException e) {
            return normalized;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object canonicalizeJsonValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            List<Map.Entry<String, Object>> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                entries.add(Map.entry(String.valueOf(entry.getKey()), canonicalizeJsonValue(entry.getValue())));
            }
            entries.sort(Comparator.comparing(Map.Entry::getKey));
            Map<String, Object> sorted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : entries) {
                sorted.put(entry.getKey(), entry.getValue());
            }
            return sorted;
        }
        if (value instanceof List<?> listValue) {
            List<Object> normalized = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                normalized.add(canonicalizeJsonValue(item));
            }
            return normalized;
        }
        if (value instanceof BigDecimal decimalValue) {
            return normalizeDecimal(decimalValue);
        }
        if (value instanceof Number numberValue) {
            return normalizeDecimal(new BigDecimal(String.valueOf(numberValue)));
        }
        return value;
    }

    private static String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    private static int unitSize(String unit) {
        BillingUnitEnum unitEnum = BillingUnitEnum.fromCode(normalizeUnit(unit));
        if (unitEnum == null) {
            return 1_000;
        }
        return switch (unitEnum) {
            case ONE_M_TOKENS -> 1_000_000;
            case PER_TOKEN, PER_REQUEST, PER_IMAGE, PER_SECOND, CUSTOM -> 1;
            case PER_MINUTE -> 60;
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
