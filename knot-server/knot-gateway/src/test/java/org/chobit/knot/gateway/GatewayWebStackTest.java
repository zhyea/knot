package org.chobit.knot.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住网关的 Web 栈选型：<b>必须是 REACTIVE（Netty）</b>。
 *
 * <p>Boot 的判定规则是「有 WebFlux 且<b>没有</b> WebMvc 才算 REACTIVE」。
 * 批1 期间两个 starter 并存，容器仍是 SERVLET；2026-10-20 批2 移除
 * {@code spring-boot-starter-web} 后翻转为 REACTIVE，本类同步改断言方向。</p>
 *
 * <p>为什么值得钉死：一旦有人把 {@code spring-boot-starter-web} 加回来（哪怕只是某个模块传递进来），
 * 容器会静默退回 Tomcat，届时全部 {@code WebFilter}、{@code Mono} 返回值、
 * {@code ServerWebExchange} 入参<b>同时失效</b>，而编译期完全看不出来——只有真启才会暴露。</p>
 */
class GatewayWebStackTest {

    /**
     * Boot 的三个判定指示类（与 {@code WebApplicationType.deduceFromClasspath()} 内部一致；
     * 该方法不是 public，无法直接调用，故在这里等价复刻其判定条件）。
     */
    private static final String WEBFLUX_INDICATOR = "org.springframework.web.reactive.DispatcherHandler";
    private static final String WEBMVC_INDICATOR = "org.springframework.web.servlet.DispatcherServlet";
    private static final String SERVLET_INDICATOR = "jakarta.servlet.Servlet";

    @Test
    @DisplayName("WebFlux 在 classpath 上")
    void webfluxIsOnClasspath() {
        assertTrue(present(WEBFLUX_INDICATOR), "WebFlux 是网关唯一的 Web 栈");
    }

    @Test
    @DisplayName("WebMvc 不在 classpath 上：否则 Boot 会静默退回 Tomcat")
    void webMvcIsAbsent() {
        assertFalse(present(WEBMVC_INDICATOR),
                "spring-webmvc 一旦出现在 classpath 上，Boot 会推断 SERVLET——"
                        + "WebFilter / Mono 返回值 / ServerWebExchange 入参全部静默失效");
    }

    @Test
    @DisplayName("Servlet API 不在 classpath 上：网关代码不得再依赖它")
    void servletApiIsAbsent() {
        assertFalse(present(SERVLET_INDICATOR),
                "jakarta.servlet 缺失才是「Servlet 痕迹清干净」的硬证据；"
                        + "它一旦回来，说明有人又把 spring-boot-starter-web 拖进来了");
    }

    private static boolean present(String className) {
        return ClassUtils.isPresent(className, GatewayWebStackTest.class.getClassLoader());
    }
}
