package org.chobit.knot.gateway;

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
 * 分层红线守卫：gateway 不得直接访问 {@code knot-dal} —— Mapper 不行，实体/查询对象也不行。
 *
 * <p><b>为什么它必须是一个测试</b>：红线写进规范后，如果只能靠 review 记住，
 * 那么第一个赶时间的人就会破例，而下一个人看到「这里已经有一处了」就会再加一处。
 * 这条约束是<b>纯静态可判</b>的（有没有 import 是一行 grep 的事），
 * 所以不能停在文档里——能判的就钉住，跟 {@code MapperStatementCoverageTest} 同理。</p>
 *
 * <p>⭐ <b>两个 app 的红线必须用同一把尺子量。</b>admin 那份（{@code knot-admin/.../LayeringGuardTest}）
 * 早就有「§3.1 配套纪律 × 2」：Mapper 棘轮 + 非 Mapper 棘轮。本类原先<b>只有 Mapper 那半</b>，
 * 结果是实体引用从规范登记的 1 个长到实测的 10 个，而<b>没有任何测试会响</b>——
 * 少了一半的尺子，等于这半条红线只写在文档里。2026-10-31 补齐并当天清零，两个名单现在都为空。</p>
 *
 * <p><b>为什么豁免启动类</b>：{@code GatewayApplication} 上的
 * {@code @MapperScan(basePackageClasses = AppMapper.class)} 引用的是 Mapper <b>类字面量</b>，
 * 规范要求必须用这种形式（而不是字符串包名，见 {@code 10-backend-java.md §1.1}），
 * 所以它必然要 import 一个 mapper。它注册 bean，不执行 SQL——与「注入 mapper 自己查库」是两回事。</p>
 */
class LayeringGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    private static final Pattern MAPPER_IMPORT =
            Pattern.compile("^\\s*import\\s+org\\.chobit\\.knot\\.dal\\.mapper\\.", Pattern.MULTILINE);

    /**
     * ⭐ 判据刻意写成「整个 {@code dal} 包，但排除 {@code mapper} 子包」，而不是只盯 {@code entity}。
     *
     * <p>排除 mapper 是因为上面那条正则已经专门管它，而且报错信息更具体（直指 svc 的哪个服务）；
     * 两条重叠只会让同一个文件报两遍。</p>
     *
     * <p>盯整个包的理由：{@code dal} 目前只有 {@code entity} 和 {@code mapper} 两个子包，
     * 所以现在的行为与「只盯 entity」完全等价——<b>但将来 dal 长出查询对象、审计快照之类的子包时，
     * 这条判据会自动覆盖</b>，不需要有人记得回来改测试。红线是「gateway 不许碰 dal」，
     * 判据就该照红线的原话写，不该照着今天的包结构写。</p>
     */
    private static final Pattern NON_MAPPER_DAL_IMPORT =
            Pattern.compile("^\\s*import\\s+org\\.chobit\\.knot\\.dal\\.(?!mapper\\.)", Pattern.MULTILINE);

    /** 豁免：启动类的 {@code @MapperScan} 需要 mapper 类字面量 */
    private static final String SCAN_EXEMPT = "GatewayApplication.java";

    /**
     * ⚠ <b>存量违规登记（Mapper 侧）—— 2026-10-31 已清零，本名单保持为空。</b>
     *
     * <p>历史：{@code GatewayEntityCacheService} 把 10 张配置表预热成本地缓存，直接注入 11 个 Mapper。
     * 收口方式是把它拆成两半——数据访问搬到 {@code svc.service.GatewayConfigQueryService}，
     * 缓存留在 gateway 改名 {@code GatewayConfigCacheService}（一个 Mapper 都不注入）。</p>
     *
     * <p>⭐ <b>这份名单会自我失效</b>：{@code registeredMapperOffendersStillOffend} 断言每个登记项
     * <b>现在确实还在违规</b>。一旦有人把它改干净，那条断言立刻变红并提示「从名单里删掉」——
     * 于是名单不会变成永久避难所。这是与「注释里写 TODO」唯一的实质区别：TODO 会静默腐烂，这条会响。
     * 上面这次清零就是被这条断言逼出来的。</p>
     *
     * <p>留空而不是删掉机制：下一个人再遇到「改动面太大要分两步走」时，往这里登记一项即可，
     * 而他必须同时接受「改完那天测试会红着催他删」。</p>
     */
    private static final List<String> KNOWN_MAPPER_OFFENDERS = List.of();

    /**
     * ⚠ <b>存量违规登记（非 Mapper 的 dal 引用）—— 2026-10-31 已清零，本名单保持为空。</b>
     *
     * <p>历史（当天如实盘点出的 10 个文件，形状是「一个源头 + 九个下游」）：
     * {@code GatewayEntityCacheService} 吐 dal 实体，下游为了读字段只能跟着 import 实体类型，
     * 于是 11 种实体扩散到 9 个类。这与 admin 那批（响应体直返实体）不同：<b>网关不对外出实体</b>，
     * 实体只在本进程内被路由/上游/异步任务链路消费，所以问题不是「契约外泄」，
     * 而是「svc 的载体没覆盖网关需要的字段，下游只能退回实体」。</p>
     *
     * <p>收口结果：{@code svc.vo.gateway} 下 11 个配置快照载体（{@code XxxConfigVo}）+
     * {@code svc.converter.GatewayConfigConverter}（MapStruct）+ {@code GatewayConfigQueryService}，
     * 下游 9 个文件 148 处 getter 全改访问器。棘轮两半同时归零。</p>
     *
     * <p>⭐ <b>为什么没复用 admin 的同名 VO</b>：那批 record 是<b>展示语义</b>——例如
     * {@code ModelApiBindingItem} 是 {@code boolean enabled}，而网关读的是
     * {@code binding.getStatus()}（{@code EnabledStatusEnum} 整型码）。字段名与类型都不同，
     * 套用会逼着下游改判定口径，比现状更糟。网关要的是「配置快照」：字段集由路由决策决定，
     * 语义与实体一一对应。</p>
     */
    private static final List<String> KNOWN_NON_MAPPER_DAL_IMPORTS = List.of();

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
                "gateway 不得直接 import dal 的 Mapper：数据访问统一收在 knot-service（svc）。"
                        + "需要读写异步任务时用 org.chobit.knot.svc.service.AsyncTaskService / AsyncTaskProfileService。\n  违规文件："
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

    /** 棘轮第一半：不许出现清单之外的新 dal 引用（mapper 除外，它由上面那条专门管） */
    @Test
    void noNewNonMapperDalLeaksIntoGateway() throws Exception {
        Set<String> additions = new TreeSet<>(nonMapperDalImporters());
        additions.removeAll(KNOWN_NON_MAPPER_DAL_IMPORTS);
        assertTrue(additions.isEmpty(),
                "新增了直接 import org.chobit.knot.dal 的类（mapper 之外）。gateway 侧的实体消费应当走"
                        + " svc 的配置快照载体（org.chobit.knot.svc.vo.gateway.*），实体只允许出现在"
                        + " svc 的 Converter 里。\n  新增：\n  "
                        + String.join("\n  ", additions));
    }

    /** 棘轮第二半：清单里的项一旦改干净就必须删掉，否则清单会慢慢变成「历史记录」而失去意义 */
    @Test
    void dalImportInventoryHasNoStaleEntries() throws Exception {
        Set<String> current = nonMapperDalImporters();
        List<String> stale = KNOWN_NON_MAPPER_DAL_IMPORTS.stream().filter(path -> !current.contains(path)).toList();
        assertTrue(stale.isEmpty(),
                "以下文件已经不再 import dal（mapper 之外），请从 KNOWN_NON_MAPPER_DAL_IMPORTS 里删掉：\n  "
                        + String.join("\n  ", stale));
    }

    /** 扫描路径写错会让上面几条「空跑通过」，所以显式钉住扫描规模 */
    @Test
    void doesNotSilentlySkipBecauseOfEmptyScan() throws Exception {
        List<Path> files = javaFiles();
        assertTrue(Files.isDirectory(SOURCE_ROOT),
                "找不到源码目录，扫描口径失效：" + SOURCE_ROOT.toAbsolutePath());
        assertFalse(files.isEmpty(), "没扫到任何源文件");
        assertTrue(files.size() > 100, "只扫到 " + files.size() + " 个源文件，疑似路径不对");
        assertTrue(files.stream().anyMatch(file -> SCAN_EXEMPT.equals(file.getFileName().toString())),
                "没扫到启动类，豁免名单可能已经失效（类改名后这里会一直绿）");
    }

    private static Set<String> nonMapperDalImporters() throws IOException {
        Set<String> result = new TreeSet<>();
        for (Path file : javaFiles()) {
            if (NON_MAPPER_DAL_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
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
