package org.chobit.knot.admin.auth;

import org.chobit.knot.admin.auth.AdminAuthorizationInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端接口级授权的覆盖率守卫。
 *
 * <p>背景：{@link AdminAuthorizationInterceptor} 采用「默认拒绝」——{@code /api/**} 的
 * handler 必须在 {@code ks_api_permission_bindings} 里有绑定，否则
 * {@link AuthorizationAccessService#validateApiAccess} 直接抛 ForbiddenException。
 * 所以「新增 Controller 端点却忘了登记绑定」不会报编译错，只在运行期表现为 403，
 * 且 ADMIN 的全量角色授权也绕不过这一层。真实案例：2026-10-20 路由规则↔消费者 1:1
 * 改造新增的 {@code GET /api/routing-consumers/code/{code}} 漏配绑定，测试抽屉取不到密钥。</p>
 *
 * <p>本守卫用反射遍历所有 {@code @Controller} 的映射（不启动 Spring 容器、不连库），
 * 与 {@code data.sql} 中的绑定记录求差集。白名单路径与 {@code WebMvcConfig} 的
 * {@code excludePathPatterns} 保持人工同步——那里新增免鉴权路径时，请一并补到
 * {@link #WHITELIST}，让豁免是显式写出来的，而不是悄悄生效。</p>
 */
class ApiPermissionBindingCoverageTest {

    /** 只扫生产控制器包：包下全是正式端点，测试夹具里的桩 Controller 不在其中 */
    private static final String CONTROLLER_BASE_PACKAGE = "org.chobit.knot.admin.controller";

    private static final String DATA_SQL = "db/data.sql";

    /**
     * {@code WebMvcConfig#addInterceptors} 中对 adminAuthorizationInterceptor 豁免的路径。
     * 与配置同源，改动需双向同步。
     */
    private static final Set<String> WHITELIST = Set.of(
            "/api/auth/login",
            "/api/auth/force-password-change",
            "/api/auth/logout",
            "/api/health",
            "/api/me/authorizations",
            "/api/enum-options"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 反向守门的例外：确实存在、但不在 {@code admin.controller} 包内的端点。
     * springdoc 的 {@code OpenApiWebMvcResource} 是第三方 {@code @RestController}，扫描不到，
     * 它的绑定只能人工登记在这里——新增一条要能说出理由。
     */
    private static final Set<String> EXTERNAL_BINDINGS = Set.of(
            "GET /api/docs/openapi.json"
    );

    @Test
    void everyAdminEndpointHasPermissionBinding() throws Exception {
        Map<String, Set<String>> endpoints = scanEndpoints();
        Set<String> bindings = parseBindings(readDataSql());

        assertTrue(!endpoints.isEmpty(), "未在 " + CONTROLLER_BASE_PACKAGE + " 下扫描到任何 @Controller 映射，扫描逻辑可能已失效");
        assertTrue(!bindings.isEmpty(), "data.sql 中未解析到 ks_api_permission_bindings 记录，解析逻辑可能已失效");

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : new TreeMap<>(endpoints).entrySet()) {
            String path = entry.getKey();
            if (!path.startsWith("/api/") || WHITELIST.contains(path)) {
                continue;
            }
            for (String verb : entry.getValue()) {
                if (!bindings.contains(verb + " " + path)) {
                    missing.add(verb + " " + path);
                }
            }
        }
        missing.sort(Comparator.naturalOrder());

        assertTrue(missing.isEmpty(),
                () -> "以下管理端接口未登记 ks_api_permission_bindings（默认拒绝，运行期表现为 403）：\n  "
                        + String.join("\n  ", missing)
                        + "\n补登记位置：knot-admin/src/main/resources/db/data.sql，"
                        + "既有库另需同目录下的幂等迁移脚本（docs/database/migration/，执行完即删）。");
    }

    /**
     * 运行时接口文档路径必须「已绑定或已白名单」。
     *
     * <p>springdoc 的 {@code OpenApiWebMvcResource} 是第三方 {@code @RestController}，不在
     * {@link #CONTROLLER_BASE_PACKAGE} 扫描范围内——{@link #everyAdminEndpointHasPermissionBinding()}
     * 结构上抓不到它。这里直接读 {@code springdoc.api-docs.path} 的配置值做断言，
     * 路径改了测试跟着变，不靠人记常量。</p>
     */
    @Test
    void springdocPathIsBoundOrWhitelisted() throws Exception {
        String path = springdocApiDocsPath();
        assertTrue(path != null && path.startsWith("/api/"),
                "springdoc.api-docs.path 必须配在 /api/** 下才受授权拦截器保护，当前：" + path);

        Set<String> bindings = parseBindings(readDataSql());
        assertTrue(WHITELIST.contains(path) || bindings.contains("GET " + path),
                "运行时接口文档 " + path + " 既未登记 ks_api_permission_bindings，也不在授权白名单里；"
                        + "默认拒绝会让它返回 403（登录后也看不了）。补登记位置：knot-admin/src/main/resources/db/data.sql。");
    }

    /**
     * 读取 {@code springdoc.api-docs.path} 的配置值；取不到返回 {@code null}。
     */
    @SuppressWarnings("unchecked")
    private String springdocApiDocsPath() throws IOException {
        ClassPathResource resource = new ClassPathResource("application.yml");
        try (InputStream in = resource.getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            if (!(root.get("springdoc") instanceof Map<?, ?> springdoc)) {
                return null;
            }
            Map<String, Object> springdocMap = (Map<String, Object>) springdoc;
            if (!(springdocMap.get("api-docs") instanceof Map<?, ?> apiDocs)) {
                return null;
            }
            Object path = ((Map<String, Object>) apiDocs).get("path");
            return path == null ? null : path.toString();
        }
    }

    /**
     * 反向守卫：绑定不得指向已删掉的端点。
     *
     * <p>正向守卫只查「端点 → 有没有绑定」，拦不住「删了接口忘了删绑定」——残留绑定会让权限树上
     * 永远挂着一个打不到的接口，白扩攻击面。实测判例：2026-11-17 下线 3 个操作日志 GET 与枚举日志
     * GET 时，正向守卫全绿，漏删的绑定只能靠这条兜住。</p>
     */
    @Test
    void everyBindingPointsToAnExistingEndpoint() throws Exception {
        Map<String, Set<String>> normalizedEndpoints = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : scanEndpoints().entrySet()) {
            normalizedEndpoints.computeIfAbsent(normalizePath(entry.getKey()), key -> new TreeSet<>())
                    .addAll(entry.getValue());
        }

        List<String> orphans = new ArrayList<>();
        for (String binding : parseBindings(readDataSql())) {
            if (EXTERNAL_BINDINGS.contains(binding)) {
                continue;
            }
            int space = binding.indexOf(' ');
            String verb = binding.substring(0, space);
            String path = normalizePath(binding.substring(space + 1));
            Set<String> verbs = normalizedEndpoints.get(path);
            if (verbs == null || !verbs.contains(verb)) {
                orphans.add(binding);
            }
        }
        orphans.sort(Comparator.naturalOrder());

        assertTrue(orphans.isEmpty(),
                () -> "以下 ks_api_permission_bindings 指向已不存在的端点（接口删了、绑定没删）：\n  "
                        + String.join("\n  ", orphans)
                        + "\n清理位置：knot-admin/src/main/resources/db/data.sql，"
                        + "既有库另需幂等迁移脚本（docs/database/migration/，执行完即删）。");
    }

    /**
     * 路径归一：{@code {任意变量名}} 一律折成 {@code {}}，避免「绑定写 {@code {id}}、端点写
     * {@code {roleId}}」这类仅变量改名的误报。
     */
    private static String normalizePath(String path) {
        return path.replaceAll("\\{[^/}]+}", "{}");
    }

    /**
     * 扫描 classpath 下所有 {@code @Controller} 的 URL 映射，返回 path -&gt; HTTP methods。
     */
    private Map<String, Set<String>> scanEndpoints() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        Map<String, Set<String>> result = new LinkedHashMap<>();
        ClassLoader loader = getClass().getClassLoader();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_BASE_PACKAGE)) {
            String className = definition.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> type = Class.forName(className, false, loader);
            if (isTestClass(type)) {
                // 测试夹具里的桩 Controller（拦截器单测的 /api/ping 等）不属于生产端点
                continue;
            }
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String base = (classMapping == null || classMapping.path().length == 0)
                    ? ""
                    : classMapping.path()[0];
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String pattern = (mapping.path().length == 0)
                        ? base
                        : pathMatcher.combine(base, mapping.path()[0]);
                result.computeIfAbsent(pattern, key -> new TreeSet<>()).addAll(verbsOf(mapping));
            }
        }
        return result;
    }

    /**
     * 判断类是否来自 test-classes（allowed to be skipped）。
     */
    private boolean isTestClass(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        return source != null
                && source.getLocation() != null
                && source.getLocation().toString().contains("test-classes");
    }

    /**
     * 未显式声明 method 时视为接受全部动词（与 Spring 的默认口径一致）。
     */
    private Set<String> verbsOf(RequestMapping mapping) {
        Set<String> verbs = new TreeSet<>();
        RequestMethod[] methods = mapping.method();
        if (methods.length == 0) {
            for (RequestMethod each : RequestMethod.values()) {
                verbs.add(each.name());
            }
            return verbs;
        }
        for (RequestMethod each : methods) {
            verbs.add(each.name());
        }
        return verbs;
    }

    /**
     * 读取 classpath 中的 {@code data.sql} 全文。
     */
    private String readDataSql() throws IOException {
        ClassPathResource resource = new ClassPathResource(DATA_SQL);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final Pattern BINDING = Pattern.compile(
            "\\(\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*'([A-Z]+)'\\s*,\\s*'([^']+)'");

    /**
     * 解析 {@code ks_api_permission_bindings} 插入块，返回 "METHOD path" 集合。
     */
    private Set<String> parseBindings(String dataSql) {
        int start = dataSql.indexOf("INSERT IGNORE INTO ks_api_permission_bindings");
        assertTrue(start >= 0, "data.sql 中找不到 ks_api_permission_bindings 插入块");
        int end = dataSql.indexOf(';', start);
        String block = end > 0 ? dataSql.substring(start, end) : dataSql.substring(start);

        Set<String> bindings = new TreeSet<>();
        for (String rawLine : block.split("\n")) {
            // ⚠ 必须逐行剔注释：整块做正则会把「注释掉的历史绑定」也算成已登记，
            //   那样新增端点漏登记时守卫反而哑火（实测踩过）。
            String line = rawLine.trim();
            if (line.startsWith("--") || line.startsWith("INSERT")) {
                continue;
            }
            int comment = line.indexOf(" --");
            if (comment >= 0) {
                line = line.substring(0, comment).trim();
            }
            Matcher matcher = BINDING.matcher(line);
            if (matcher.find()) {
                bindings.add(matcher.group(1) + " " + matcher.group(2));
            }
        }
        return bindings;
    }
}
