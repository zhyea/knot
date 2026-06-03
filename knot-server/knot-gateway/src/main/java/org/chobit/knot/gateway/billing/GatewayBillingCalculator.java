package org.chobit.knot.gateway.billing;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.BillingUnitEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.model.BillingDetail;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.NormalizedUsageDetail;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GatewayBillingCalculator {

    private final GatewayDataService dataService;

    /**
     * Executes the public operation. Executes the public operation.
     */
    public BillingDetail calculateUsageDetail(Long modelId, BillingUsage usage) {
        if (modelId == null) {
            return null;
        }
        ModelEntity model = dataService.getModelById(modelId);
        if (model == null || model.getBillingRuleId() == null) {
            return null;
        }
        BillingRuleEntity rule = dataService.getActiveBillingRuleById(model.getBillingRuleId());
        if (rule == null) {
            return null;
        }
        BillingUsage normalizedUsage = usage == null ? BillingUsage.empty() : usage;
        BillingAmount amount = calculateAmount(rule, normalizedUsage);
        BillingUsage detailUsage = new BillingUsage(
                normalizedUsage.inputTokens(),
                normalizedUsage.outputTokens(),
                normalizedUsage.totalTokens() > 0
                        ? normalizedUsage.totalTokens()
                        : normalizedUsage.inputTokens() + normalizedUsage.outputTokens(),
                normalizedUsage.cacheReadTokens(),
                normalizedUsage.cacheWriteTokens(),
                amount.amount()
        );
        return new BillingDetail(
                modelId,
                rule.getId(),
                rule.getCode(),
                rule.getName(),
                rule.getVersionNo(),
                rule.getVersionCode(),
                rule.getBillingMode(),
                rule.getCurrency(),
                rule.getItemType(),
                rule.getUnit(),
                unitSize(rule.getUnit()),
                safeMoney(rule.getUnitPrice()),
                detailUsage,
                amount.details(),
                amount.totalCost()
        );
    }

    private BillingAmount calculateAmount(BillingRuleEntity rule, BillingUsage usage) {
        String mode = normalizeBillingMode(rule.getBillingMode());
        int unitSize = unitSize(rule.getUnit());
        BigDecimal unitPrice = safeMoney(rule.getUnitPrice());
        BillingModeEnum modeEnum = BillingModeEnum.fromCode(mode);
        if (modeEnum == null) {
            modeEnum = BillingModeEnum.CUSTOM;
        }
        if (BillingModeEnum.TOKEN == modeEnum) {
            long inputTokens = usage.inputTokens();
            long outputTokens = usage.outputTokens();
            long totalTokens = usage.totalTokens();
            long cacheReadTokens = usage.cacheReadTokens();
            long cacheWriteTokens = usage.cacheWriteTokens();
            BigDecimal inputPrice = jsonDecimal(rule.getConfigJson(), "inputUnitPrice", unitPrice);
            BigDecimal outputPrice = jsonDecimal(rule.getConfigJson(), "outputUnitPrice", unitPrice);
            BigDecimal cacheReadPrice = jsonDecimal(rule.getConfigJson(), "cacheReadUnitPrice", BigDecimal.ZERO);
            BigDecimal cacheWritePrice = jsonDecimal(rule.getConfigJson(), "cacheWriteUnitPrice", inputPrice);
            long uncachedInputTokens = Math.max(0L, inputTokens - cacheReadTokens - cacheWriteTokens);
            BigDecimal inputCost = cost(uncachedInputTokens, inputPrice, unitSize);
            BigDecimal outputCost = cost(outputTokens, outputPrice, unitSize);
            BigDecimal cacheReadCost = cost(cacheReadTokens, cacheReadPrice, unitSize);
            BigDecimal cacheWriteCost = cost(cacheWriteTokens, cacheWritePrice, unitSize);
            long billedAmount = totalTokens > 0 ? totalTokens : inputTokens + outputTokens;
            List<NormalizedUsageDetail> details = List.of(
                    detail("uncachedInput", uncachedInputTokens, inputCost, inputPrice, unitSize),
                    detail("cachedRead", cacheReadTokens, cacheReadCost, cacheReadPrice, unitSize),
                    detail("cachedWrite", cacheWriteTokens, cacheWriteCost, cacheWritePrice, unitSize),
                    detail("output", outputTokens, outputCost, outputPrice, unitSize)
            );
            return new BillingAmount(billedAmount, inputCost.add(outputCost).add(cacheReadCost).add(cacheWriteCost), details);
        }

        long amount = switch (modeEnum) {
            case EMBEDDING -> firstAmount(usage.inputTokens(), usage.totalTokens());
            case REQUEST -> 1L;
            case IMAGE, AUDIO, VIDEO -> firstAmount(usage.amount(), 1L);
            default -> firstAmount(usage.totalTokens(), usage.inputTokens());
        };
        if (amount <= 0 && (BillingModeEnum.IMAGE == modeEnum || BillingModeEnum.REQUEST == modeEnum)) {
            amount = 1L;
        }
        BigDecimal totalCost = cost(amount, unitPrice, unitSize);
        return new BillingAmount(
                amount,
                totalCost,
                List.of(detail(normalizeDetailType(rule.getItemType(), modeEnum), amount, totalCost, unitPrice, unitSize))
        );
    }

    private record BillingAmount(long amount, BigDecimal totalCost, List<NormalizedUsageDetail> details) {
    }

    private static String normalizeBillingMode(String value) {
        String normalized = StringUtils.upperCase(StringUtils.trim(value));
        return StringUtils.defaultIfBlank(normalized, BillingModeEnum.TOKEN.code());
    }

    private static String normalizeUnit(String value) {
        String normalized = StringUtils.upperCase(StringUtils.trim(value));
        return StringUtils.defaultIfBlank(normalized, BillingUnitEnum.ONE_K_TOKENS.code());
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

    private static NormalizedUsageDetail detail(String type,
                                                long tokens,
                                                BigDecimal cost,
                                                BigDecimal unitPrice,
                                                int unitSize) {
        return new NormalizedUsageDetail(
                type,
                tokens,
                safeMoney(cost),
                pricePerMillion(safeMoney(unitPrice), unitSize)
        );
    }

    private static BigDecimal pricePerMillion(BigDecimal unitPrice, int unitSize) {
        if (unitPrice == null || unitPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return unitPrice
                .multiply(BigDecimal.valueOf(1_000_000L))
                .divide(BigDecimal.valueOf(Math.max(1, unitSize)), 8, RoundingMode.HALF_UP);
    }

    private static String normalizeDetailType(String itemType, BillingModeEnum mode) {
        if (StringUtils.isNotBlank(itemType)) {
            return StringUtils.trim(itemType);
        }
        return mode == null ? BillingModeEnum.CUSTOM.code() : mode.code();
    }

    private static BigDecimal safeMoney(BigDecimal value) {
        return ObjectUtils.defaultIfNull(value, BigDecimal.ZERO);
    }

    private static long firstAmount(long primary, long fallback) {
        return primary > 0 ? primary : fallback;
    }

    private static BigDecimal jsonDecimal(String json, String key, BigDecimal fallback) {
        if (StringUtils.isBlank(json)) {
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
