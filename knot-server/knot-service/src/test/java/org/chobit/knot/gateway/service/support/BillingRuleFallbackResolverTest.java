package org.chobit.knot.gateway.service.support;

import org.chobit.knot.gateway.dto.routing.BillingRuleSource;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link BillingRuleFallbackResolver} 三级回退：EXPLICIT → FAMILY → GLOBAL → MISSING。
 */
class BillingRuleFallbackResolverTest {

    private BillingRuleEntity ruleWithFamily(String family) {
        BillingRuleEntity rule = new BillingRuleEntity();
        rule.setCode("r");
        rule.setModelFamilyCode(family);
        return rule;
    }

    private BillingRuleMapper mapperReturning(BillingRuleEntity byCode, BillingRuleEntity byFamily) {
        BillingRuleMapper mapper = mock(BillingRuleMapper.class);
        when(mapper.getActiveByRuleCode(any(), any(LocalDateTime.class))).thenReturn(byCode);
        when(mapper.getActiveByModelFamily(any(), any(LocalDateTime.class))).thenReturn(byFamily);
        return mapper;
    }

    @Test
    void shouldResolveExplicitByCode() {
        BillingRuleEntity explicit = ruleWithFamily("fam1");
        BillingRuleFallbackResolver resolver = new BillingRuleFallbackResolver(
                mapperReturning(explicit, ruleWithFamily(null)));
        BillingRuleResolution r = resolver.resolve("explicit-code", "fam1");
        assertEquals(BillingRuleSource.EXPLICIT, r.matchedBy());
    }

    @Test
    void shouldResolveFamilyWhenCodeMissing() {
        BillingRuleEntity familyRule = ruleWithFamily("fam1");
        BillingRuleFallbackResolver resolver = new BillingRuleFallbackResolver(
                mapperReturning(null, familyRule));
        BillingRuleResolution r = resolver.resolve("missing", "fam1");
        assertEquals(BillingRuleSource.FAMILY, r.matchedBy());
    }

    @Test
    void shouldResolveGlobalWhenOnlyDefaultExists() {
        BillingRuleEntity globalRule = ruleWithFamily(null);
        BillingRuleFallbackResolver resolver = new BillingRuleFallbackResolver(
                mapperReturning(null, globalRule));
        BillingRuleResolution r = resolver.resolve("missing", "fam1");
        assertEquals(BillingRuleSource.GLOBAL, r.matchedBy());
    }

    @Test
    void shouldResolveMissingWhenNothingMatches() {
        BillingRuleFallbackResolver resolver = new BillingRuleFallbackResolver(
                mapperReturning(null, null));
        BillingRuleResolution r = resolver.resolve("missing", "fam1");
        assertEquals(BillingRuleSource.MISSING, r.matchedBy());
        assertNull(r.rule());
    }
}
