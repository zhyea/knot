package org.chobit.knot.gateway.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守门：<b>网关对外接口一律 POST，不提供 GET</b>（{@code 10-backend-java.md §5.2}）。
 *
 * <p>为什么值得一条全包扫描的守门，而不是靠评审：规范腐化的方式是「下一个接口顺手写成
 * {@code @GetMapping}」——它编译通过、单测绿、联调也通，唯一的代价在半年后（句柄进了代理日志、
 * 被中间层预取重放、想加副作用时契约已经发出去）。单点测试守不住「新增的类」，故扫整个
 * {@code gateway.controller} 包。</p>
 *
 * <p>⚠ 扫描到 0 个控制器也判失败：那是守门自己失效（过滤器写错 / 包搬了），
 * 空集合上的断言永远绿。</p>
 */
class GatewayHttpMethodGuardTest {

    /** 对外入口全部在此包下；新控制器只要挂 {@code @RestController} 就自动纳入守门。 */
    private static final String CONTROLLER_PACKAGE = "org.chobit.knot.gateway.controller";

    @Test
    @DisplayName("网关 controller 包不出现 GET 映射（含类级 @RequestMapping(method = GET)）")
    void noGetEndpoint() throws ClassNotFoundException {
        List<Class<?>> controllers = scanControllers();
        assertFalse(controllers.isEmpty(), "扫不到任何 @RestController：守门已失效，别当成通过");
        assertTrue(controllers.contains(GatewayTaskController.class),
                "扫描结果里没有 GatewayTaskController：要么包搬了，要么排除逻辑把生产类也排掉了");

        List<String> violations = new ArrayList<>();
        for (Class<?> type : controllers) {
            collectGetMappings(type.getAnnotation(RequestMapping.class), type.getSimpleName(), violations);
            for (Method method : type.getDeclaredMethods()) {
                collectGetMappings(AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class),
                        type.getSimpleName() + "#" + method.getName(), violations);
            }
        }

        assertTrue(violations.isEmpty(), () -> "网关对外接口一律 POST（10-backend-java.md §5.2，GET 需举证）：\n  "
                + String.join("\n  ", violations)
                + "\n改为 @PostMapping；句柄留在路径上即可（POST 允许无请求体）。");
    }

    /**
     * 收集一处 GET 映射。
     *
     * <p>判据用「合并后的 {@code RequestMapping.method} 含 {@link RequestMethod#GET}」而不是
     * 只看 {@code @GetMapping}：{@code @PostMapping} / {@code @GetMapping} 都是
     * {@code @RequestMapping} 的派生注解，合并后 method 才是唯一事实；只看派生注解会漏掉
     * 手写 {@code @RequestMapping(method = GET)} 的那种（正是「顺手写成」最容易的形态）。</p>
     */
    private void collectGetMappings(RequestMapping mapping, String target, List<String> violations) {
        if (mapping == null || !Arrays.asList(mapping.method()).contains(RequestMethod.GET)) {
            return;
        }
        violations.add(target + " → " + Arrays.toString(mapping.value()) + " " + Arrays.toString(mapping.method()));
    }

    private List<Class<?>> scanControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(CONTROLLER_PACKAGE);
        Set<Class<?>> types = new LinkedHashSet<>();
        for (BeanDefinition candidate : candidates) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (isTestClasspath(type)) {
                continue;
            }
            types.add(type);
        }
        return new ArrayList<>(types);
    }

    /**
     * 测试夹具也会挂 {@code @RestController}（如 {@code GatewayTrafficRejectResponseTest.RejectController}
     * 用 {@code GET /reject} 验流量拒绝响应），它们不产生对外契约。不排掉就是「守门守的是测试代码」——
     * 首个红箭头指向夹具而不是生产端点，守门随即被注释掉。
     */
    private boolean isTestClasspath(Class<?> type) {
        ProtectionDomain domain = type.getProtectionDomain();
        if (domain == null || domain.getCodeSource() == null || domain.getCodeSource().getLocation() == null) {
            return false;
        }
        return domain.getCodeSource().getLocation().getPath().contains("test-classes");
    }
}
