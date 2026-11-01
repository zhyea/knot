package org.chobit.knot.svc.service.external;

import org.chobit.knot.common.constants.enums.ExternalModelSyncStatusEnum;
import org.chobit.knot.dal.entity.ExternalModelItemEntity;
import org.chobit.knot.dal.entity.ExternalModelSourceEntity;
import org.chobit.knot.dal.mapper.ExternalModelMapper;
import org.chobit.knot.svc.dto.model.ExternalModelSyncResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 同步编排的查重契约。
 *
 * <p>2026-11-01 robin 拍板：去重键从 {@code (source_code, model_id)} 收窄为
 * {@code model_id}——同一模型跨源视为同一条目，后同步的源覆盖旧数据，条目归属
 * （source_code）翻到最近同步的源。这里锁四件事：查重只按 model_id、插入/更新时
 * 带上当前源、更新分支保留 id 与 logicalModelId、单条异常落 FAILED。</p>
 */
class AbstractExternalModelSyncProviderTest {

    private final ExternalModelMapper mapper = mock(ExternalModelMapper.class);

    private static final class StubProvider extends AbstractExternalModelSyncProvider {
        private final List<ExternalModelItemEntity> items;

        StubProvider(ExternalModelMapper mapper, List<ExternalModelItemEntity> items) {
            super(mapper);
            this.items = items;
        }

        @Override
        public String sourceCode() {
            return "STUB";
        }

        @Override
        protected ExternalModelSourceEntity defaultSource() {
            ExternalModelSourceEntity source = new ExternalModelSourceEntity();
            source.setSourceCode("STUB");
            source.setSourceName("Stub Source");
            source.setSourceType("MODEL_CATALOG");
            source.setStatus(1);
            return source;
        }

        @Override
        protected List<ExternalModelItemEntity> fetchItems(ExternalModelSourceEntity source) {
            return items;
        }
    }

    private ExternalModelItemEntity item(String modelId, String syncHash) {
        ExternalModelItemEntity entity = new ExternalModelItemEntity();
        entity.setModelId(modelId);
        entity.setModelName(modelId);
        entity.setSyncHash(syncHash);
        return entity;
    }

    private AbstractExternalModelSyncProvider provider(List<ExternalModelItemEntity> items) {
        return new StubProvider(mapper, items);
    }

    private ExternalModelSourceEntity stubSource() {
        ExternalModelSourceEntity source = new ExternalModelSourceEntity();
        source.setId(100L);
        source.setSourceCode("STUB");
        return source;
    }

    @Test
    @DisplayName("查重只按 model_id，与来源无关")
    void dedupsByModelIdOnly() {
        when(mapper.getSourceByCode("STUB")).thenReturn(stubSource());
        when(mapper.getItemByModelId("deepseek-chat")).thenReturn(null);
        when(mapper.insertItem(any())).thenReturn(1);

        provider(List.of(item("deepseek-chat", "h1"))).sync();

        verify(mapper).getItemByModelId("deepseek-chat");
        verify(mapper, never()).getItemByModelId(eq("other"));
        // 旧方法已删除，这里用「insert 收到的实体 sourceCode 为当前源」佐证键不再含来源
        ArgumentCaptor<ExternalModelItemEntity> captor = ArgumentCaptor.forClass(ExternalModelItemEntity.class);
        verify(mapper).insertItem(captor.capture());
        assertEquals("STUB", captor.getValue().getSourceCode());
    }

    @Test
    @DisplayName("查无此模型 → 插入，同步状态置为已同步")
    void insertsWhenMissing() {
        when(mapper.getSourceByCode("STUB")).thenReturn(stubSource());
        when(mapper.getItemByModelId("m1")).thenReturn(null);
        when(mapper.insertItem(any())).thenReturn(1);

        ExternalModelSyncResult result = provider(List.of(item("m1", "h1"))).sync();

        assertEquals(1, result.inserted());
        ArgumentCaptor<ExternalModelItemEntity> captor = ArgumentCaptor.forClass(ExternalModelItemEntity.class);
        verify(mapper).insertItem(captor.capture());
        assertEquals("STUB", captor.getValue().getSourceCode());
        assertEquals(ExternalModelSyncStatusEnum.SYNCED.code(), captor.getValue().getSyncStatus());
    }

    @Test
    @DisplayName("内容指纹未变 → 记 skipped，仍更新归属与同步状态")
    void skipsWhenHashUnchanged() {
        ExternalModelItemEntity existing = item("m1", "same-hash");
        existing.setId(7L);
        existing.setLogicalModelId(9L);
        existing.setSourceCode("OPENROUTER");
        when(mapper.getSourceByCode("STUB")).thenReturn(stubSource());
        when(mapper.getItemByModelId("m1")).thenReturn(existing);

        ExternalModelSyncResult result = provider(List.of(item("m1", "same-hash"))).sync();

        assertEquals(1, result.skipped());
        ArgumentCaptor<ExternalModelItemEntity> captor = ArgumentCaptor.forClass(ExternalModelItemEntity.class);
        verify(mapper).updateItem(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals(9L, captor.getValue().getLogicalModelId());
        assertEquals("STUB", captor.getValue().getSourceCode());
        assertEquals(ExternalModelSyncStatusEnum.SYNCED.code(), captor.getValue().getSyncStatus());
    }

    @Test
    @DisplayName("内容有变 → 更新同一条，保留 id 与逻辑模型绑定，归属翻到新源")
    void updatesAndReassignsSourceWhenHashChanged() {
        ExternalModelItemEntity existing = item("m1", "old-hash");
        existing.setId(7L);
        existing.setLogicalModelId(9L);
        existing.setSourceCode("OPENROUTER");
        when(mapper.getSourceByCode("STUB")).thenReturn(stubSource());
        when(mapper.getItemByModelId("m1")).thenReturn(existing);

        ExternalModelSyncResult result = provider(List.of(item("m1", "new-hash"))).sync();

        assertEquals(1, result.updated());
        ArgumentCaptor<ExternalModelItemEntity> captor = ArgumentCaptor.forClass(ExternalModelItemEntity.class);
        verify(mapper).updateItem(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals(9L, captor.getValue().getLogicalModelId());
        assertEquals("STUB", captor.getValue().getSourceCode());
    }

    @Test
    @DisplayName("更新抛异常 → 该条落 FAILED，不让记录永远停在待同步")
    void marksFailedWhenUpdateThrows() {
        ExternalModelItemEntity existing = item("m1", "old-hash");
        existing.setId(7L);
        existing.setLogicalModelId(9L);
        when(mapper.getSourceByCode("STUB")).thenReturn(stubSource());
        when(mapper.getItemByModelId("m1")).thenReturn(existing);
        when(mapper.updateItem(any())).thenThrow(new RuntimeException("db down"))
                .thenReturn(1);

        ExternalModelSyncResult result = provider(List.of(item("m1", "new-hash"))).sync();

        assertEquals(1, result.failed());
        ArgumentCaptor<ExternalModelItemEntity> captor = ArgumentCaptor.forClass(ExternalModelItemEntity.class);
        verify(mapper, times(2)).updateItem(captor.capture());
        assertEquals(ExternalModelSyncStatusEnum.FAILED.code(), captor.getAllValues().get(1).getSyncStatus());
    }

    @Test
    @DisplayName("同步结束刷新源的 last_sync_at")
    void touchesSourceLastSyncAt() {
        when(mapper.getSourceByCode("STUB")).thenReturn(stubSource());
        when(mapper.getItemByModelId("m1")).thenReturn(null);
        when(mapper.insertItem(any())).thenReturn(1);

        provider(List.of(item("m1", "h1"))).sync();

        verify(mapper).updateSourceLastSyncAt(eq("STUB"), any());
    }

    @Test
    @DisplayName("源不存在时按 defaultSource 兜底插入")
    void insertsSourceWhenMissing() {
        when(mapper.getSourceByCode("STUB")).thenReturn(null);
        when(mapper.getItemByModelId("m1")).thenReturn(null);
        when(mapper.insertItem(any())).thenReturn(1);

        provider(List.of(item("m1", "h1"))).sync();

        ArgumentCaptor<ExternalModelSourceEntity> captor = ArgumentCaptor.forClass(ExternalModelSourceEntity.class);
        verify(mapper).insertSource(captor.capture());
        assertEquals("STUB", captor.getValue().getSourceCode());
    }

    @Test
    @DisplayName("条目缺 syncHash 时走 updated 分支而不是 skipped（null 指纹视为内容未知）")
    void treatsNullHashAsChanged() {
        ExternalModelItemEntity existing = item("m1", null);
        existing.setId(7L);
        when(mapper.getSourceByCode("STUB")).thenReturn(stubSource());
        when(mapper.getItemByModelId("m1")).thenReturn(existing);

        ExternalModelSyncResult result = provider(List.of(item("m1", null))).sync();

        assertEquals(0, result.skipped());
        assertEquals(1, result.updated());
        verify(mapper).updateItem(any(ExternalModelItemEntity.class));
    }
}
