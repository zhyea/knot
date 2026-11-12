package org.chobit.knot.gateway;

import org.chobit.knot.gateway.async.AsyncTaskQueryClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 上下文装配冒烟：<b>网关里唯一能证明「新增组件真的进得了容器」的测试</b>。
 *
 * <p>⭐ 为什么需要它（2026-10-29 补）：单元测试全部是手工 {@code new}，它们证明不了
 * {@code @Component} 的构造器参数在容器里都找得到。{@code AsyncTaskLookupRunner} 这类
 * 新组件若依赖缺失或循环，编译与 1300 多个单测全绿，坏的是启动——而启动失败只有真启或
 * 本测试能发现。admin 早就有等价的一份（{@code AppControllerTest#contextLoads}），
 * 网关一直缺，是这次「controller 瘦身」新增组件时暴露的。</p>
 *
 * <p>⚠ 它守的是「装配」，不是「业务」：断言为空正是目的——起得来就是通过。
 * 真启 jar 冒烟仍不可省（本测试用 {@code MOCK} 环境，不启 Netty、不读真实上游）。</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class GatewayContextTest {

    @Test
    void contextLoads() {
    }

    /**
     * ⭐ 守门：{@code AsyncTaskQueryClient} 的 {@code PluginDispatcher} 注入。
     *
     * <p>该类是异步查询链路上唯一「手工 new 不出来」的组件（6 参构造，其中
     * {@code PluginDispatcher} 是 2026-11-12 新增）。单元测试全部手工构造，证明不了这个
     * 构造参数在容器里找得到；一旦 plugin 模块的 bean 缺失或被条件装配关掉，编译与 1500+
     * 单测全绿，坏的是启动 —— 而异步查询侧是 fail-open，插件日志会整段静默，
     * 表现为「没配日志插件」，比启动失败更难发现。</p>
     *
     * <p>反射读私有字段是有意的：{@code contextLoads} 只能证明「构造没抛」，
     * 证明不了「注入的不是 null」。字段被改名时本测试会直接失败，那正是要人去更新它的时机。</p>
     */
    @Test
    @DisplayName("装配：AsyncTaskQueryClient 的 PluginDispatcher 真的注入进去了（不是 null）")
    void asyncTaskQueryClientGetsPluginDispatcher(@Autowired AsyncTaskQueryClient queryClient)
            throws NoSuchFieldException, IllegalAccessException {
        assertNotNull(queryClient, "AsyncTaskQueryClient 未进容器");

        Field field = AsyncTaskQueryClient.class.getDeclaredField("pluginDispatcher");
        field.setAccessible(true);
        assertNotNull(field.get(queryClient),
                "pluginDispatcher 为 null：异步查询的 UPSTREAM_REQUEST/RESPONSE/ERROR 三阶段日志会整段静默");
    }
}
