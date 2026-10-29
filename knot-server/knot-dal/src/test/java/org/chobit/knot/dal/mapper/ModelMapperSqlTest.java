package org.chobit.knot.dal.mapper;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ModelMapper.xml} 状态筛选的解析守卫。
 *
 * <p><b>为什么需要它</b>：与 {@link ScheduledTaskMapperSqlTest} 同一个 bug 的另一处。
 * 2026-10-07「状态枚举数字化」的规范文档已明文写了
 * {@code <if test="status != null and status != ''"> → 只判 status != null}，
 * 但迁移只改了 status 的类型，表达式留在 XML 里——{@code ModelMapper#list} 的
 * {@code status} 已是 {@code Integer}，OGNL 把 {@code ''} 当 0 比较，于是
 * <b>状态 0 的筛选被静默丢弃</b>。</p>
 *
 * <p>后果按状态枚举推断：模型列表选「停用」→ 返回全部模型。不报错、不告警、日志无痕，
 * 前端看到了「筛选已生效」的 UI 反馈，只是数据不对。</p>
 *
 * <p>⚠ 断言里的 {@code 0} 是故意写死的裸值：引 {@code EnabledStatus} 之类常量的话，
 * 将来改码值这条测试会一起变，护栏就失效了。</p>
 */
class ModelMapperSqlTest {

    /** 列名带表别名：{@code m.status}（两表 join，不加别名会有歧义） */
    private static final Pattern STATUS = MapperDynamicSql.equalTo("m.status");

    @Test
    void keepsTheZeroStatusPredicate() {
        String sql = MapperDynamicSql.of(ModelMapper.class).sql("list", paramsOf(0));
        assertTrue(STATUS.matcher(sql).find(),
                "status=0 时 ModelMapper#list 的 status 条件被丢了——十有八九是 <if> 里还留着"
                        + " status != ''（OGNL 把 '' 当 0 比较）。后果是「筛停用」返回全部模型，且不报错：\n  " + sql);
    }

    @Test
    void nullStatusMeansNoStatusPredicate() {
        String sql = MapperDynamicSql.of(ModelMapper.class).sql("list", paramsOf(null));
        assertFalse(STATUS.matcher(sql).find(), "status 传 null（全部）时不该出现 status 条件：\n  " + sql);
    }

    /** XML 没被解析到时，上面的断言都会「空跑通过」，所以这里显式钉住解析结果 */
    @Test
    void doesNotSilentlySkipBecauseNothingWasParsed() {
        MapperDynamicSql.of(ModelMapper.class).assertParsedEveryInterfaceMethod();
    }

    private static Map<String, Object> paramsOf(Integer status) {
        return MapperDynamicSql.params(
                "keyword", null,
                "modelTypes", null,
                "logicalModelCode", null,
                "status", status,
                "includeDeleted", null);
    }
}
