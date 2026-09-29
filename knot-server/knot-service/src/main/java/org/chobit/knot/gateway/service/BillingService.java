package org.chobit.knot.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.AiPayloadFields;
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
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.vo.billing.BillingModeCapabilityItem;
import org.chobit.knot.gateway.util.BillingPriceResolver;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.util.MapNumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    public PageResult<BillingRuleDto> listRules(PageRequest pageRequest, String keyword, String providerCode, String logicalModelCode) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<BillingRuleEntity> pageInfo = new PageInfo<>(
                    billingRuleMapper.list(normalizeKeyword(keyword), providerCode, logicalModelCode)
            );
            return PageResult.fromPage(pageInfo, list -> list.stream().map(billingConverter::toRuleDto).toList(), pageRequest);
        }
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
     * Lists billing mode capabilities: supported units and default unit per mode.
     */
    public List<BillingModeCapabilityItem> listModeCapabilities() {
        return Arrays.stream(BillingModeEnum.values())
                .map(mode -> new BillingModeCapabilityItem(
                        mode.code(),
                        mode.supportedUnitCodes(),
                        mode.defaultUnit().code()
                ))
                .toList();
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
            return JsonKit.toMap(dto);
        } catch (BusinessException e) {
            return null;
        }
    }

    /**
     * Creates a new rule and its initial version.
     */
    @Transactional
    public BillingRuleDto createRule(BillingRuleDto request) {
        validateRule(request, null);
        BillingRuleEntity e = new BillingRuleEntity();
        applyRule(e, request);
        e.setStatus(request.enabled() ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.INACTIVE.code());
        billingRuleMapper.insert(e);
        createVersion(e.getId(), request, request.enabled());
        return billingConverter.toRuleDto(billingRuleMapper.getById(e.getId()));
    }

    /**
     * Updates the rule and syncs versions: 配置指纹变化才出新版本；
     * 指纹与已有历史版本相同则复用该版本（避免重复版本，天然支持回退）。
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
        syncVersion(id, request, request.enabled());
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
        if (!enabled) {
            assertRuleNotBound(id, "billing rule is bound by provider models, cannot disable");
        }
        billingRuleMapper.updateStatus(id, enabled ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.INACTIVE.code());
        BillingRuleVersionEntity latest = billingRuleMapper.getLatestVersion(id);
        if (latest != null) {
            billingRuleMapper.updateVersionStatus(latest.getId(), statusFor(enabled));
        }
        return billingConverter.toRuleDto(billingRuleMapper.getById(id));
    }

    /**
     * Lifecycle-deletes the rule（status 置 DELETED，不做物理删除）。
     */
    @Transactional
    public void deleteRule(Long id) {
        BillingRuleEntity existing = billingRuleMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        assertRuleNotBound(id, "billing rule is bound by provider models, cannot delete");
        billingRuleMapper.deleteRule(id);
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
        detail.put("versionCode", rule.getVersionCode());
        detail.put("billingMode", rule.getBillingMode());
        detail.put("currency", rule.getCurrency());
        detail.put("unit", rule.getUnit());
        detail.put("unitSize", unitSize(rule.getUnit()));
        detail.put("usage", amount.usage());
        detail.put("totalCost", amount.totalCost());
        return detail;
    }

    private BillingAmount calculateAmount(BillingRuleEntity rule, Map<String, Object> usage) {
        String mode = normalizeBillingMode(rule.getBillingMode());
        int unitSize = unitSize(rule.getUnit());
        String configJson = rule.getConfigJson();
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
            long ladderAmount = totalTokens > 0 ? totalTokens : inputTokens + outputTokens;
            BigDecimal inputPrice = BillingPriceResolver.resolvePrice(configJson, "input", ladderAmount, BigDecimal.ZERO);
            BigDecimal outputPrice = BillingPriceResolver.resolvePrice(configJson, "output", ladderAmount, BigDecimal.ZERO);
            BigDecimal cacheReadPrice = BillingPriceResolver.resolvePrice(configJson, "cacheRead", ladderAmount, BigDecimal.ZERO);
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
        BigDecimal unitPrice = BillingPriceResolver.resolveDefaultPrice(configJson, amount, BigDecimal.ZERO);
        Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("amount", amount);
        return new BillingAmount(parts, cost(amount, unitPrice, unitSize));
    }

    private record BillingAmount(Map<String, Object> usage, BigDecimal totalCost) {
    }

    private void applyRule(BillingRuleEntity entity, BillingRuleDto request) {
        entity.setCode(normalizeCode(request.code()));
        entity.setProviderCode(request.providerCode());
        entity.setLogicalModelCode(request.logicalModelCode());
        entity.setRemark(blankToNull(request.remark()));
    }

    /** 配置指纹变化 -> 新版本；与历史版本指纹相同 -> 复用（激活并刷新生效时间）；最新版本未变 -> 仅同步状态 */
    private void syncVersion(Long ruleId, BillingRuleDto request, boolean enabled) {
        String uniqHash = buildUniqHash(request);
        BillingRuleVersionEntity latest = billingRuleMapper.getLatestVersion(ruleId);
        if (latest != null && uniqHash.equals(latest.getUniqHash())) {
            billingRuleMapper.updateVersionStatus(latest.getId(), statusFor(enabled));
            return;
        }
        BillingRuleVersionEntity same = billingRuleMapper.getVersionByHash(ruleId, uniqHash);
        if (same != null) {
            if (enabled) {
                billingRuleMapper.activateVersion(same.getId(), LocalDateTime.now());
                billingRuleMapper.disableOtherActiveVersions(ruleId, same.getId());
            } else {
                billingRuleMapper.updateVersionStatus(same.getId(), EntityStatusEnum.DISABLED.code());
            }
            return;
        }
        createVersion(ruleId, request, enabled);
    }

    private BillingRuleVersionEntity createVersion(Long ruleId, BillingRuleDto request, boolean active) {
        String versionCode = "v" + (billingRuleMapper.countVersions(ruleId) + 1);
        BillingRuleVersionEntity version = new BillingRuleVersionEntity();
        version.setRuleId(ruleId);
        version.setVersionCode(versionCode);
        version.setUniqHash(buildUniqHash(request));
        version.setBillingMode(normalizeBillingMode(request.billingMode()));
        version.setCurrency(normalizeCurrency(request.currency()));
        version.setUnit(normalizeUnit(request.unit()));
        version.setConfigJson(blankToNull(request.configJson()));
        version.setEffectiveFrom(request.effectiveFrom() == null ? LocalDateTime.now() : request.effectiveFrom());
        version.setEffectiveTo(request.effectiveTo());
        version.setStatus(active ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.DISABLED.code());
        billingRuleMapper.insertVersion(version);
        billingRuleMapper.disableOtherActiveVersions(ruleId, version.getId());
        return version;
    }

    /** 版本内容指纹：结构化计费字段 + 归一化 config_json 的 MD5 */
    private String buildUniqHash(BillingRuleDto request) {
        Map<String, Object> versionPayload = new LinkedHashMap<>();
        versionPayload.put("billingMode", normalizeBillingMode(request.billingMode()));
        versionPayload.put("currency", normalizeCurrency(request.currency()));
        versionPayload.put("unit", normalizeUnit(request.unit()));
        versionPayload.put("configJson", normalizeJsonText(request.configJson()));
        try {
            return md5Hex(OBJECT_MAPPER.writeValueAsString(versionPayload));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "failed to build billing version hash");
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
        validateConfigJson(request.configJson());
        validateModeAndUnit(request.billingMode(), request.unit());
    }

    /**
     * config_json 校验：可解析为对象；defaultUnitPrice / basePrices / ladder 价格非负；
     * ladder 结构合法且区间不重叠。
     */
    private void validateConfigJson(String configJson) {
        String normalized = blankToNull(configJson);
        if (normalized == null) {
            return;
        }
        Map<String, Object> config;
        try {
            Object parsed = OBJECT_MAPPER.readValue(normalized, Object.class);
            config = parsed instanceof Map<?, ?> map ? castToMap(map) : null;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "config_json must be a valid JSON object");
        }
        if (config == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "config_json must be a JSON object");
        }
        BigDecimal defaultUnitPrice = decimalOf(config.get("defaultUnitPrice"));
        if (defaultUnitPrice != null && defaultUnitPrice.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "config_json.defaultUnitPrice cannot be negative");
        }
        if (config.get("basePrices") != null) {
            if (!(config.get("basePrices") instanceof Map<?, ?> basePrices)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "config_json.basePrices must be an object");
            }
            for (Map.Entry<?, ?> entry : basePrices.entrySet()) {
                BigDecimal price = decimalOf(entry.getValue());
                if (price == null || price.signum() < 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "base price cannot be negative: " + entry.getKey());
                }
            }
        }
        String ladderError = BillingPriceResolver.validateLadder(config.get("ladder"));
        if (ladderError != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "config_json.ladder invalid: " + ladderError);
        }
    }

    /**
     * 校验计费模式与计费单位的组合。此前这条规则只存在于前端 unitsByMode，
     * 后端不校验，移除前端映射后必须由这里兜住，否则会写入「TOKEN + PER_IMAGE」这类组合。
     */
    private void validateModeAndUnit(String billingMode, String unit) {
        BillingModeEnum mode = BillingModeEnum.fromCode(billingMode);
        if (mode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "unsupported billing mode: " + billingMode);
        }
        if (unit == null || unit.isBlank()) {
            return;
        }
        if (!mode.supportsUnit(unit)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "billing unit " + unit + " is not supported by mode " + mode.code());
        }
    }

    private void assertRuleNotBound(Long id, String message) {
        Long count = billingRuleMapper.countBoundModels(id);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String statusFor(boolean enabled) {
        return enabled ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.DISABLED.code();
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

    private static String normalizeUnit(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? BillingUnitEnum.ONE_K_TOKENS.code() : normalized;
    }

    private static BigDecimal decimalOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
}
