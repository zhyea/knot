package org.chobit.knot.admin;

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
 * <b>Controller 必须薄</b>的守卫（规范 {@code 10-backend-java.md §3.3}）。
 *
 * <p>与 {@code knot-gateway/.../ControllerThinGuardTest} 同源同判据——两个 app 的厚度必须用
 * 同一把尺子量，否则「网关的控制器瘦下来了、管理端还塞着编排」不会有人发现。
 * 之所以各写一份而不是放公共测试模块：判的是各自模块的源码路径，跨模块扫描在 CI 里更脆。</p>
 *
 * <p>判据只有三类确定是业务的写法（{@code JsonKit.} / {@code new LinkedHashMap} / {@code switch}），
 * 理由见网关那份的类注释：它们分别对应「自己解 JSON」「自己拼响应体」「自己在入口做状态映射」，
 * 而这三件事在本项目都已有唯一归属（Runner/Support、{@code model.vo.response} 的 record、
 * 枚举本身）。</p>
 *
 * <p><b>双向探针</b>：给任一 controller 加一句 {@code JsonKit.toJson(..)} → {@code noBusinessInControllers}
 * 必红；把名单里的存量改干净 → {@code registeredOffendersStillOffend} 必红并要求删名单。</p>
 */
class ControllerThinGuardTest {

    private static final Path CONTROLLER_DIR =
            Path.of("src/main/java/org/chobit/knot/admin/controller");

    /** 剥注释用：块注释与行注释。启发式即可——注释里讨论「别在这里用 JsonKit」是好注释，不该变红 */
    private static final Pattern BLOCK_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private static final List<Rule> RULES = List.of(
            new Rule("JsonKit", Pattern.compile("\\bJsonKit\\s*\\.")),
            new Rule("new LinkedHashMap", Pattern.compile("new\\s+LinkedHashMap\\s*<")),
            new Rule("switch", Pattern.compile("\\bswitch\\s*\\(")));

    /**
     * ⚠ <b>存量违规登记（2026-10-29 立规当天），不是永久豁免</b>。
     *
     * <ul>
     *   <li>{@code RoutingRuleController}——试跑接口的 SSE 写事件与 {@code meta} 拼装：
     *       事件类型、字段名、命中快照的形状都是业务，理想归属是一个 SSE 写入器 + 事件 record；</li>
     *   <li>{@code DepartmentController}——部门树组装（{@code childrenByParent} + 递归挂载）：
     *       这是纯粹的树算法，与 HTTP 无关，应进 {@code service.support}。</li>
     * </ul>
     * <p>两处都牵动既有接口与前端联调，单独排期；本轮只把「新代码不得再加厚」立起来。</p>
     */
    private static final List<String> KNOWN_OFFENDERS = List.of(
            "RoutingRuleController.java",
            "DepartmentController.java");

    @Test
    void noBusinessInControllers() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : controllerFiles()) {
            if (KNOWN_OFFENDERS.contains(file.getFileName().toString())) {
                continue;
            }
            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            for (Rule rule : RULES) {
                if (rule.pattern().matcher(code).find()) {
                    offenders.add(file.getFileName() + " → " + rule.name());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "controller 只做「绑定入参 + 委托 + HTTP 状态映射」，业务搬到 Service/Support"
                        + "（见 10-backend-java.md §3.3）。违规点：\n  " + String.join("\n  ", offenders));
    }

    /** 名单里的每一项都必须仍然违规；改干净了就把它从 {@link #KNOWN_OFFENDERS} 里删掉 */
    @Test
    void registeredOffendersStillOffend() throws IOException {
        List<String> stale = new ArrayList<>();
        for (String name : KNOWN_OFFENDERS) {
            Path file = CONTROLLER_DIR.resolve(name);
            if (!Files.isRegularFile(file)) {
                stale.add(name + "（文件已不存在，可能已改名）");
                continue;
            }
            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            boolean stillOffends = RULES.stream().anyMatch(rule -> rule.pattern().matcher(code).find());
            if (!stillOffends) {
                stale.add(name + "（已经不再违规）");
            }
        }
        assertTrue(stale.isEmpty(),
                "以下登记项已经改干净，请从 KNOWN_OFFENDERS 里删掉——留着会让新违规藏进名单：\n  "
                        + String.join("\n  ", stale));
    }

    /** 扫描路径写错会让上面两条「空跑通过」，所以显式钉住扫描规模 */
    @Test
    void doesNotSilentlySkipBecauseOfEmptyScan() throws IOException {
        assertTrue(Files.isDirectory(CONTROLLER_DIR),
                "找不到 controller 目录，扫描口径失效：" + CONTROLLER_DIR.toAbsolutePath());
        List<Path> files = controllerFiles();
        assertTrue(files.size() >= 10, "只扫到 " + files.size() + " 个 controller，疑似路径不对");
        assertFalse(RULES.isEmpty(), "规则表空了，等于什么都没守");
    }

    private static List<Path> controllerFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(CONTROLLER_DIR)) {
            return walk.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String stripComments(String code) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(code).replaceAll("")).replaceAll("");
    }

    /** 一条禁用规则：名字进失败信息，正则做判定 */
    private record Rule(String name, Pattern pattern) {
    }
}
