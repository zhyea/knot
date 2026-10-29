package org.chobit.knot.dal.mapper;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ScheduledTaskMapper.xml} 状态筛选的解析守卫。
 *
 * <p><b>为什么需要它（2026-10-28 发现真 bug 后补）</b>：{@code listTasks} / {@code listRuns}
 * 原本写的是 {@code <if test="status != null and status != ''">}。这个写法对
 * <b>{@code String} 参数</b>是对的（挡空串），但这两处的 {@code status} 是 {@code Integer}，
 * 而 OGNL 把 {@code ''} 当数字 0 来比较——于是 {@code status = 0} 时整个条件被判为
 * <b>false 并静默丢弃</b>。后果：运行记录面板选「运行中」（{@code ScheduledTaskRunStatus.RUNNING = 0}）、
 * 任务列表选「停用」（{@code EnabledStatus.DISABLED = 0}）都会返回<b>全部</b>记录，
 * 而后端不报错、日志无痕、单测全绿（mapper 在 service 单测里是 mock）。</p>
 *
 * <p>这正是「状态 0 合法」那条红线的 SQL 侧变体：<b>凡是拿状态码当布尔用，0 都会被吃掉</b>，
 * 前端禁 truthy 只挡住了 JS 那一半。</p>
 *
 * <p>⚠ <b>判据只针对 {@code Integer} 状态列</b>：字符串筛选列（{@code handlerCode} /
 * {@code taskCode} / {@code triggerType}）保留 {@code != ''} 是正确语义，别一起改掉——
 * 那会让「传了空串」变成「按空串精确匹配」，反而查不到东西。</p>
 */
class ScheduledTaskMapperSqlTest {

    private static final Pattern STATUS = MapperDynamicSql.equalTo("status");

    /**
     * ⚠ 断言里的 {@code 0} 是<b>故意写死的裸值</b>，不引 {@code EnabledStatusEnum}：
     * 引常量的话，将来有人把「停用」改成别的码，这条测试会跟着变，护栏就失效了。
     * 「0 必须是有效筛选值」这件事本身才是要钉住的事实。
     */
    @Test
    void listTasksKeepsTheZeroStatusPredicate() {
        String sql = MapperDynamicSql.of(ScheduledTaskMapper.class).sql("listTasks", listTasksParams(0));
        assertTrue(STATUS.matcher(sql).find(),
                "status=0 时 listTasks 的 status 条件被丢了——十有八九是 <if> 里还留着 status != ''"
                        + "（OGNL 把 '' 当 0 比较，0 就被判成 false）。"
                        + "后果是「筛停用」返回全部任务，且不报错：\n  " + sql);
    }

    @Test
    void listRunsKeepsTheZeroStatusPredicate() {
        String sql = MapperDynamicSql.of(ScheduledTaskMapper.class).sql("listRuns", listRunsParams(0));
        assertTrue(STATUS.matcher(sql).find(),
                "status=0 时 listRuns 的 status 条件被丢了——「运行中」（0）是本面板最常用的筛选，"
                        + "丢了等于筛选按钮没接后端：\n  " + sql);
    }

    @Test
    void nullStatusMeansNoStatusPredicate() {
        MapperDynamicSql mapper = MapperDynamicSql.of(ScheduledTaskMapper.class);
        assertFalse(STATUS.matcher(mapper.sql("listTasks", listTasksParams(null))).find(),
                "status 传 null（全部）时不该出现 status 条件");
        assertFalse(STATUS.matcher(mapper.sql("listRuns", listRunsParams(null))).find(),
                "status 传 null（全部）时不该出现 status 条件");
    }

    /** XML 没被解析到时，上面所有断言都会「空跑通过」，所以这里显式钉住解析结果 */
    @Test
    void doesNotSilentlySkipBecauseNothingWasParsed() {
        MapperDynamicSql.of(ScheduledTaskMapper.class).assertParsedEveryInterfaceMethod();
    }

    private static Map<String, Object> listTasksParams(Integer status) {
        return MapperDynamicSql.params("keyword", null, "status", status, "handlerCode", null);
    }

    private static Map<String, Object> listRunsParams(Integer status) {
        return MapperDynamicSql.params("taskCode", null, "status", status, "triggerType", null);
    }
}
