package org.chobit.knot.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
}
