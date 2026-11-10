package org.chobit.knot.svc.service;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.entity.AppEntity;
import org.chobit.knot.dal.mapper.AppMapper;
import org.chobit.knot.dal.mapper.RoutingRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 应用删除的反向守卫：删掉应用会留下「启用规则 + 孤儿 app_code」，
 * 运行时必然 {@code Bound app not found}。与改应用编码的守卫同口径，**409 拒绝、不级联**。
 *
 * <p>只拦启用态规则：停用规则不参与运行时解析，不该因为它挡住删除。</p>
 */
class AppServiceDeleteGuardTest {

    private static final Long APP_ID = 7L;
    private static final String APP_CODE = "app_001";

    private final AppMapper appMapper = mock(AppMapper.class);
    private final RoutingRuleMapper ruleMapper = mock(RoutingRuleMapper.class);

    private AppService service() {
        // delete 不用 converter / department / traffic 三个协作者
        return new AppService(appMapper, null, null, null, ruleMapper);
    }

    @BeforeEach
    void baseline() {
        AppEntity app = new AppEntity();
        app.setId(APP_ID);
        app.setAppCode(APP_CODE);
        when(appMapper.getById(APP_ID)).thenReturn(app);
    }

    @Test
    void refusesDeleteWhenEnabledRulesAreBound() {
        when(ruleMapper.countEnabledByAppCode(APP_CODE)).thenReturn(2L);

        BusinessException error = assertThrows(BusinessException.class, () -> service().delete(APP_ID));

        assertEquals(ErrorCode.CONFLICT.code(), error.getCode());
        assertTrue(error.getMessage().contains("仍被 2 条启用的路由规则绑定"));
        verify(appMapper, never()).softDelete(APP_ID);
    }

    @Test
    void allowsDeleteWhenOnlyDisabledRulesAreBound() {
        when(ruleMapper.countEnabledByAppCode(APP_CODE)).thenReturn(0L);
        when(appMapper.softDelete(APP_ID)).thenReturn(1);

        service().delete(APP_ID);

        verify(appMapper).softDelete(APP_ID);
    }
}
