package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.converter.ModerationWordConverter;
import org.chobit.knot.gateway.dto.moderation.ModerationWordDto;
import org.chobit.knot.gateway.dto.moderation.ModerationWordImportItem;
import org.chobit.knot.gateway.dto.moderation.ModerationWordImportResult;
import org.chobit.knot.gateway.entity.ModerationWordEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.mapper.ModerationWordMapper;
import org.chobit.knot.gateway.moderation.ModerationTerm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 敏感词导入导出与逻辑删除语义专项测试。
 *
 * <p>重点守护「部分失败不影响已成功的行」：唯一键 {@code uk_word_category} 是物理键，
 * 判重必须含已逻辑删除行，否则重复导入一个已删除词会撞唯一键把整批回滚。</p>
 */
class ModerationWordServiceTest {

    private final ModerationWordMapper wordMapper = mock(ModerationWordMapper.class);
    private final ModerationWordConverter converter = new ModerationWordConverter();
    private final ModerationWordService service =
            new ModerationWordService(wordMapper, converter);

    /** 全量行（含已逻辑删除）：importWords 的判重数据源。 */
    private final List<ModerationWordEntity> allRows = new ArrayList<>();
    /** 未删除行：listForExport 的返回。 */
    private final List<ModerationWordEntity> liveRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(wordMapper.list(isNull(), isNull(), eq(Boolean.TRUE))).thenAnswer(inv -> allRows);
        when(wordMapper.listForExport()).thenAnswer(inv -> liveRows);
        when(wordMapper.insert(any(ModerationWordEntity.class))).thenAnswer(inv -> {
            ModerationWordEntity e = inv.getArgument(0);
            e.setId((long) (allRows.size() + 1));
            allRows.add(e);
            return 1;
        });
    }

    private ModerationWordEntity row(Long id, String word, String category, int isDeleted, int status) {
        ModerationWordEntity e = new ModerationWordEntity();
        e.setId(id);
        e.setWord(word);
        e.setNormalizedWord(word.toLowerCase());
        e.setCategoryCode(category);
        e.setSeverity(2);
        e.setLang("zh");
        e.setStatus(status);
        e.setIsDeleted(isDeleted);
        e.setRemark(null);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        if (isDeleted == 0) {
            liveRows.add(e);
        }
        allRows.add(e);
        return e;
    }

    private static ModerationWordImportItem item(String word, String category) {
        return new ModerationWordImportItem(word, category, 2, "zh", null);
    }

    // ==================== importWords ====================

    @Test
    void importSkipsKeysAlreadyDeletedInDb() {
        // 已逻辑删除的行仍占唯一键：重复导入必须跳过，不能撞 uk_word_category 回滚整批
        row(1L, "badword", "ABUSE", 1, 0);

        ModerationWordImportResult result = service.importWords(List.of(
                item("badword", "ABUSE"),
                item("newword", "ABUSE")));

        assertEquals(2, result.total());
        assertEquals(1, result.inserted());
        assertEquals(1, result.skipped());
        assertTrue(result.errors().isEmpty(), "已删除词应静默跳过，不算失败行");
    }

    @Test
    void importSkipsKeysAlreadyLiveInDb() {
        row(1L, "badword", "ABUSE", 0, 1);

        ModerationWordImportResult result = service.importWords(List.of(item("badword", "ABUSE")));

        assertEquals(1, result.skipped());
        assertEquals(0, result.inserted());
    }

    @Test
    void importReportsBatchInternalDuplicateAsError() {
        ModerationWordImportResult result = service.importWords(List.of(
                item("dup", "AD"),
                item("dup", "AD")));

        assertEquals(2, result.total());
        assertEquals(1, result.inserted());
        assertEquals(0, result.skipped());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("与本批中前面的行重复"));
    }

    @Test
    void importRejectsOversizedBatch() {
        List<ModerationWordImportItem> rows = new ArrayList<>();
        for (int i = 0; i < 20_001; i++) {
            rows.add(item("w" + i, "CUSTOM"));
        }

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importWords(rows));
        assertTrue(ex.getMessage().contains("上限"));
        verify(wordMapper, never()).insert(any());
    }

    @Test
    void importValidatesEachRowIndependently() {
        ModerationWordImportResult result = service.importWords(List.of(
                item("  ", "AD"),
                item("ok", ""),
                new ModerationWordImportItem("bad-sev", "AD", 9, "zh", null),
                item("good", "AD")));

        assertEquals(4, result.total());
        assertEquals(1, result.inserted());
        assertEquals(3, result.errors().size());
    }

    @Test
    void importEmptyBatchIsNoop() {
        ModerationWordImportResult result = service.importWords(List.of());
        assertEquals(0, result.total());
        verify(wordMapper, never()).insert(any());
    }

    // ==================== exportWords ====================

    @Test
    void exportReturnsLiveRowsOnly() {
        row(1L, "live", "AD", 0, 1);
        row(2L, "gone", "AD", 1, 0);

        List<ModerationWordDto> exported = service.exportWords();

        assertEquals(1, exported.size());
        assertEquals("live", exported.get(0).word());
    }

    // ==================== delete / restore ====================

    @Test
    void deleteRejectsEnabledWord() {
        when(wordMapper.getById(1L)).thenReturn(row(1L, "live", "AD", 0, 1));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("先停用"));
        verify(wordMapper, never()).logicalDelete(any());
    }

    @Test
    void deleteRejectsMissingWord() {
        when(wordMapper.getById(9L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.delete(9L));
        verify(wordMapper, never()).logicalDelete(any());
    }

    @Test
    void restoreRejectsWordNotDeleted() {
        ModerationWordEntity live = row(1L, "live", "AD", 0, 1);
        when(wordMapper.getByIdIncludingDeleted(1L)).thenReturn(live);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restore(1L));
        assertTrue(ex.getMessage().contains("无需恢复"));
        verify(wordMapper, never()).restore(any());
    }

    @Test
    void createRejectsDuplicateInSameCategory() {
        row(1L, "dup", "AD", 0, 1);
        when(wordMapper.countByWordAndCategory(eq("dup"), eq("AD"), isNull())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new ModerationWordDto(null, "dup", "AD", 2, "zh",
                        true, false, null, null, null, null)));
        assertTrue(ex.getMessage().contains("已存在相同敏感词"));
        verify(wordMapper, never()).insert(any());
    }

    @Test
    void createNormalizesWordAndDefaultsSeverityAndLang() {
        when(wordMapper.countByWordAndCategory(anyString(), anyString(), isNull())).thenReturn(0L);
        when(wordMapper.getById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return allRows.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
        });

        ModerationWordDto created = service.create(new ModerationWordDto(null, "  BＡD  ", "AD",
                null, null, true, false, null, null, null, null));

        assertEquals("BＡD", created.word());
        assertEquals(2, created.severity());
        assertEquals("zh", created.lang());
        assertFalse(created.deleted());
    }

    @Test
    void createRejectsSeverityOutOfRange() {
        when(wordMapper.countByWordAndCategory(anyString(), anyString(), isNull())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new ModerationWordDto(null, "w", "AD", 4, "zh",
                        true, false, null, null, null, null)));
        assertTrue(ex.getMessage().contains("严重度"));
    }

    @Test
    void updateRejectsMissingWord() {
        when(wordMapper.getById(9L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.update(9L,
                new ModerationWordDto(9L, "w", "AD", 2, "zh", true, false, null, null, null, null)));
    }

    @Test
    void updateStatusRejectsMissingWord() {
        when(wordMapper.getById(9L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.updateStatus(9L, true));
        verify(wordMapper, never()).updateStatus(anyLong(), anyInt());
    }

    // ==================== loadEnabledTerms ====================

    @Test
    void loadEnabledTermsMapsRowsToNeutralTerms() {
        when(wordMapper.listEnabled()).thenReturn(List.of(row(1L, "badword", "ABUSE", 0, 1)));

        List<ModerationTerm> terms = service.loadEnabledTerms();

        assertEquals(1, terms.size());
        ModerationTerm term = terms.get(0);
        assertEquals("badword", term.word());
        assertEquals("badword", term.normalizedWord());
        assertEquals("ABUSE", term.categoryCode());
        assertEquals(2, term.severity());
        assertNotNull(term.updatedAtMillis());
    }

    @Test
    void loadEnabledTermsReturnsEmptyListWhenNoRows() {
        when(wordMapper.listEnabled()).thenReturn(List.of());

        assertTrue(service.loadEnabledTerms().isEmpty());
    }
}
