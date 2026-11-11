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
 * <p>判据都是「确定属于业务、且在本项目已经有唯一归属」的写法：</p>
 * <ul>
 *   <li>{@code JsonKit.} / {@code new LinkedHashMap} / {@code switch}——分别对应
 *       「自己解 JSON」「自己拼响应体」「自己在入口做状态映射」，归属是 Runner/Support、
 *       {@code model.vo.response} 的 record、枚举本身；</li>
 *   <li>{@code new XxxDto(}——DTO 只该由 {@code svc.converter} 造（视图↔DTO 的两个方向都在那边），
 *       controller 里出现它说明映射又被搬回入口了；</li>
 *   <li>{@code new Xxx(..., null, null, ...)}——一串 {@code null} 占位是「映射缺字段」的典型信号：
 *       读代码的人无从知道第 9 个 {@code null} 是哪个字段，写代码的人下次就会填错位。</li>
 * </ul>
 *
 * <p><b>双向探针</b>：给任一 controller 加一句 {@code JsonKit.toJson(..)}（或 {@code new XxxDto(..)}、
 * 一串 {@code null} 占位）→ {@code noBusinessInControllers} 必红；把名单里的存量改干净 →
 * {@code registeredOffendersStillOffend} 必红并要求删名单。</p>
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
            new Rule("switch", Pattern.compile("\\bswitch\\s*\\(")),
            new Rule("new XxxDto", Pattern.compile("new\\s+\\w+Dto\\s*\\(")),
            // 同一语句（不跨分号）内出现两个连续 null 实参：不依赖括号配对，故跨行书写也能命中
            new Rule("多 null 构造", Pattern.compile("new\\s+\\w+\\s*\\([^;]*?\\bnull\\s*,\\s*null\\b")));

    /**
     * ⚠ <b>存量违规登记（2026-10-29 立规当天），不是永久豁免</b>。
     *
     * <p>登记粒度是<b>「文件 → 规则」而不是整个文件</b>：早先按文件豁免，导致这两个文件里新增的
     * 其它违规（比如新写一段 {@code new XxxDto(..)}）会被一起放过——豁免一个文件里的一处写法，
     * 不该顺带豁免整份文件。</p>
     *
     * <ul>
     *   <li>{@code RoutingRuleController} 的 SSE 写事件与 {@code meta} 拼装：事件类型、字段名、
     *       命中快照的形状都是业务，理想归属是一个 SSE 写入器 + 事件 record；</li>
     *   <li>{@code RoutingRuleController} / {@code DepartmentController} 的响应体拼装：
     *       后者是部门树组装（{@code childrenByParent} + 递归挂载），纯树算法、与 HTTP 无关，
     *       应进 {@code service.support}。</li>
     * </ul>
     * <p>两处都牵动既有接口与前端联调，单独排期；本轮只把「新代码不得再加厚」立起来。</p>
     */
    private static final List<KnownOffender> KNOWN_OFFENDERS = List.of(
            new KnownOffender("RoutingRuleController.java", "JsonKit"),
            new KnownOffender("RoutingRuleController.java", "new LinkedHashMap"),
            new KnownOffender("DepartmentController.java", "new LinkedHashMap"));

    @Test
    void noBusinessInControllers() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : controllerFiles()) {
            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            String name = file.getFileName().toString();
            for (Rule rule : RULES) {
                if (!rule.pattern().matcher(code).find()) {
                    continue;
                }
                if (KNOWN_OFFENDERS.contains(new KnownOffender(name, rule.name()))) {
                    continue;
                }
                offenders.add(name + " → " + rule.name());
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
        for (KnownOffender offender : KNOWN_OFFENDERS) {
            String label = offender.file() + " → " + offender.rule();
            Path file = CONTROLLER_DIR.resolve(offender.file());
            if (!Files.isRegularFile(file)) {
                stale.add(label + "（文件已不存在，可能已改名）");
                continue;
            }
            Rule rule = RULES.stream()
                    .filter(candidate -> candidate.name().equals(offender.rule()))
                    .findFirst()
                    .orElse(null);
            if (rule == null) {
                stale.add(label + "（规则名不存在，多半是改名后忘了同步名单）");
                continue;
            }
            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            if (!rule.pattern().matcher(code).find()) {
                stale.add(label + "（已经不再违规）");
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
        // 名单按「文件 → 规则」登记，规则名写错会静默失效，这里钉住每条登记都能对上一条真规则
        for (KnownOffender offender : KNOWN_OFFENDERS) {
            assertTrue(RULES.stream().anyMatch(rule -> rule.name().equals(offender.rule())),
                    "登记里的规则名不存在：" + offender.file() + " → " + offender.rule());
        }
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

    /** 一条存量豁免：只豁免这一份文件里的这一条规则 */
    private record KnownOffender(String file, String rule) {
    }
}
