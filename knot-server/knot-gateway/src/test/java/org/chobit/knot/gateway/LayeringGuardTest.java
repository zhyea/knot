package org.chobit.knot.gateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分层红线守卫：gateway 不得直接访问 {@code knot-dal} 的 Mapper。
 *
 * <p><b>为什么它必须是一个测试</b>：红线写进规范后，如果只能靠 review 记住，
 * 那么第一个赶时间的人就会破例，而下一个人看到「这里已经有一处了」就会再加一处。
 * 这条约束是<b>纯静态可判</b>的（有没有 import {@code dal.mapper} 是一行 grep 的事），
 * 所以不能停在文档里——能判的就钉住，跟 {@code MapperStatementCoverageTest} 同理。</p>
 *
 * <p><b>判据的边界</b>：只禁 {@code org.chobit.knot.dal.mapper}，不禁 {@code dal.entity}。
 * 这不是松口，是当前状态：{@code GatewayEntityCacheService} 仍在用 dal 实体做本地缓存
 * （那是另一处待改的违规，改动面大、单独排期）。先守住「谁执行 SQL」这条最硬的线——
 * 实体只是数据结构，Mapper 才是「数据访问发生在哪一层」的开关。
 * ⚠ 等那一处也收干净之后，这里的判据应当收紧到整个 {@code org.chobit.knot.dal}。</p>
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

    /** 豁免：启动类的 {@code @MapperScan} 需要 mapper 类字面量 */
    private static final String SCAN_EXEMPT = "GatewayApplication.java";

    /**
     * ⚠ <b>存量违规登记（2026-10-28），不是永久豁免</b>。
     *
     * <p>名单里的文件仍然直接 import Mapper，属于「本轮没改完」的部分：
     * {@code GatewayEntityCacheService} 把 10 张配置表预热成本地缓存，改动面大、单独排期
     * （异步任务那一套已经收进 {@code org.chobit.knot.svc.service}，是这个方向的样板）。</p>
     *
     * <p>⭐ <b>这份名单会自我失效</b>：下面 {@code registeredOffendersStillOffend} 断言每个登记项
     * <b>现在确实还在违规</b>。一旦有人把它改干净，那条断言立刻变红并提示「从名单里删掉」——
     * 于是名单不会变成永久避难所。这是与「注释里写 TODO」唯一的实质区别：TODO 会静默腐烂，
     * 这条会响。</p>
     */
    private static final List<String> KNOWN_OFFENDERS = List.of(
            "src/main/java/org/chobit/knot/gateway/service/GatewayEntityCacheService.java");

    @Test
    void noMapperImportOutsideTheScanConfiguration() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String name = file.getFileName().toString();
            if (SCAN_EXEMPT.equals(name)) {
                continue;
            }
            String path = relative(file);
            if (KNOWN_OFFENDERS.contains(path)) {
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

    /** 名单里的每一项都必须仍然违规；改干净了就把它从 {@link #KNOWN_OFFENDERS} 里删掉 */
    @Test
    void registeredOffendersStillOffend() throws Exception {
        List<String> stale = new ArrayList<>();
        for (String path : KNOWN_OFFENDERS) {
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
                "以下登记项已经改干净，请从 KNOWN_OFFENDERS 里删掉——留着会让新违规藏进名单：\n  "
                        + String.join("\n  ", stale));
    }

    /** 扫描路径写错会让上面那条「空跑通过」，所以显式钉住扫描规模 */
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

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(SOURCE_ROOT)) {
            return walk.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String relative(Path file) {
        return file.toString().replace('\\', '/');
    }
}
