package org.chobit.knot.svc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.common.constants.enums.BillingUnitEnum;
import org.chobit.knot.common.constants.enums.CurrencyCodeEnum;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.model.BillingConfig;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.common.pricing.HolidayCalendar;
import org.chobit.knot.common.pricing.PeakOffPeakResolver;
import org.chobit.knot.common.pricing.PhaseDecision;
import org.chobit.knot.svc.converter.BillingConverter;
import org.chobit.knot.svc.dto.billing.BillingRuleDto;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.dal.entity.BillingRuleVersionEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.chobit.knot.svc.service.support.AuditSnapshotSupport;
import org.chobit.knot.svc.service.support.CodeBindingSupport;
import org.chobit.knot.svc.vo.billing.BillingCapabilities;
import org.chobit.knot.svc.vo.billing.BillingReportSummary;
import org.chobit.knot.svc.vo.billing.BillingRuleListItem;
import org.chobit.knot.svc.vo.billing.PricingPreviewRequest;
import org.chobit.knot.svc.vo.billing.PricingPreviewResult;
import org.chobit.knot.common.util.JsonKit;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final BillingConverter billingConverter;

    /**
     * Constructs a new instance.
     */
    public BillingService(BillingRuleMapper billingRuleMapper, BillingConverter billingConverter) {
        this.billingRuleMapper = billingRuleMapper;
        this.billingConverter = billingConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<BillingRuleListItem> listRules(PageRequest pageRequest,
                                                     String keyword,
                                                     String modelFamilyCode,
                                                     String code,
                                                     Boolean includeDeleted) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<BillingRuleEntity> pageInfo = new PageInfo<>(
                billingRuleMapper.list(normalizeKeyword(keyword),
                    normalizeModelFamily(modelFamilyCode),
                    normalizeKeyword(code),
                    includeDeleted)
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
        BillingConfig config = rule.configJson() == null || rule.configJson().isBlank()
                ? null
                : parseConfigOrThrow(rule.configJson());
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
            unitSize(rule.unit(), modeEnum),
            occurredAt.toString(),
            config == null || config.pricing() == null ? null : config.pricing().timezone(),
            decision == null ? null : decision.phase().code(),
            decision == null ? null : decision.reason().name(),
            decision == null ? null : decision.multiplier(),
            prices
        );
    }

    /**
     * TOKEN 模式四种单价一次性算齐；未填的价格记为 0，与既有 TOKEN 计算器口径一致
     */
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

    /**
     * ISO-8601 时点解析：支持 {@code 2026-09-28T02:30:00Z} 与带偏移写法；空值取当前时间
     */
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

    private boolean isActive(Integer status) {
        return EnabledStatusEnum.isEnabled(status);
    }

    private List<BillingReportSummary.CodeCount> toCodeCounts(Map<String, Long> map) {
        return map.entrySet().stream()
            .map(entry -> new BillingReportSummary.CodeCount(
                entry.getKey().isEmpty() ? null : entry.getKey(), entry.getValue()))
            .toList();
    }

    /**
     * 操作日志用的计费规则快照：{@code @OperationLog} 的 SpEL 在方法前后各取一次，
     * 规则不存在时返回 {@code null}（重复删除不改写业务结果）。
     */
    public Map<String, Object> billingRuleAuditSnapshot(Long id) {
        return AuditSnapshotSupport.capture(id, key -> JsonKit.toMap(getRuleById(key)));
    }

    /**
     * Creates a new rule and its initial version.
     */
    @Transactional
    public BillingRuleDto createRule(BillingRuleDto request) {
        validateRule(request, null);
        BillingRuleEntity e = new BillingRuleEntity();
        applyRule(e, request);
        e.setStatus(EnabledStatusEnum.codeOf(request.enabled()));
        billingRuleMapper.insert(e);
        createVersion(e.getId(), request, request.enabled());
        return billingConverter.toRuleDto(billingRuleMapper.getById(e.getId()));
    }

    /**
     * Updates the rule and syncs its single version in place: 原地覆盖配置，
     * 不生成新版本（版本仅占位预留，无实际迭代语义；见 MEMORY.md §七）。
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
        existing.setStatus(EnabledStatusEnum.codeOf(request.enabled()));
        // 版本按规则主键归属，改码不影响版本行：此处用 id 同步，不存在「按新码查不到旧版本」的失配
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
        billingRuleMapper.updateStatus(id, EnabledStatusEnum.codeOf(enabled));
        BillingRuleVersionEntity latest = billingRuleMapper.getLatestVersion(id);
        if (latest != null) {
            billingRuleMapper.updateVersionStatus(latest.getId(), statusFor(enabled));
        }
        return billingConverter.toRuleDto(billingRuleMapper.getById(id));
    }

    /**
     * Lifecycle-deletes the rule（{@code is_deleted = 1} 且 {@code status = 0}，不做物理删除）。
     *
     * <p>删时强制停用：避免恢复后立刻参与计费。恢复后是否启用由调用方显式调
     * {@code PUT /rules/{id}/status} 决定。</p>
     */
    @Transactional
    public void deleteRule(Long id) {
        BillingRuleEntity existing = billingRuleMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        assertRuleNotBound(existing.getCode(), "billing rule is bound by provider models, cannot delete");
        billingRuleMapper.logicalDelete(id);
    }

    /**
     * 恢复已逻辑删除的计费规则：{@code is_deleted = 0}，{@code status} 保持 0（不自动生效）。
     *
     * <p>code 唯一性按物理行判定（uk_billing_rules_code 不区分 is_deleted），所以逻辑删除后同
     * {@code code} 无法新建，只能恢复。</p>
     */
    @Transactional
    public BillingRuleDto restoreRule(Long id) {
        BillingRuleEntity existing = billingRuleMapper.getByIdIncludingDeleted(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        if (!Integer.valueOf(1).equals(existing.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONFLICT, "billing rule is not deleted, restore skipped");
        }
        if (billingRuleMapper.restore(id) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "billing rule restore failed, refresh and retry");
        }
        return getRuleById(id);
    }

    private void applyRule(BillingRuleEntity entity, BillingRuleDto request) {
        entity.setCode(normalizeCode(request.code()));
        entity.setModelFamilyCode(normalizeModelFamily(request.modelFamilyCode()));
        entity.setRemark(blankToNull(request.remark()));
    }

    /**
     * 更新规则时同步其唯一版本：原地覆盖配置内容（计费字段 + config_json + 生效期 + 状态），
     * 不生成新版本、不复用历史版本。版本仅占位预留，无实际迭代语义（见 MEMORY.md §七）。
     *
     * <p>按 {@code ruleId} 定位版本行（不按 code）：版本是主体的从属明细，
     * code 改动不该让版本查询落空、更不该在改码时另起一条版本行。</p>
     */
    private void syncVersion(Long ruleId, BillingRuleDto request, boolean enabled) {
        BillingRuleVersionEntity latest = billingRuleMapper.getLatestVersion(ruleId);
        if (latest == null) {
            createVersion(ruleId, request, enabled);
            return;
        }
        latest.setUniqHash(buildUniqHash(request));
        latest.setBillingMode(normalizeBillingMode(request.billingMode()));
        latest.setPricingPlan(normalizePricingPlan(request.pricingPlan()));
        latest.setCurrency(normalizeCurrency(request.currency()));
        latest.setUnit(normalizeUnit(request.unit(), modeEnumOf(normalizeBillingMode(request.billingMode()))));
        latest.setConfigJson(blankToNull(request.configJson()));
        latest.setEffectiveFrom(request.effectiveFrom() == null ? LocalDateTime.now() : request.effectiveFrom());
        latest.setEffectiveTo(request.effectiveTo());
        latest.setStatus(statusFor(enabled));
        billingRuleMapper.updateVersionContent(latest);
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
        version.setUnit(normalizeUnit(request.unit(), modeEnumOf(normalizeBillingMode(request.billingMode()))));
        version.setConfigJson(blankToNull(request.configJson()));
        version.setEffectiveFrom(request.effectiveFrom() == null ? LocalDateTime.now() : request.effectiveFrom());
        version.setEffectiveTo(request.effectiveTo());
        version.setStatus(EnabledStatusEnum.codeOf(active));
        billingRuleMapper.insertVersion(version);
        return version;
    }

    /** 版本内容指纹：结构化计费字段（含 pricingPlan）+ 归一化 config_json 的 MD5 */
    /**
     * 包内可见：便于同包测试直接验证指纹兼容性与字段作用域。
     */
    String buildUniqHash(BillingRuleDto request) {
        BillingRuleDto normalized = request.withNormalizedPricing(
            normalizeBillingMode(request.billingMode()),
            normalizePricingPlan(request.pricingPlan()),
            normalizeCurrency(request.currency()),
            normalizeUnit(request.unit(), modeEnumOf(normalizeBillingMode(request.billingMode()))));
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
        CurrencyCodeEnum.requireCode(normalizeCurrency(request.currency()),
            "unsupported currency: " + request.currency());
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
        String normalizedUnit = BillingUnitEnum.requireCode(unit, "unsupported billing unit: " + unit);
        if (!mode.supportsUnit(normalizedUnit)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "billing unit " + normalizedUnit + " is not supported by mode " + mode.code());
        }
    }

    private void assertRuleNotBound(String code, String message) {
        Long count = billingRuleMapper.countBoundModels(code);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }

    /**
     * 规则编码可改，但原编码一旦被引用就不许改：模型绑定存的是 code，改码会让存量模型掉绑。
     *
     * <p><b>版本行不是引用方</b>：{@code kb_billing_rule_versions} 按 {@code rule_id}（规则主键）
     * 归属主体，与 code 无关。此前按 code 关联，导致每条规则恒有版本行 → 改码守卫恒判「已引用」，
     * 规则编码实际永远改不了；且改码后 {@code syncVersion} 按新码查不到旧版本行，会另起一条
     * {@code v1} 并把旧行留成孤儿（旧码永久占用）。改为按主键绑定后这两个问题一并消失。</p>
     *
     * <p>唯一性由 {@link #validateRule} 的 countByCode 保证。</p>
     */
    private void assertCodeChangeAllowed(BillingRuleEntity existing, String newCode) {
        String oldCode = normalizeCode(existing.getCode());
        if (CodeBindingSupport.isSameCode(oldCode, newCode)) {
            return;
        }
        Long models = billingRuleMapper.countBoundModels(oldCode);
        CodeBindingSupport.requireCodeChangeAllowed(oldCode, newCode, models, "计费规则编码");
    }

    private static Integer statusFor(boolean enabled) {
        return EnabledStatusEnum.codeOf(enabled);
    }

    /**
     * 规则编码归一：仅去空白，不再强制大写（编码大小写由用户自定，唯一性按原值校验）
     */
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

    /** 预览同样不许把非法配置静默映射为零价（60-business-rules §8）。 */
    private static BillingConfig parseConfigOrThrow(String json) {
        try {
            return BillingConfig.parse(json);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "计费规则 config_json 非法: " + e.getMessage());
        }
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

    /**
     * 进阶定价方案归一：空值默认 FIXED（调用前已由 validateRule 保证非空合法）
     */
    private static String normalizePricingPlan(String value) {
        PricingPlanEnum plan = PricingPlanEnum.fromCode(value);
        return plan == null ? PricingPlanEnum.FIXED.code() : plan.code();
    }

    /**
     * 计费单位归一化：空白 / 非法编码 / 该模式不支持时，取模式的默认单位。
     *
     * <p><b>必须带模式</b>：单位基数是金额公式的分母，{@code PER_SECOND}(1) 与
     * {@code 1K_TOKENS}(1000) 差 1000 倍。此前这里不分模式一律兜底成 {@code 1K_TOKENS}，
     * 于是「VIDEO 规则忘了给 unit」会落成每秒价再除 1000（少收 1000 倍），
     * 「TOKEN 规则忘了给 unit」也拿不到 TOKEN 模式声明的 {@code 1M_TOKENS}。
     * 口径在 {@link BillingModeEnum#normalizeUnit(String)} 里只有一份，
     * 与管理端表单、与网关计算器的热路径同源。</p>
     */
    private static String normalizeUnit(String value, BillingModeEnum mode) {
        return mode.normalizeUnit(value);
    }

    /**
     * 模式枚举：未知编码按 {@link BillingModeEnum#CUSTOM} 处理（与预览链路同口径）
     */
    private static BillingModeEnum modeEnumOf(String billingMode) {
        BillingModeEnum mode = BillingModeEnum.fromCode(billingMode);
        return mode == null ? BillingModeEnum.CUSTOM : mode;
    }

    /**
     * 模型族 code 归一：小写；空值返回 null 表示默认规则（覆盖所有族）
     */
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

    private static int unitSize(String unit, BillingModeEnum mode) {
        return BillingUnitEnum.unitSizeOf(mode.normalizeUnit(unit));
    }

}
