package org.chobit.knot.admin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分层红线守卫：admin 不得直接访问 {@code knot-dal} 的 Mapper。
 *
 * <p><b>为什么它必须是一个测试</b>：规范 {@code 10-backend-java.md §3.1} 已经写明
 * 「只有 {@code knot-service} 能碰 Mapper」，但写在文档里的约束只能靠人记住；
 * 而这条约束<b>纯静态可判</b>（有没有 import {@code dal.mapper} 是一行 grep 的事），
 * 能判的就不该停在文档里。与 {@code knot-gateway/.../LayeringGuardTest} 同源同判据——
 * 两个 app 的红线必须用同一把尺子量，否则「网关收干净了、管理端还在散着」没人会发现。</p>
 *
 * <p><b>本轮（2026-10-28 分层改造③）改掉了什么</b>：定时任务模块原先有 4 个类各自注入
 * {@code ScheduledTaskMapper}（{@code ScheduledTaskService} / {@code ScheduledTaskSchedulerService} /
 * {@code ScheduledTaskExecutor} / {@code ScheduleRunRetentionHandler}）——同一张表 4 个写入入口，
 * 「这张表谁能改」没有单点答案。现在数据访问收在 {@code org.chobit.knot.svc.service}
 * （{@code org.chobit.knot.svc.service.ScheduledTaskService}），
 * 调度基础设施（Quartz）仍留在 admin，因为「这个进程要不要跑定时任务」是部署问题、
 * 不是数据访问问题。</p>
 *
 * <p><b>为什么豁免启动类</b>：{@code AdminApplication} 上的
 * {@code @MapperScan(basePackageClasses = AppMapper.class)} 引用的是 Mapper <b>类字面量</b>
 * （规范要求就这么写，禁字符串包名），所以它必然 import 一个 mapper。
 * 它注册 bean，不执行 SQL——与「注入 mapper 自己查库」是两回事。</p>
 */
class LayeringGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    private static final Pattern MAPPER_IMPORT =
            Pattern.compile("^\\s*import\\s+org\\.chobit\\.knot\\.dal\\.mapper\\.", Pattern.MULTILINE);

    private static final Pattern ENTITY_IMPORT =
            Pattern.compile("^\\s*import\\s+org\\.chobit\\.knot\\.dal\\.entity\\.", Pattern.MULTILINE);

    /** 豁免：启动类的 {@code @MapperScan} 需要 mapper 类字面量 */
    private static final String SCAN_EXEMPT = "AdminApplication.java";

    /**
     * 存量违规登记：当前<b>空名单</b>——定时任务收进 svc 之后，admin 侧注入 Mapper 的类归零。
     *
     * <p>⭐ 这份名单会自我失效（见 {@code registeredMapperOffendersStillOffend}）：
     * 登记项一旦改干净，那条断言立刻变红并提示删掉它，所以名单不会变成永久避难所。
     * 需要新增登记项的场合基本只有一种：改动面太大、本轮改不完，
     * 而<b>不是</b>「来不及看规范」。</p>
     */
    private static final List<String> KNOWN_MAPPER_OFFENDERS = List.of();

    /**
     * {@code dal.entity} 的存量清单（2026-10-28 盘点：9 个 controller）。
     *
     * <p>⚠ <b>这不是豁免，是债务盘点 + 只许降不许升的棘轮</b>。
     * 这些 controller 直接把实体当响应体返回（{@code PageResult<XxxEntity>}），
     * 属于同一族红线的另一半：Mapper 决定「谁执行 SQL」，实体决定「层边界在哪」。
     * 本轮范围只到 Mapper（robin 明确的「admin/gateway 层不宜出现 Mapper 直接调用」），
     * 但<b>不让它继续变多</b>：新加一个就得先删掉一个。</p>
     *
     * <p>为什么这些还没收：把实体换成 svc 的 record 会改动对外 JSON 契约（字段收窄、
     * record 的 SpEL 访问器写法），9 个 controller 逐个要过前端，属于单独立项。</p>
     */
    private static final List<String> KNOWN_ENTITY_IMPORTS = List.of(
            "src/main/java/org/chobit/knot/admin/controller/AuthorizationApiBindingController.java",
            "src/main/java/org/chobit/knot/admin/controller/AuthorizationMenuController.java",
            "src/main/java/org/chobit/knot/admin/controller/AuthorizationModuleController.java",
            "src/main/java/org/chobit/knot/admin/controller/AuthorizationPermissionController.java",
            "src/main/java/org/chobit/knot/admin/controller/AuthorizationRoleController.java",
            "src/main/java/org/chobit/knot/admin/controller/EnumConfigController.java",
            "src/main/java/org/chobit/knot/admin/controller/ExternalModelController.java",
            "src/main/java/org/chobit/knot/admin/controller/ModelFamilyController.java",
            "src/main/java/org/chobit/knot/admin/controller/OperationLogController.java");

    @Test
    void noMapperImportOutsideTheScanConfiguration() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (SCAN_EXEMPT.equals(file.getFileName().toString())) {
                continue;
            }
            String path = relative(file);
            if (KNOWN_MAPPER_OFFENDERS.contains(path)) {
                continue;
            }
            if (MAPPER_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                offenders.add(path);
            }
        }
        assertTrue(offenders.isEmpty(),
                "admin 不得直接 import dal 的 Mapper：数据访问统一收在 knot-service（svc）。"
                        + "定时任务用 org.chobit.knot.svc.service.ScheduledTaskService，"
                        + "异步任务用 org.chobit.knot.svc.service.AsyncTaskService / AsyncTaskProfileService。\n  违规文件："
                        + String.join("\n  ", offenders));
    }

    /** 名单里的每一项都必须仍然违规；改干净了就把它从 {@link #KNOWN_MAPPER_OFFENDERS} 里删掉 */
    @Test
    void registeredMapperOffendersStillOffend() throws Exception {
        List<String> stale = new ArrayList<>();
        for (String path : KNOWN_MAPPER_OFFENDERS) {
            Path file = Path.of(path);
            if (!Files.isRegularFile(file)) {
                stale.add(path + "（文件已不存在）");
                continue;
            }
            if (!MAPPER_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                stale.add(path + "（已经不再 import Mapper）");
            }
        }
        assertTrue(stale.isEmpty(),
                "以下登记项已经改干净，请从 KNOWN_MAPPER_OFFENDERS 里删掉——留着会让新违规藏进名单：\n  "
                        + String.join("\n  ", stale));
    }

    /** 棘轮第一半：不许出现清单之外的新实体引用 */
    @Test
    void noNewEntityLeaksIntoAdmin() throws Exception {
        Set<String> additions = new TreeSet<>(entityImporters());
        additions.removeAll(KNOWN_ENTITY_IMPORTS);
        assertTrue(additions.isEmpty(),
                "新增了直接 import dal.entity 的类。admin 的对外响应应该是 svc 的 record"
                        + "（样板见 ScheduledTaskController），实体只允许出现在 svc 的 Converter 里。\n"
                        + "  新增：\n  " + String.join("\n  ", additions));
    }

    /** 棘轮第二半：清单里的项一旦改干净就必须删掉，否则清单会慢慢变成「历史记录」而失去意义 */
    @Test
    void entityImportInventoryHasNoStaleEntries() throws Exception {
        Set<String> current = entityImporters();
        List<String> stale = KNOWN_ENTITY_IMPORTS.stream().filter(path -> !current.contains(path)).toList();
        assertTrue(stale.isEmpty(),
                "以下文件已经不再 import dal.entity，请从 KNOWN_ENTITY_IMPORTS 里删掉：\n  "
                        + String.join("\n  ", stale));
    }

    /** 扫描路径写错会让上面几条「空跑通过」，所以显式钉住扫描规模 */
    @Test
    void doesNotSilentlySkipBecauseOfEmptyScan() throws Exception {
        List<Path> files = javaFiles();
        assertTrue(Files.isDirectory(SOURCE_ROOT),
                "找不到源码目录，扫描口径失效：" + SOURCE_ROOT.toAbsolutePath());
        assertFalse(files.isEmpty(), "没扫到任何源文件");
        assertTrue(files.size() > 50, "只扫到 " + files.size() + " 个源文件，疑似路径不对");
        assertTrue(files.stream().anyMatch(file -> SCAN_EXEMPT.equals(file.getFileName().toString())),
                "没扫到启动类，豁免名单可能已经失效（类改名后这里会一直绿）");
    }

    private static Set<String> entityImporters() throws IOException {
        Set<String> result = new TreeSet<>();
        for (Path file : javaFiles()) {
            if (ENTITY_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                result.add(relative(file));
            }
        }
        return result;
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(SOURCE_ROOT)) {
            return walk.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String relative(Path file) {
        return file.toString().replace('\\', '/');
    }
}
