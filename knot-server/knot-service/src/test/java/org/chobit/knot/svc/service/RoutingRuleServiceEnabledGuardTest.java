package org.chobit.knot.svc.service;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.entity.AppEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.RoutingConsumerEntity;
import org.chobit.knot.dal.entity.RoutingRuleEntity;
import org.chobit.knot.dal.entity.RoutingRuleTargetEntity;
import org.chobit.knot.dal.mapper.AppMapper;
import org.chobit.knot.dal.mapper.LogicalModelMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.dal.mapper.ModelPoolMapper;
import org.chobit.knot.dal.mapper.RoutingConsumerMapper;
import org.chobit.knot.dal.mapper.RoutingRuleMapper;
import org.chobit.knot.dal.mapper.RoutingRuleTargetMapper;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「启用态路由规则必须绑定存在且启用的应用」的守门测试。
 *
 * <p>走 {@code updateStatus(id, true)} 这条独立写路径（列表开关），而不是只测 {@code create/update}：
 * 它从库里现有规则重建请求，最容易漏掉「应用事后被停用」这种情况。
 * 只判应用「存在」不判「启用」时，规则能保存成功，运行时却在
 * {@code RoutingResolver} 被 "Bound app is disabled" 挡住——启用即可用的契约被静默打破。</p>
 *
 * <p>依赖里只有 Mapper 接口用 Mockito（JDK 25 不能 mock 具体类）；
 * {@link ResourceTrafficPolicySupport} 是具体类，用子类覆写只读方法当替身。</p>
 */
class RoutingRuleServiceEnabledGuardTest {

    private static final Long RULE_ID = 9L;
    private static final Long APP_ID = 7L;
    private static final String APP_CODE = "app_001";
    private static final String CONSUMER_CODE = "cons-1";
    private static final String RULE_CODE = "r-1";

    private final RoutingRuleMapper ruleMapper = mock(RoutingRuleMapper.class);
    private final RoutingRuleTargetMapper targetMapper = mock(RoutingRuleTargetMapper.class);
    private final RoutingConsumerMapper consumerMapper = mock(RoutingConsumerMapper.class);
    private final ModelMapper modelMapper = mock(ModelMapper.class);
    private final ModelPoolMapper poolMapper = mock(ModelPoolMapper.class);
    private final AppMapper appMapper = mock(AppMapper.class);
    private final LogicalModelMapper logicalMapper = mock(LogicalModelMapper.class);
    private final ResourceTrafficPolicySupport traffic = new NoopTrafficPolicySupport();

    private RoutingRuleService service() {
        return new RoutingRuleService(ruleMapper, targetMapper, consumerMapper, modelMapper,
                poolMapper, appMapper, logicalMapper, traffic);
    }

    @BeforeEach
    void baseline() {
        when(ruleMapper.getById(RULE_ID)).thenReturn(rule(0));
        when(ruleMapper.countByRuleCode(any(), any())).thenReturn(0L);
        when(targetMapper.listByRuleCodes(any())).thenReturn(List.of());
        when(consumerMapper.getByCode(CONSUMER_CODE)).thenReturn(consumer(1));
    }

    @Test
    void enablingRuleRejectsDisabledBoundApp() {
        when(appMapper.getById(APP_ID)).thenReturn(app(0));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().updateStatus(RULE_ID, true));

        assertEquals(ErrorCode.VALIDATION_ERROR.code(), error.getCode());
        assertEquals("绑定应用未启用", error.getMessage());
        verify(ruleMapper, never()).updateStatus(any(), any());
    }

    @Test
    void enablingRuleRejectsMissingApp() {
        when(ruleMapper.getById(RULE_ID)).thenReturn(ruleWithoutApp());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().updateStatus(RULE_ID, true));

        assertEquals(ErrorCode.VALIDATION_ERROR.code(), error.getCode());
        assertEquals("启用规则前请选择绑定应用", error.getMessage());
        verify(ruleMapper, never()).updateStatus(any(), any());
    }

    @Test
    void disablingRuleAllowsDisabledBoundApp() {
        // 停用态不做应用启用校验：与「未启用时跳过消费者 / 应用 / 目标完整性校验」同口径
        when(appMapper.getById(APP_ID)).thenReturn(app(0));

        service().updateStatus(RULE_ID, false);

        verify(ruleMapper).updateStatus(RULE_ID, 0);
    }

    @Test
    void enablingRuleAcceptsEnabledAppAndTargets() {
        // 双向探针：校验不能退化成「一律拒绝启用」
        when(appMapper.getById(APP_ID)).thenReturn(app(1));
        when(targetMapper.listByRuleCodes(any())).thenReturn(List.of(target()));
        when(modelMapper.getByCode("m-1")).thenReturn(model(1));

        service().updateStatus(RULE_ID, true);

        verify(ruleMapper).updateStatus(RULE_ID, 1);
    }

    private static RoutingRuleEntity rule(int status) {
        RoutingRuleEntity entity = new RoutingRuleEntity();
        entity.setId(RULE_ID);
        entity.setRuleCode(RULE_CODE);
        entity.setName("规则A");
        entity.setAppId(APP_ID);
        entity.setAppCode(APP_CODE);
        entity.setConsumerCode(CONSUMER_CODE);
        entity.setSelectionStrategy("PRIORITY");
        entity.setStatus(status);
        return entity;
    }

    private static RoutingRuleEntity ruleWithoutApp() {
        RoutingRuleEntity entity = rule(0);
        entity.setAppId(null);
        entity.setAppCode(null);
        return entity;
    }

    private static AppEntity app(int status) {
        AppEntity entity = new AppEntity();
        entity.setId(APP_ID);
        entity.setAppCode(APP_CODE);
        entity.setStatus(status);
        return entity;
    }

    private static RoutingConsumerEntity consumer(int status) {
        RoutingConsumerEntity entity = new RoutingConsumerEntity();
        entity.setConsumerCode(CONSUMER_CODE);
        entity.setStatus(status);
        return entity;
    }

    private static ModelEntity model(int status) {
        ModelEntity entity = new ModelEntity();
        entity.setModelCode("m-1");
        entity.setStatus(status);
        return entity;
    }

    private static RoutingRuleTargetEntity target() {
        RoutingRuleTargetEntity entity = new RoutingRuleTargetEntity();
        entity.setRuleCode(RULE_CODE);
        entity.setTargetType("MODEL");
        entity.setTargetCode("m-1");
        entity.setPriority(100);
        entity.setPrimary(true);
        return entity;
    }

    /** 只读协作组件替身：本测试不触碰频控落库，覆写掉会打 Mapper 的读取方法。 */
    private static final class NoopTrafficPolicySupport extends ResourceTrafficPolicySupport {

        NoopTrafficPolicySupport() {
            super(null, null, null, null);
        }

        @Override
        public TrafficPolicies load(String resourceType, Long resourceId) {
            return null;
        }

        @Override
        public Map<Long, TrafficPolicies> loadBatch(String resourceType, List<Long> resourceIds) {
            return Map.of();
        }
    }
}
