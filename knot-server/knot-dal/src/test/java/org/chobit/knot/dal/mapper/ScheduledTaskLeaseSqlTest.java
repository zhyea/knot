package org.chobit.knot.dal.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ScheduledTaskMapper.xml} 执行租约两条 SQL 的解析守卫。
 *
 * <p>抢与放都是一条短语句，语义全在 {@code where} 里，而 {@code where} 写宽了不会报错，
 * 只会让「互斥」变成「轮流覆盖」：</p>
 * <ul>
 *   <li>抢的 {@code where} 少了「空或过期」只剩 {@code task_code}，就成了无条件覆盖——
 *       多节点同时到点时全都认为自己抢到了，任务被重复执行。</li>
 *   <li>放的 {@code where} 少了 {@code lock_owner = ?}，一旦自己的 handler 超时、租约已被
 *       别的节点接手，收尾反而会把对方的租约清掉，于是第三个节点又挤进来。</li>
 * </ul>
 *
 * <p>SQL 文本可静态验证，{@code now(3)} 的时区口径与真实并发不在本测试范围
 * （靠 {@code schema.sql} 启动执行 + 真库冒烟）。</p>
 */
class ScheduledTaskLeaseSqlTest {

    private static final Path XML = Path.of("src/main/resources/mapper/ScheduledTaskMapper.xml");

    @Test
    void acquireLeaseOnlyTakesAFreeOrExpiredRow() {
        String sql = MapperDynamicSql.of(ScheduledTaskMapper.class).sql("acquireLease",
                MapperDynamicSql.params("taskCode", "T1", "owner", "node-a", "leaseMinutes", 30));

        assertTrue(sql.contains("set lock_owner = ?"),
                "抢租约必须写 lock_owner：\n  " + sql);
        assertTrue(sql.contains("lease_until = date_add(now(3), interval ? minute)"),
                "租期必须由数据库时钟算，且分钟数是参数：\n  " + sql);
        assertTrue(sql.contains("where task_code = ?"),
                "抢租约必须按 task_code 定位：\n  " + sql);
        assertTrue(sql.contains("(lock_owner is null or lease_until is null or lease_until <= now(3))"),
                "抢租约的条件少了「空或过期」，就会无条件覆盖别人的租约：\n  " + sql);
    }

    @Test
    void releaseLeaseOnlyClearsItsOwnLease() {
        String sql = MapperDynamicSql.of(ScheduledTaskMapper.class).sql("releaseLease",
                MapperDynamicSql.params("taskCode", "T1", "owner", "node-a"));

        assertTrue(sql.contains("set lock_owner = null, lease_until = null"),
                "放租约要把两列一起清掉，只清一列会留下看不出归属的到期时间：\n  " + sql);
        assertTrue(sql.contains("where task_code = ? and lock_owner = ?"),
                "放租约必须限定 lock_owner = 自己，否则会误清已被别人接手的租约：\n  " + sql);
    }

    @Test
    void sessionLevelLockIsGone() {
        String xml = read();
        assertFalse(xml.contains("GET_LOCK"),
                "GET_LOCK 是会话级锁，必须与 RELEASE_LOCK 同连接；执行器已不开事务，同连接不再有保证");
        assertFalse(xml.contains("RELEASE_LOCK"),
                "RELEASE_LOCK 落到另一条池化连接上会返回 0，锁挂在原连接直到连接关闭，任务永久不再执行");
    }

    /** XML 没被解析到时，上面所有断言都会「空跑通过」，所以这里显式钉住解析结果 */
    @Test
    void doesNotSilentlySkipBecauseNothingWasParsed() {
        MapperDynamicSql.of(ScheduledTaskMapper.class).assertParsedEveryInterfaceMethod();
    }

    private static String read() {
        try {
            return Files.readString(XML, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
