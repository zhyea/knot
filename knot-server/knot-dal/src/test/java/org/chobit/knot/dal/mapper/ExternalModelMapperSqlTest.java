package org.chobit.knot.dal.mapper;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ExternalModelMapper.xml} 中 {@code model_id} 写入口径的守卫。
 *
 * <p>同步去重以 {@code model_id} 为准，行身份由唯一键 {@code uk_external_model_item} 在
 * {@code utf8mb4_unicode_ci} 下判定，不区分大小写；因此更新分支一律按 {@code id} 定位，
 * {@code model_id} 只由插入写入，不随同步刷新大小写。</p>
 */
class ExternalModelMapperSqlTest {

    /** set 子句里的 {@code model_id = ?}：命中即说明更新会改写行身份列 */
    private static final Pattern MODEL_ID_ASSIGN = Pattern.compile("(?i)\\bmodel_id\\s*=");

    @Test
    void updateItemKeepsModelIdUntouched() {
        String sql = MapperDynamicSql.of(ExternalModelMapper.class).sql("updateItem", Map.of());
        assertFalse(MODEL_ID_ASSIGN.matcher(sql).find(),
                "updateItem 改写了 model_id —— 同步会按上游大小写覆盖行身份列，"
                        + "历史条目的大小写随上游抖动：\n  " + sql);
    }

    @Test
    void insertItemStillCarriesModelId() {
        String sql = MapperDynamicSql.of(ExternalModelMapper.class).sql("insertItem", Map.of());
        assertTrue(sql.toLowerCase().contains("model_id"),
                "insertItem 没写 model_id，上面那条断言会空跑通过：\n  " + sql);
    }

    @Test
    void doesNotSilentlySkipBecauseNothingWasParsed() {
        MapperDynamicSql.of(ExternalModelMapper.class).assertParsedEveryInterfaceMethod();
    }
}
