package org.chobit.knot.svc.service;

import org.chobit.knot.dal.entity.ExternalModelItemEntity;
import org.chobit.knot.dal.mapper.ExternalModelMapper;
import org.chobit.knot.svc.converter.ExternalModelConverter;
import org.chobit.knot.svc.dto.model.ExternalModelItemQuery;
import org.chobit.knot.svc.dto.model.ExternalModelSyncResult;
import org.chobit.knot.svc.dto.model.LogicalModelDto;
import org.chobit.knot.svc.service.support.ExternalModelImportRunner;
import org.chobit.knot.svc.service.support.ModelFamilyResolver;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量导入的「部分成功」契约：一条失败不连坐其余，摘要与落库结果严格一致。
 *
 * <p>语义口径（2026-11-17 robin 拍板）：每条独立事务提交，失败项带 {@code itemId} +
 * {@code modelId} + 原因进 {@code failures}；成功项照常回填 {@code logical_model_id}。</p>
 */
class ExternalModelImportPartialSuccessTest {

    private final ExternalModelMapper mapper = mock(ExternalModelMapper.class);
    private final StubLogicalModelService logicalModelService = new StubLogicalModelService();
    private final Map<Long, ExternalModelItemEntity> itemsById = new HashMap<>();

    @Test
    void oneFailureDoesNotRollBackTheOthers() {
        itemsById.put(1L, item(1L, "m1"));
        itemsById.put(2L, item(2L, "m2"));
        itemsById.put(3L, item(3L, "m3"));
        itemsById.put(4L, item(4L, "m4"));
        itemsById.get(4L).setLogicalModelId(88L);
        logicalModelService.failOn("m2");
        when(mapper.listItems(any(), any(), any(), any(), anyList()))
                .thenReturn(List.of(itemsById.get(1L), itemsById.get(2L), itemsById.get(3L), itemsById.get(4L)));
        when(mapper.getItemById(anyLong())).thenAnswer(invocation -> itemsById.get(invocation.getArgument(0)));

        ExternalModelSyncResult result = service().createLogicalModels(
                new ExternalModelItemQuery(null, null, null, null, null, null, List.of(1L, 2L, 3L, 4L)));

        assertEquals(4, result.total());
        assertEquals(2, result.inserted(), "第 1、3 条应成功落库");
        assertEquals(1, result.failed());
        assertEquals(1, result.skipped(), "已导入过的第 4 条跳过");
        assertEquals(1, result.failures().size());
        assertEquals("2", result.failures().get(0).itemId());
        assertEquals("m2", result.failures().get(0).modelId());
        assertTrue(result.failures().get(0).reason().contains("duplicate"), "失败原因要能直接定位");
        assertEquals(2, result.affectedRows(), "摘要里的成功数必须等于真正写入的条数");

        verify(mapper, times(2)).updateItemLogicalModel(anyLong(), anyLong());
        verify(mapper, never()).updateItemLogicalModel(eq(2L), anyLong());
    }

    /**
     * 结构守门：逐条事务必须落在独立 bean 上。
     *
     * <p>{@code createLogicalModels} 在同一个实例里循环调用 {@code createLogicalModel}，
     * {@code this.} 自调用走不到 Spring 代理——外层一旦标回 {@code @Transactional}，
     * 内层注解静默失效，一条失败会把整批回滚。这条测试钉住这个反模式。</p>
     */
    @Test
    void perItemTransactionLivesOnTheRunner() throws Exception {
        Transactional importOne = ExternalModelImportRunner.class
                .getMethod("importOne", LogicalModelDto.class, Long.class)
                .getAnnotation(Transactional.class);
        assertNotNull(importOne, "单条导入必须标事务");
        assertEquals(Propagation.REQUIRES_NEW, importOne.propagation(),
                "必须 REQUIRES_NEW：每条独立提交，失败不连坐");

        Method loop = ExternalModelService.class.getMethod("createLogicalModels", ExternalModelItemQuery.class);
        assertNull(loop.getAnnotation(Transactional.class),
                "批量导入入口不能再包事务：异常被 catch 后提交会抛 UnexpectedRollbackException");
    }

    private ExternalModelService service() {
        ExternalModelImportRunner runner = new ExternalModelImportRunner(mapper, logicalModelService);
        return new ExternalModelService(mapper, logicalModelService, List.of(),
                new StubFamilyResolver(), mock(ExternalModelConverter.class), runner);
    }

    private ExternalModelItemEntity item(Long id, String modelId) {
        ExternalModelItemEntity entity = new ExternalModelItemEntity();
        entity.setId(id);
        entity.setModelId(modelId);
        entity.setModelName(modelId);
        entity.setSourceCode("STUB");
        entity.setSyncStatus(1);
        return entity;
    }

    private static LogicalModelDto created(Long id, String code) {
        return new LogicalModelDto(id, code, null, null, null, null, List.of(), List.of(), null, null,
                List.of(), List.of(), List.of(), null, null, false, false, 0, false, null, null, null,
                List.of(), 0L);
    }

    /**
     * 真实服务是具体类，JDK 25 下 Mockito 不能 mock，这里子类覆写：编码唯一性恒通过，
     * 指定编码的 create 抛异常以模拟「该条自身有问题」。
     */
    private static final class StubLogicalModelService extends LogicalModelService {

        private final Set<String> failingCodes = new HashSet<>();
        private long nextId = 100L;

        private StubLogicalModelService() {
            super(null, null, null, null);
        }

        private void failOn(String code) {
            failingCodes.add(code);
        }

        @Override
        public boolean isModelCodeAvailable(String modelCode, Long excludeId) {
            return true;
        }

        @Override
        public LogicalModelDto create(LogicalModelDto request) {
            if (failingCodes.contains(request.modelCode())) {
                throw new IllegalStateException("duplicate logical model code: " + request.modelCode());
            }
            return created(nextId++, request.modelCode());
        }
    }

    private static final class StubFamilyResolver extends ModelFamilyResolver {

        private StubFamilyResolver() {
            super(null);
        }

        @Override
        public String resolve(String modelId, String modelName) {
            return null;
        }
    }
}
