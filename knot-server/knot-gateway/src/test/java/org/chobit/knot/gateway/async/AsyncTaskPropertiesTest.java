package org.chobit.knot.gateway.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认值的守门：一处是「时间预算」，一处是「节拍」。
 *
 * <p>⭐ 预算链只有三个数，但错一个就是资损口径：查询超时（{@code query-timeout-ms}）
 * 必须严格小于占位有效期（{@code claim-ttl-ms}），而后者又要留够余量给视频转储与落终态的
 * JDBC/发事件。改任意一个默认值都可能把链条弄断，且断的表现是「偶发少算上游调用数」
 * ——线上查不出来，只能靠这里和启动期校验挡住。</p>
 *
 * <p>⭐ 节拍这边守的是<b>重复定义</b>：{@code @Scheduled} 读不到 bean 字段，cron 默认值被迫写在
 * 注解与属性两处。这里比对两份 + 校验能被 Spring 解析，避免「yml 没配时跑的是哪一份」说不清。</p>
 *
 * <p>为什么放在单元测试而不是只留启动期校验：启动期校验要到部署才跑，
 * 而默认值是代码改动，改错应该在 CI 就红。</p>
 */
class AsyncTaskPropertiesTest {

    private final AsyncTaskProperties properties = new AsyncTaskProperties();

    @Test
    @DisplayName("默认查询超时必须严格小于占位有效期")
    void queryTimeoutFitsInClaimTtl() {
        assertTrue(properties.getQueryTimeoutMs() > 0, "查询超时必须是正数");
        assertTrue(properties.getClaimTtlMs() > properties.getQueryTimeoutMs(),
                "占位必须先于查询超时——反过来占位会在查询返回前过期，别的节点会重领同一行");
    }

    @Test
    @DisplayName("占位还要留够余量给转储与落终态，不能只比查询超时大 1ms")
    void claimTtlLeavesRoomForArchiveAndSettle() {
        long slack = properties.getClaimTtlMs() - properties.getQueryTimeoutMs();
        assertTrue(slack >= 60_000L,
                "扣除查询后至少要留一分钟给视频转储与落终态，否则 S3 慢一点就会把占位拖过期");
    }

    @Test
    @DisplayName("默认不启用超时回收的空档保护：占位至少是分钟级")
    void claimTtlIsMinuteScale() {
        assertTrue(properties.getClaimTtlMs() >= 60_000L,
                "占位过短会让节点崩溃后的孤儿行回收得很快，但健康节点也会被频繁抢走任务");
    }

    /**
     * ⭐ {@code @Scheduled} 只吃字面量/占位符，读不到 bean 字段，所以 cron 的默认值被迫写两份。
     *
     * <p>两份漂移的后果不是编译错，而是「yml 里没配的时候到底跑哪一份」说不清——
     * 排查时看到 `AsyncTaskProperties` 里写着每分钟、实际却每秒扫一次，会直接怀疑人生。
     * 这里把注解里的兜底值与属性默认值抠出来比一比。</p>
     */
    @Test
    @DisplayName("注解兜底值与属性默认值一致（同一事实不许写两遍还写歪）")
    void annotationFallbackMatchesPropertiesDefault() throws Exception {
        assertEquals(properties.getTickCron(), cronFallbackOf("tick"));
        assertEquals(properties.getCleanupCron(), cronFallbackOf("archive"));
    }

    @Test
    @DisplayName("两个默认 cron 都能被 Spring 解析（写错只在启动期炸，CI 拦更便宜）")
    void defaultCronsAreParseable() {
        CronExpression.parse(properties.getTickCron());
        CronExpression.parse(properties.getCleanupCron());
    }

    @Test
    @DisplayName("tick 默认每分钟一次：秒位是 0，不是 *")
    void tickRunsOncePerMinute() {
        assertEquals("0 * * * * *", properties.getTickCron());
        CronExpression parsed = CronExpression.parse(properties.getTickCron());
        LocalDateTime base = LocalDateTime.of(2026, 10, 28, 12, 0, 0);
        assertEquals(base.plusMinutes(1), parsed.next(base), "下一拍必须正好是下一分钟，不是下一秒");
    }

    /** 从 {@code ${key:default}} 里抠出 default；没写兜底说明本类断言的前提就不成立。 */
    private static String cronFallbackOf(String methodName) throws Exception {
        Scheduled scheduled = AsyncTaskPoller.class.getMethod(methodName).getAnnotation(Scheduled.class);
        String cron = scheduled.cron();
        assertTrue(cron.startsWith("${") && cron.endsWith("}"), "cron 必须写成占位符形式: " + cron);
        assertTrue(cron.contains(":"), "缺少兜底默认值，去掉 yml 配置就会启动失败: " + cron);
        return cron.substring(cron.indexOf(':') + 1, cron.length() - 1);
    }
}
