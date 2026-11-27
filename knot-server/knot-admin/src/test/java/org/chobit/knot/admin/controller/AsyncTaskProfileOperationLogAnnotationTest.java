package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;
import org.chobit.knot.dal.mapper.AsyncTaskProfileMapper;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileSaveDto;
import org.chobit.knot.svc.service.AsyncTaskProfileService;
import org.chobit.knot.svc.vo.async.AsyncTaskProfileDetailVo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 异步任务档案的「操作记录」能否被检索到的守门。
 *
 * <p>日志抽屉按 {@code entity_id} 精确检索（{@code OperationLogQuery.entityId}），而档案的引用键是
 * {@code profileCode}；一旦 {@code @OperationLog} 的 {@code entityId}/{@code entityIdAfter} 写错，
 * {@link org.chobit.knot.svc.aspect.OperationLogAspect} 会**静默**把 entity_id 落成 null
 * （SpEL 求值异常被吞），前端抽屉打开永远是空表——不报错、不提示，只能被人发现。</p>
 *
 * <p>所以这里不复制注解，而是取**控制器上真实的注解**，用真实类型求值：save 侧拿
 * {@link AsyncTaskProfileDetailVo} 当 {@code #result}，delete 侧走真实的
 * {@link AsyncTaskProfileService#profileIdOf} + mock mapper。</p>
 */
class AsyncTaskProfileOperationLogAnnotationTest {

    private final AsyncTaskProfileMapper profileMapper = mock(AsyncTaskProfileMapper.class);

    @Test
    void saveLogCarriesProfileId() throws Exception {
        OperationLog annotation = annotationOf("save", AsyncTaskProfileSaveDto.class);
        AsyncTaskProfileDetailVo saved = detail(42L, "KLING_TASK");

        Object entityId = evaluate(annotation.entityIdAfter(), Map.of("result", saved));

        assertEquals(42L, entityId, "保存日志没拿到档案 id，操作记录抽屉会查不到这条");
    }

    @Test
    void deleteLogCarriesProfileIdResolvedFromCode() throws Exception {
        AsyncTaskProfileEntity existing = new AsyncTaskProfileEntity();
        existing.setId(42L);
        existing.setProfileCode("KLING_TASK");
        when(profileMapper.getByProfileCode("KLING_TASK")).thenReturn(existing);

        OperationLog annotation = annotationOf("delete", String.class);

        Object entityId = evaluate(annotation.entityId(), Map.of("p0", "KLING_TASK"));

        assertEquals(42L, entityId, "删除日志没拿到档案 id，删完那条记录就查不回来了");
    }

    private OperationLog annotationOf(String methodName, Class<?> parameterType) throws Exception {
        Method method = AsyncTaskProfileController.class.getMethod(methodName, parameterType);
        return method.getAnnotation(OperationLog.class);
    }

    /** 与切面同构的求值环境：参数名 p0 与 @bean 解析都要在，否则测不出真实接线 */
    private Object evaluate(String expression, Map<String, Object> variables) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("asyncTaskProfileService",
                new AsyncTaskProfileService(profileMapper, null));
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        variables.forEach(context::setVariable);
        return new SpelExpressionParser().parseExpression(expression).getValue(context);
    }

    private static AsyncTaskProfileDetailVo detail(Long id, String profileCode) {
        return new AsyncTaskProfileDetailVo(id, profileCode, "VIDEO_GENERATIONS", null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                1, null, 0, null, null, null);
    }
}
