package org.chobit.knot.gateway.service;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.BillingUnitEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.model.BillingDetail;
import org.chobit.knot.gateway.model.BillingUsage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class GatewayBillingService {

    private final GatewayDataCache dataCache;

    /**
     * Executes the public operation. Executes the public operation.
     */
    public BillingDetail calculateUsageDetail(Long modelId, BillingUsage usage) {
        if (modelId == null) {
            return null;
        }
        ModelEntity model = dataCache.getModelById(modelId);
        if (model == null || model.getBillingRuleId() == null) {
            return null;
        }
        BillingRuleEntity rule = dataCache.getActiveBillingRuleById(model.getBillingRuleId());
        if (rule == null) {
            return null;
        }
        BillingUsage normalizedUsage = usage == null ? new BillingUsage(0L, 0L, 0L, 0L, 0L) : usage;
        BillingAmount amount = calculateAmount(rule, normalizedUsage);
        BillingUsage detailUsage = new BillingUsage(
                normalizedUsage.inputTokens(),
                normalizedUsage.outputTokens(),
                normalizedUsage.totalTokens() > 0
                        ? normalizedUsage.totalTokens()
                        : normalizedUsage.inputTokens() + normalizedUsage.outputTokens(),
                normalizedUsage.cacheReadTokens(),
                amount.amount()
        );
        return new BillingDetail(
                modelId,
                rule.getId(),
                rule.getCode(),
                rule.getName(),
                rule.getVersionNo(),
                rule.getBillingMode(),
                rule.getCurrency(),
                rule.getItemType(),
                rule.getUnit(),
                unitSize(rule.getUnit()),
                safeMoney(rule.getUnitPrice()),
                detailUsage,
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
            BigDecimal inputPrice = jsonDecimal(rule.getConfigJson(), "inputUnitPrice", unitPrice);
            BigDecimal outputPrice = jsonDecimal(rule.getConfigJson(), "outputUnitPrice", unitPrice);
            BigDecimal cacheReadPrice = jsonDecimal(rule.getConfigJson(), "cacheReadUnitPrice", BigDecimal.ZERO);
            BigDecimal inputCost = cost(inputTokens - cacheReadTokens, inputPrice, unitSize);
            BigDecimal outputCost = cost(outputTokens, outputPrice, unitSize);
            BigDecimal cacheReadCost = cost(cacheReadTokens, cacheReadPrice, unitSize);
            long billedAmount = totalTokens > 0 ? totalTokens : inputTokens + outputTokens;
            return new BillingAmount(billedAmount, inputCost.add(outputCost).add(cacheReadCost));
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
        return new BillingAmount(amount, cost(amount, unitPrice, unitSize));
    }

    private record BillingAmount(long amount, BigDecimal totalCost) {
    }

    private static String normalizeBillingMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? BillingModeEnum.TOKEN.code() : normalized;
    }

    private static String normalizeUnit(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? BillingUnitEnum.ONE_K_TOKENS.code() : normalized;
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

    private static long firstAmount(long primary, long fallback) {
        return primary > 0 ? primary : fallback;
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
