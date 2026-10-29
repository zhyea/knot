package org.chobit.knot.dal.mapper;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ModelPoolMapper.xml} 状态筛选的解析守卫。
 *
 * <p><b>为什么需要它</b>：{@code ModelMapper} 的同批漏改（同一份 2026-10-07 迁移方案
 * 明文要求「<b>只判 status != null</b>」，但改类型时漏了表达式）。
 * {@code status} 是 {@code Integer}，OGNL 把 {@code ''} 当 0 比较 → 模型池列表选
 * 「停用」（0）会返回全部池，不报错。</p>
 *
 * <p>⚠ 列名带表别名 {@code mp.status}：本语句 join 了 {@code kb_logical_models}，
 * 判据必须盯真实列而不是裸 {@code status}（后者会被 join 侧的列名满足而假绿）。</p>
 */
class ModelPoolMapperSqlTest {

    private static final Pattern STATUS = MapperDynamicSql.equalTo("mp.status");

    @Test
    void keepsTheZeroStatusPredicate() {
        String sql = MapperDynamicSql.of(ModelPoolMapper.class).sql("list", paramsOf(0));
        assertTrue(STATUS.matcher(sql).find(),
                "status=0 时 ModelPoolMapper#list 的 status 条件被丢了——十有八九是 <if> 里还留着"
                        + " status != ''（OGNL 把 '' 当 0 比较）。后果是「筛停用」返回全部模型池：\n  " + sql);
    }

    @Test
    void nullStatusMeansNoStatusPredicate() {
        String sql = MapperDynamicSql.of(ModelPoolMapper.class).sql("list", paramsOf(null));
        assertFalse(STATUS.matcher(sql).find(), "status 传 null（全部）时不该出现 status 条件：\n  " + sql);
    }

    @Test
    void doesNotSilentlySkipBecauseNothingWasParsed() {
        MapperDynamicSql.of(ModelPoolMapper.class).assertParsedEveryInterfaceMethod();
    }

    private static Map<String, Object> paramsOf(Integer status) {
        return MapperDynamicSql.params(
                "keyword", null,
                "modelTypes", null,
                "includeDeleted", null,
                "status", status);
    }
}
