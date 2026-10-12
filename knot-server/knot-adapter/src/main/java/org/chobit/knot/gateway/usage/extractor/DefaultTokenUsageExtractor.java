package org.chobit.knot.gateway.usage.extractor;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.SimpleBillingModeCalculator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
@Order(0)
public class DefaultTokenUsageExtractor implements UsageExtractor<TokenBillingUsage> {

    public static final String CODE = "DEFAULT";
    private final SimpleBillingModeCalculator calculator;

    public DefaultTokenUsageExtractor(SimpleBillingModeCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Default Usage Extractor";
    }

    @Override
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        // 通用兜底：Simple 计算器按规则模式分摄取量（token/请求/媒体计量全走 switch），全部模式均可服务
        return EnumSet.allOf(BillingModeEnum.class);
    }

    @Override
    public TokenBillingUsage empty() {
        return TokenBillingUsage.empty();
    }

    @Override
    public TokenBillingUsage extractUsage(Map<String, Object> body, Map<String, Object> requestBody) {
        return TokenBillingUsage.fromResponse(body);
    }
}
