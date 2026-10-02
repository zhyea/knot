package org.chobit.knot.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.BillingUnitEnum;
import org.chobit.knot.gateway.constants.enums.CurrencyCodeEnum;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.model.BillingConfig;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.pricing.HolidayCalendar;
import org.chobit.knot.gateway.pricing.PeakOffPeakResolver;
import org.chobit.knot.gateway.pricing.PhaseDecision;
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
import org.chobit.knot.gateway.vo.billing.BillingCapabilities;
import org.chobit.knot.gateway.vo.billing.BillingReportSummary;
import org.chobit.knot.gateway.vo.billing.BillingRuleListItem;
import org.chobit.knot.gateway.vo.billing.PricingPreviewRequest;
import org.chobit.knot.gateway.vo.billing.PricingPreviewResult;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.util.MapNumberUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BillingService {

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
    public PageResult<BillingRuleListItem> listRules(PageRequest pageRequest, String keyword, String modelFamilyCode) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<BillingRuleEntity> pageInfo = new PageInfo<>(
                    billingRuleMapper.list(normalizeKeyword(keyword), normalizeModelFamily(modelFamilyCode))
            );
            return PageResult.fromPage(pageInfo, list -> list.stream().map(billingConverter::toRuleListItem).toList(), pageRequest);
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
     * 方案试算：给定规则与时点，**复用** {@code PeakOffPeakResolver} 算出相位与最终单价。
     *
     * <p>用途有两个：管理端配置页的「时间判定测试」，以及排障时确认某个时刻实际按多少收费。
     * 因此这里不能用另一套判定实现兜底 —— 判定错了就说明线上错了。
     *
     * <p>日历来源：首期为 {@code HolidayCalendar.EMPTY}（不建表），即节假日/调休一律不命中，
     * 判定结果只反映「星期 + 时段」；接入真实日历时改这一个入参即可。
     */
    public PricingPreviewResult previewPricing(Long ruleId, PricingPreviewRequest request) {
        BillingRuleDto rule = getRuleById(ruleId);
        Instant occurredAt = parseInstant(request == null ? null : request.occurredAt());
        long amount = request == null || request.usageAmount() == null || request.usageAmount() < 0
                ? 0L
                : request.usageAmount();
        String mode = normalizeBillingMode(rule.billingMode());
        BillingModeEnum modeEnum = BillingModeEnum.fromCode(mode);
        if (modeEnum == null) {
            modeEnum = BillingModeEnum.CUSTOM;
        }
        PricingPlanEnum plan = PricingPlanEnum.fromCode(rule.pricingPlan());
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.configJson());
        BillingConfig.PricingContext context = new BillingConfig.PricingContext(amount, occurredAt);
        BigDecimal zero = BigDecimal.ZERO;

        PhaseDecision decision = null;
        Map<String, BigDecimal> prices;
        if (config == null) {
            prices = BillingModeEnum.TOKEN == modeEnum ? tokenPricesOf(null, context, zero) : Map.of("default", zero);
        } else {
            BillingConfig.PricingPlan pricingPlan = config.pricingPlan(plan, HolidayCalendar.EMPTY);
            if (PricingPlanEnum.PEAK_OFF_PEAK == plan) {
                decision = PeakOffPeakResolver.resolve(config.pricing(), occurredAt, HolidayCalendar.EMPTY);
            }
            prices = BillingModeEnum.TOKEN == modeEnum
                    ? tokenPricesOf(pricingPlan, context, zero)
                    : Map.of("default", pricingPlan.resolveDefaultPrice(context, zero));
        }
        return new PricingPreviewResult(
                rule.id(),
                rule.code(),
                modeEnum.code(),
                plan == null ? PricingPlanEnum.FIXED.code() : plan.code(),
                rule.currency(),
                rule.unit(),
                unitSize(rule.unit()),
                occurredAt.toString(),
                config == null || config.pricing() == null ? null : config.pricing().timezone(),
                decision == null ? null : decision.phase().code(),
                decision == null ? null : decision.reason().name(),
                decision == null ? null : decision.multiplier(),
                prices
        );
    }

    /** TOKEN 模式四种单价一次性算齐；未填的价格记为 0，与既有 TOKEN 计算器口径一致 */
    private Map<String, BigDecimal> tokenPricesOf(BillingConfig.PricingPlan pricingPlan,
                                                  BillingConfig.PricingContext context,
                                                  BigDecimal zero) {
        LinkedHashMap<String, BigDecimal> prices = new LinkedHashMap<>();
        for (BillingConfig.PriceKind kind : BillingConfig.PriceKind.values()) {
            BigDecimal price = pricingPlan == null ? zero : pricingPlan.resolvePrice(kind, context, zero);
            prices.put(kind.name().toLowerCase(Locale.ROOT), price);
        }
        return prices;
    }

    /** ISO-8601 时点解析：支持 {@code 2026-09-28T02:30:00Z} 与带偏移写法；空值取当前时间 */
    private Instant parseInstant(String text) {
        String value = StringUtils.trimToNull(text);
        if (value == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // 允许带偏移的写法（如 2026-09-28T10:30:00+08:00）
            OffsetDateTime offset = OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return offset.toInstant();
        }
    }

    /**
     * 计费能力矩阵：模式 -> 单位/默认单位/支持的进阶方案；方案列表（仅已开放的）。
     *
     * <p>方案的下发口径即 {@link PricingPlanEnum#supports}，与保存端 {@link #validateRule} 同源，
     * 因此「某模式能看到哪些方案」在前后端不会走偏。
     */
    public BillingCapabilities listModeCapabilities() {
        List<BillingCapabilities.BillingModeCapability> modes = Arrays.stream(BillingModeEnum.values())
                .map(mode -> new BillingCapabilities.BillingModeCapability(
                        mode.code(),
                        mode.supportedUnitCodes(),
                        mode.defaultUnit().code(),
                        Arrays.stream(PricingPlanEnum.values())
                                .filter(PricingPlanEnum::isAvailable)
                                .filter(plan -> plan.supports(mode))
                                .map(PricingPlanEnum::code)
                                .toList()
                ))
                .toList();
        List<BillingCapabilities.PricingPlanCapability> plans = Arrays.stream(PricingPlanEnum.values())
                .filter(PricingPlanEnum::isAvailable)
                .map(plan -> new BillingCapabilities.PricingPlanCapability(plan.code()))
                .toList();
        return new BillingCapabilities(modes, plans);
    }

    /**
     * 计费报表汇总（配置维度）：规则状态计数 + 模型族/计费模式/进阶方案/币种分布。
     * 按「当前版本」口径聚合，数据量小，一次取全量后内存分组。
     */
    public BillingReportSummary getReportSummary() {
        List<BillingRuleEntity> rules = billingRuleMapper.listForReport();

        long activeRules = rules.stream().filter(r -> isActive(r.getStatus())).count();
        long activeVersionRules = rules.stream()
                .filter(r -> r.getVersionCode() != null && isActive(r.getVersionStatus()))
                .count();

        Map<String, BillingReportSummary.ModelFamilyDistribution> familyMap = new LinkedHashMap<>();
        Map<String, Long> modeMap = new LinkedHashMap<>();
        Map<String, Long> planMap = new LinkedHashMap<>();
        Map<String, Long> currencyMap = new LinkedHashMap<>();

        for (BillingRuleEntity rule : rules) {
            boolean active = isActive(rule.getStatus());

            String familyKey = rule.getModelFamilyCode() == null ? "" : rule.getModelFamilyCode();
            familyMap.compute(familyKey, (key, dist) -> {
                if (dist == null) {
                    return new BillingReportSummary.ModelFamilyDistribution(
                            rule.getModelFamilyCode(), rule.getModelFamilyName(), 1L, active ? 1L : 0L);
                }
                return new BillingReportSummary.ModelFamilyDistribution(
                        dist.modelFamilyCode(), dist.modelFamilyName(), dist.ruleCount() + 1,
                        dist.activeCount() + (active ? 1L : 0L));
            });

            modeMap.merge(rule.getBillingMode() == null ? "" : rule.getBillingMode(), 1L, Long::sum);
            planMap.merge(rule.getPricingPlan() == null ? "" : rule.getPricingPlan(), 1L, Long::sum);
            currencyMap.merge(rule.getCurrency() == null ? "" : rule.getCurrency(), 1L, Long::sum);
        }

        long modelCount = familyMap.keySet().stream().filter(key -> !key.isEmpty()).count();

        return new BillingReportSummary(
                rules.size(),
                activeRules,
                rules.size() - activeRules,
                activeVersionRules,
                modelCount,
                List.copyOf(familyMap.values()),
                toCodeCounts(modeMap),
                toCodeCounts(planMap),
                toCodeCounts(currencyMap)
        );
    }

    private boolean isActive(String status) {
        return "ACTIVE".equals(status);
    }

    private List<BillingReportSummary.CodeCount> toCodeCounts(Map<String, Long> map) {
        return map.entrySet().stream()
                .map(entry -> new BillingReportSummary.CodeCount(
                        entry.getKey().isEmpty() ? null : entry.getKey(), entry.getValue()))
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
        assertCodeChangeAllowed(existing, normalizeCode(request.code()));
        if (!request.enabled()) {
            assertRuleNotBound(existing.getCode(), "billing rule is bound by provider models, cannot disable");
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
            assertRuleNotBound(existing.getCode(), "billing rule is bound by provider models, cannot disable");
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
        assertRuleNotBound(existing.getCode(), "billing rule is bound by provider models, cannot delete");
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
        if (model == null || model.getBillingRuleCode() == null) {
            return null;
        }
        BillingRuleEntity rule = billingRuleMapper.getActiveByRuleCode(model.getBillingRuleCode(), LocalDateTime.now());
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
        int unitSize = unitSize(rule.getUnit());
        BillingModeEnum modeEnum = BillingModeEnum.fromCode(normalizeBillingMode(rule.getBillingMode()));
        if (modeEnum == null) {
            modeEnum = BillingModeEnum.CUSTOM;
        }
        if (BillingModeEnum.TOKEN == modeEnum) {
            return calculateTokenAmount(rule, usage, unitSize);
        }
        return calculateSimpleAmount(rule, usage, unitSize, modeEnum);
    }

    /** TOKEN 计费：input / output / cacheRead / cacheWrite 四类单价分别计费后汇总。 */
    private BillingAmount calculateTokenAmount(BillingRuleEntity rule, Map<String, Object> usage, int unitSize) {
        long inputTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS);
        long outputTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.COMPLETION_TOKENS, AiPayloadFields.OUTPUT_TOKENS);
        long totalTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.TOTAL_TOKENS);
        long cacheReadTokens = MapNumberUtils.nestedLong(usage, "prompt_tokens_details", "cached_tokens")
                + MapNumberUtils.nestedLong(usage, "input_tokens_details", "cached_tokens");
        long cacheWriteTokens = MapNumberUtils.nestedLong(usage, "prompt_tokens_details", "cache_creation_input_tokens")
                + MapNumberUtils.nestedLong(usage, "input_tokens_details", "cache_creation_input_tokens")
                + MapNumberUtils.firstLong(usage, "cache_creation_input_tokens", "cached_write_tokens",
                "cache_write_input_tokens", "cache_write_tokens");
        long ladderAmount = totalTokens > 0 ? totalTokens : inputTokens + outputTokens;
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.getConfigJson());
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal inputPrice;
        BigDecimal outputPrice;
        BigDecimal cacheReadPrice;
        BigDecimal cacheWritePrice;
        if (config == null) {
            inputPrice = zero;
            outputPrice = zero;
            cacheReadPrice = zero;
            cacheWritePrice = zero;
        } else {
            BillingConfig.PricingPlan pricing = config.pricingPlan(PricingPlanEnum.fromCode(rule.getPricingPlan()));
            inputPrice = pricing.resolvePrice(BillingConfig.PriceKind.INPUT, ladderAmount, zero);
            outputPrice = pricing.resolvePrice(BillingConfig.PriceKind.OUTPUT, ladderAmount, zero);
            cacheReadPrice = pricing.resolvePrice(BillingConfig.PriceKind.CACHE_READ, ladderAmount, zero);
            cacheWritePrice = pricing.resolvePrice(BillingConfig.PriceKind.CACHE_WRITE, ladderAmount, zero);
        }
        TokenBillingParts parts = new TokenBillingParts(
                inputTokens,
                outputTokens,
                totalTokens > 0 ? totalTokens : inputTokens + outputTokens,
                cacheReadTokens,
                cacheWriteTokens,
                cost(TokenBillingParts.uncachedInput(inputTokens, cacheReadTokens, cacheWriteTokens),
                        inputPrice, unitSize),
                cost(outputTokens, outputPrice, unitSize),
                cost(cacheReadTokens, cacheReadPrice, unitSize),
                cost(cacheWriteTokens, cacheWritePrice, unitSize));
        return new BillingAmount(parts.toUsageMap(), parts.totalCost());
    }

    /** 非 TOKEN 计费（EMBEDDING / REQUEST / IMAGE / AUDIO / VIDEO 等）：按模式取用量，套默认单价计费。 */
    private BillingAmount calculateSimpleAmount(BillingRuleEntity rule, Map<String, Object> usage, int unitSize, BillingModeEnum modeEnum) {
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
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.getConfigJson());
        BigDecimal unitPrice = config == null
                ? BigDecimal.ZERO
                : config.pricingPlan(PricingPlanEnum.fromCode(rule.getPricingPlan())).resolveDefaultPrice(BigDecimal.ZERO);
        Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("amount", amount);
        return new BillingAmount(parts, cost(amount, unitPrice, unitSize));
    }

    private record BillingAmount(Map<String, Object> usage, BigDecimal totalCost) {
    }

    /**
     * TOKEN 计费明细：输入 / 输出 / 缓存读 / 缓存写四类 token 与对应金额。
     * 字段固定，用具名类型代替 Map + 字符串键；数量一律 {@code long}，金额一律 {@code BigDecimal}。
     * 只在结果边界转成 Map，以兼容既有的对外 JSON 字段名。
     */
    private static final class TokenBillingParts {
        private final long inputTokens;
        private final long outputTokens;
        private final long totalTokens;
        private final long cacheReadTokens;
        private final long cacheWriteTokens;
        private final BigDecimal inputCost;
        private final BigDecimal outputCost;
        private final BigDecimal cacheReadCost;
        private final BigDecimal cacheWriteCost;

        private TokenBillingParts(long inputTokens,
                                  long outputTokens,
                                  long totalTokens,
                                  long cacheReadTokens,
                                  long cacheWriteTokens,
                                  BigDecimal inputCost,
                                  BigDecimal outputCost,
                                  BigDecimal cacheReadCost,
                                  BigDecimal cacheWriteCost) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
            this.cacheReadTokens = cacheReadTokens;
            this.cacheWriteTokens = cacheWriteTokens;
            this.inputCost = inputCost;
            this.outputCost = outputCost;
            this.cacheReadCost = cacheReadCost;
            this.cacheWriteCost = cacheWriteCost;
        }

        /** 可按普通输入单价计费的 token：扣除缓存读与缓存写，且不为负。 */
        private static long uncachedInput(long inputTokens, long cacheReadTokens, long cacheWriteTokens) {
            return Math.max(0L, inputTokens - cacheReadTokens - cacheWriteTokens);
        }

        private BigDecimal totalCost() {
            return inputCost.add(outputCost).add(cacheReadCost).add(cacheWriteCost);
        }

        /** 结果边界：转成既有 JSON 字段名，保持对外形状稳定。 */
        private Map<String, Object> toUsageMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("inputTokens", inputTokens);
            map.put("outputTokens", outputTokens);
            map.put("totalTokens", totalTokens);
            map.put("cacheReadTokens", cacheReadTokens);
            map.put("cacheWriteTokens", cacheWriteTokens);
            map.put("inputCost", inputCost);
            map.put("outputCost", outputCost);
            map.put("cacheReadCost", cacheReadCost);
            map.put("cacheWriteCost", cacheWriteCost);
            return map;
        }
    }

    private void applyRule(BillingRuleEntity entity, BillingRuleDto request) {
        entity.setCode(normalizeCode(request.code()));
        entity.setModelFamilyCode(normalizeModelFamily(request.modelFamilyCode()));
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
        String versionCode = "v" + (billingRuleMapper.maxVersionSeq(ruleId) + 1);
        BillingRuleVersionEntity version = new BillingRuleVersionEntity();
        version.setRuleId(ruleId);
        version.setVersionCode(versionCode);
        version.setUniqHash(buildUniqHash(request));
        version.setBillingMode(normalizeBillingMode(request.billingMode()));
        version.setPricingPlan(normalizePricingPlan(request.pricingPlan()));
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

    /** 版本内容指纹：结构化计费字段（含 pricingPlan）+ 归一化 config_json 的 MD5 */
    /** 包内可见：便于同包测试直接验证指纹兼容性与字段作用域。 */
    String buildUniqHash(BillingRuleDto request) {
        BillingRuleDto normalized = request.withNormalizedPricing(
                normalizeBillingMode(request.billingMode()),
                normalizePricingPlan(request.pricingPlan()),
                normalizeCurrency(request.currency()),
                normalizeUnit(request.unit()));
        BillingVersionFingerprint fingerprint = new BillingVersionFingerprint(
                normalized.billingMode(),
                normalized.pricingPlan(),
                normalized.currency(),
                normalized.unit(),
                normalizeJsonText(normalized.configJson()));
        try {
            return md5Hex(JsonKit.toJsonOrThrow(fingerprint));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "failed to build billing version hash");
        }
    }

    /**
     * 版本内容指纹投影：只含参与版本判断的 5 个字段，字段名由 Java 成员决定，不再依赖魔数字符串键。
     * configJson 保持规范化后的 JSON 值语义（对象 / 数组 / 数字 / null），不能先转成 JSON 字符串，
     * 否则序列化结果会从 JSON 对象变成带引号的字符串，破坏既有 MD5。
     */
    private record BillingVersionFingerprint(String billingMode,
                                             String pricingPlan,
                                             String currency,
                                             String unit,
                                             Object configJson) {
    }

    private void validateRule(BillingRuleDto request, Long excludeId) {
        String code = normalizeCode(request.code());
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "please input billing rule code");
        }
        if (billingRuleMapper.countByCode(code, excludeId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "billing rule code already exists");
        }
        PricingPlanEnum plan = PricingPlanEnum.fromCode(request.pricingPlan());
        if (plan == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "unsupported pricing plan: " + request.pricingPlan());
        }
        validateModeAndUnit(request.billingMode(), request.unit());
        BillingModeEnum mode = BillingModeEnum.fromCode(request.billingMode());
        if (!plan.supports(mode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "pricing plan " + plan.code() + " is not supported by mode " + request.billingMode());
        }
        validateConfigJson(request.configJson(), plan);
    }

    /**
     * config_json 校验：可解析为对象；基础结构合法；并按 pricingPlan 追加方案级约束
     * （TIERED 要求 tier 非空且每项 unitPrices 完整；PEAK_OFF_PEAK 要求 pricing 结构合法）。
     */
    private void validateConfigJson(String configJson, PricingPlanEnum plan) {
        String normalized = blankToNull(configJson);
        if (normalized == null) {
            return;
        }
        BillingConfig config;
        try {
            config = BillingConfig.parse(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "config_json must be a valid JSON object");
        }
        String error = config == null ? null : config.validate(plan);
        if (error != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "config_json invalid: " + error);
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

    private void assertRuleNotBound(String code, String message) {
        Long count = billingRuleMapper.countBoundModels(code);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }

    /**
     * 规则编码可改，但原编码一旦被供应商模型绑定就不许改：绑定存的是 code，改了会让存量模型掉绑。
     * 未绑定（或编码未变）时放行；唯一性由 {@link #validateRule} 的 countByCode 保证。
     */
    private void assertCodeChangeAllowed(BillingRuleEntity existing, String newCode) {
        String oldCode = normalizeCode(existing.getCode());
        if (oldCode.equals(newCode)) {
            return;
        }
        Long count = billingRuleMapper.countBoundModels(oldCode);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "billing rule code is used by " + count + " provider models, cannot change");
        }
    }

    private static String statusFor(boolean enabled) {
        return enabled ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.DISABLED.code();
    }

    /** 规则编码归一：仅去空白，不再强制大写（编码大小写由用户自定，唯一性按原值校验） */
    private static String normalizeCode(String value) {
        return value == null ? "" : value.trim();
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

    /** 进阶定价方案归一：空值默认 FIXED（调用前已由 validateRule 保证非空合法） */
    private static String normalizePricingPlan(String value) {
        PricingPlanEnum plan = PricingPlanEnum.fromCode(value);
        return plan == null ? PricingPlanEnum.FIXED.code() : plan.code();
    }

    private static String normalizeUnit(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? BillingUnitEnum.ONE_K_TOKENS.code() : normalized;
    }

    /** 模型族 code 归一：小写；空值返回 null 表示默认规则（覆盖所有族） */
    private static String normalizeModelFamily(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Object normalizeJsonText(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        Object parsed = JsonKit.fromJson(normalized, Object.class);
        return parsed != null ? canonicalizeJsonValue(parsed) : normalized;
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
