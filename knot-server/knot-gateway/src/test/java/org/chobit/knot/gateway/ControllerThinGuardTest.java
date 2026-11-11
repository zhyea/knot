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
 * <b>Controller 必须薄</b>的守卫（规范 {@code 10-backend-java.md §3.3}）。
 *
 * <p><b>为什么它必须是测试</b>：这条约束是纯静态可判的——controller 里出现 JSON 解析、
 * 手拼响应体、状态映射，就是业务漏进来了；而「靠 review 记住」的纪律总会输给赶时间的人，
 * 第一处破例之后，第二个人看到「这里已经有一处了」就会再加一处。
 * 同 {@code GatewayHttpMethodGuardTest} / {@code LayeringGuardTest} 的处理方式。</p>
 *
 * <p><b>为什么不判行数</b>：厚度不是行数。{@code GatewayExceptionHandler} 234 行、全是异常到
 * 响应的映射，那是 HTTP 出口的本职；{@code GatewayTaskController} 只有 78 行，却曾经自己查库、
 * 自己解 JSON、自己对状态码。判据是<b>有没有业务</b>，所以只禁那些「同一件事在别处已有唯一归属」
 * 的写法。</p>
 *
 * <p><b>为什么恰是这五条</b>：</p>
 * <ul>
 *   <li>{@code JsonKit.}——解 JSON 一定是在读上游 payload 或库里的 JSON 列，
 *       那是业务/编排，属于 Runner 与 Support；</li>
 *   <li>{@code new LinkedHashMap}——手拼响应体，而对外字段集应当由
 *       {@code model.vo.response} 的 record 声明（§5.1）；</li>
 *   <li>{@code switch}——在 controller 里对状态码/枚举做映射，等于把对外协议
 *       藏在 HTTP 入口，测试只能靠起 MVC 才触达；</li>
 *   <li>{@code new XxxDto(}——DTO 由 {@code svc.converter} 造（两个方向都在那边），
 *       controller 里出现它说明映射又被搬回入口；</li>
 *   <li>{@code new Xxx(..., null, null, ...)}——一串 {@code null} 占位是「映射缺字段」的典型信号，
 *       读的人无从知道第 9 个 {@code null} 是哪个字段，写的人下次就会填错位。</li>
 * </ul>
 * <p>⚠ 扫描前剥掉注释：注释里讨论「不要在这里用 JsonKit」是好注释，不该让它变红。</p>
 *
 * <p><b>双向探针</b>：给任一 controller 加一句 {@code JsonKit.toJson(..)}（或 {@code new XxxDto(..)}、
 * 一串 {@code null} 占位）→ {@code noBusinessInControllers} 必红；把名单里的存量改干净 →
 * {@code registeredOffendersStillOffend} 必红并要求删名单。</p>
 */
class ControllerThinGuardTest {

    /**
     * 扫描目录<b>必须列全</b>：控制器不一定都住在 {@code controller/} 包里。
     *
     * <p>{@code outage/admin} 下的 {@code OutageAdminController} 也是管理面控制器，
     * 却因为不在扫描目录里长期没被这把尺子量过——「目录外控制器」是薄 Controller 守门
     * 最容易漏的一类，所以这里按清单逐目录扫，而不是靠包名猜。</p>
     */
    private static final List<Path> CONTROLLER_DIRS = List.of(
            Path.of("src/main/java/org/chobit/knot/gateway/controller"),
            Path.of("src/main/java/org/chobit/knot/gateway/outage/admin"));

    /** 剥注释用：块注释与行注释。启发式即可——注释里出现 URL 字面量不在本包 controller 的形态内 */
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
     * <p>⭐ 名单会自我失效：{@link #registeredOffendersStillOffend} 断言每一项<b>现在确实还在违规</b>，
     * 改干净后立刻变红并要求删除名单——不会变成永久避难所。</p>
     *
     * <p>⭐ 登记粒度是<b>「文件 → 规则」而不是整个文件</b>：按文件豁免会把这期间该文件里新增的
     * 其它违规一起放过（管理端那份曾经就是这个漏洞），豁免一处写法不该顺带豁免整份文件。</p>
     *
     * <p>网关侧曾登记的 {@code GatewayImageController}（edits 端点把 14 个 multipart 参数逐条
     * {@code put} 进 Map 再交给模板）已于 2026-10-30 改掉：edits 改为 <b>JSON 原样透传</b>——
     * 收客户端原始 JSON 的 {@code Map} 直接转发，不再逐字段绑定，手工组装用的
     * {@code ImageEditBodySupport} 已删除。Controller 只保留「绑定 + 委托」，已从本名单移除。</p>
     */
    private static final List<KnownOffender> KNOWN_OFFENDERS = List.of();

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
                "controller 只做「绑定入参 + 委托 + HTTP 状态映射」，业务搬到 Runner/Service/Support"
                        + "（见 10-backend-java.md §3.3）。违规点：\n  " + String.join("\n  ", offenders));
    }

    /** 名单里的每一项都必须仍然违规；改干净了就把它从 {@link #KNOWN_OFFENDERS} 里删掉 */
    @Test
    void registeredOffendersStillOffend() throws IOException {
        List<String> stale = new ArrayList<>();
        for (KnownOffender offender : KNOWN_OFFENDERS) {
            String label = offender.file() + " → " + offender.rule();
            Path file = resolveOffender(offender.file());
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
        for (Path dir : CONTROLLER_DIRS) {
            assertTrue(Files.isDirectory(dir),
                    "找不到 controller 目录，扫描口径失效：" + dir.toAbsolutePath());
        }
        List<Path> files = controllerFiles();
        assertTrue(files.size() >= 5, "只扫到 " + files.size() + " 个 controller，疑似路径不对");
        assertTrue(files.stream().anyMatch(file -> file.getFileName().toString().equals("GatewayTaskController.java")),
                "没扫到 GatewayTaskController，说明扫描目录或类名已变（这条会一直绿）");
        assertFalse(RULES.isEmpty(), "规则表空了，等于什么都没守");
        // 名单按「文件 → 规则」登记，规则名写错会静默失效，这里钉住每条登记都能对上一条真规则
        for (KnownOffender offender : KNOWN_OFFENDERS) {
            assertTrue(RULES.stream().anyMatch(rule -> rule.name().equals(offender.rule())),
                    "登记里的规则名不存在：" + offender.file() + " → " + offender.rule());
        }
    }

    private static List<Path> controllerFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path dir : CONTROLLER_DIRS) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(file -> file.toString().endsWith(".java"))
                        .sorted()
                        .forEach(files::add);
            }
        }
        return files;
    }

    /**
     * 存量登记项按文件名在扫描清单里反查。
     *
     * <p>目录一多，单目录 {@code resolve} 会查不到文件，把「在别的目录」误报成「已改名」。</p>
     */
    private static Path resolveOffender(String name) {
        for (Path dir : CONTROLLER_DIRS) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return CONTROLLER_DIRS.get(0).resolve(name);
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
