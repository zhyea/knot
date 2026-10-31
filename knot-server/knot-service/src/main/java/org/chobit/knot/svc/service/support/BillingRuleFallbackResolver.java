package org.chobit.knot.svc.service.support;

import org.chobit.knot.svc.dto.routing.BillingRuleSource;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 计费规则三级回退解析（复用生产链路口径，见 {@code GatewayConfigCacheService.getActiveBillingRule}）：
 * <ol>
 *   <li>EXPLICIT —— 模型显式绑定的规则码（{@code getActiveByRuleCode}）；</li>
 *   <li>FAMILY —— 模型族精确匹配默认规则（{@code getActiveByModelFamily(family)}）；</li>
 *   <li>GLOBAL —— 模型族为空的全局默认规则（同一查询在 family 为空时只剩此分支）；</li>
 *   <li>MISSING —— 三级均无。</li>
 * </ol>
 *
 * <p>与网关唯一差异：本解析在管理端离线发生，不参与响应热路径；命中层级被显式判定并透传给自检层。</p>
 */
@Component
public class BillingRuleFallbackResolver {

    private final BillingRuleMapper billingRuleMapper;

    public BillingRuleFallbackResolver(BillingRuleMapper billingRuleMapper) {
        this.billingRuleMapper = billingRuleMapper;
    }

    public BillingRuleResolution resolve(String ruleCode, String modelFamilyCode) {
        LocalDateTime now = LocalDateTime.now();
        if (ruleCode != null) {
            BillingRuleEntity byCode = billingRuleMapper.getActiveByRuleCode(ruleCode, now);
            if (byCode != null) {
                return new BillingRuleResolution(byCode, BillingRuleSource.EXPLICIT);
            }
        }
        BillingRuleEntity familyOrGlobal = billingRuleMapper.getActiveByModelFamily(modelFamilyCode, now);
        if (familyOrGlobal != null) {
            boolean isFamily = modelFamilyCode != null
                    && Objects.equals(familyOrGlobal.getModelFamilyCode(), modelFamilyCode);
            return new BillingRuleResolution(familyOrGlobal,
                    isFamily ? BillingRuleSource.FAMILY : BillingRuleSource.GLOBAL);
        }
        return BillingRuleResolution.missing();
    }
}
