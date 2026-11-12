package org.chobit.knot.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
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
 * javadoc 套话守卫：禁止「与方法签名同义」的句子再长回来。
 *
 * <p><b>为什么它必须是一个测试</b>：规范 {@code 10-backend-java.md §5.3} 已经写明
 * 「javadoc 只写读签名读不出来的东西」，但这条约束此前只活在文档里，
 * 结果是同一批机器套话在两轮清理（2026-11-11 的 310 处、2026-11-12 的 139 处）后
 * 仍能再次生成——因为生成它的模板（AI 补全、IDE 模板、旧代码复制）不看文档。
 * 而这条约束<b>纯静态可判</b>：句子是不是等于方法名的逐词翻译，是一行字符串比对的事。
 * 能判的就不该停在文档里。</p>
 *
 * <p><b>为什么住 {@code knot-common}</b>：这是唯一被所有后端模块依赖的模块，
 * 扫描范围要覆盖 {@code knot-admin} / {@code knot-gateway} / {@code knot-service} /
 * {@code knot-adapter} / {@code knot-dal} / {@code knot-plugin} 全部主源码，
 * 放在任何一个 app 模块里都只能量到自己那一亩三分地。</p>
 *
 * <p><b>判据口径（只收「整句不携带签名之外信息」的物种）</b>：
 * 不能用 {@code ^Lists .*\.$} 这种通配——实测
 * {@code Lists usage extractors available for model API bindings.} 带了「仅限模型 API 绑定可用的」
 * 这一层信息，{@code Updates the model enabled status only. Enabling runs full validation;
 * disabling skips it.} 更是整段语义，按通配删会把实义一起删掉。
 * 所以这里逐句枚举，新物种要加就明确加一条。</p>
 *
 * <p><b>只扫主源码</b>：测试替身的 javadoc 不面向读者，且常从被替身对象复制而来，
 * 一起扫会逼着改测试。空壳 javadoc（只有起止标记、没有正文）一并判失败——
 * 它多半是「删了正文忘了删壳」的产物。</p>
 */
class JavadocBoilerplateTest {

    /** surefire 的 basedir 是模块目录，上一级即 {@code knot-server} */
    private static final Path SERVER_ROOT = Path.of("..").toAbsolutePath().normalize();

    /**
     * 逐句枚举的套话清单。每条都对应一种「读方法签名就知道」的噪声：
     * 构造函数、getter/setter、占位式空话。
     */
    private static final List<Pattern> BOILERPLATE = List.of(
            Pattern.compile("^Constructs a new instance\\.$"),
            Pattern.compile("^-- (GETTER|SETTER) --$"),
            Pattern.compile("^Returns the requested value\\.$"),
            Pattern.compile("^Sets the provided value\\.$"),
            Pattern.compile("^Lists matching results\\.$"),
            Pattern.compile("^Creates a new resource\\.$"),
            Pattern.compile("^Updates the target resource\\.$"),
            Pattern.compile("^Deletes the target resource\\.$"),
            Pattern.compile("^Executes the public operation\\.$"));

    /** 带中文的一律不是套话——本项目的实义注释全是中文，英文只出现在术语与枚举值上 */
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    private static final String OPEN = "/**";
    private static final String CLOSE = "*/";

    @Test
    void noBoilerplateJavadocInMainSources() {
        List<Path> files = mainSources();
        assertFalse(files.isEmpty(),
                "扫不到任何主源码，路径口径已失效（basedir=" + Path.of("").toAbsolutePath() + "）");

        Set<String> problems = new TreeSet<>();
        for (Path file : files) {
            problems.addAll(scan(file));
        }
        assertTrue(problems.isEmpty(),
                "javadoc 里有与方法签名同义的套话（10-backend-java.md §5.3）：\n  "
                        + String.join("\n  ", problems));
    }

    /**
     * 逐块扫描一个文件，返回「文件:行号 → 原文」形式的问题清单。
     *
     * <p>按行状态机扫而不是用正则整块匹配：结束标记那一行会被
     * 「星号 + 任意内容」的行正则解析出一个孤立的斜杠，误判成「块有实义内容」——
     * 统计脚本在这上面翻过一次车（把 245 个可整块删的空壳全算成了有实义）。</p>
     */
    private static List<String> scan(Path file) {
        List<String> src;
        try {
            src = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> problems = new ArrayList<>();
        boolean inBlock = false;
        int startLine = 0;
        int contentLines = 0;

        for (int i = 0; i < src.size(); i++) {
            String stripped = src.get(i).strip();
            if (!inBlock) {
                if (!stripped.startsWith(OPEN)) {
                    continue;
                }
                // 单行块 `/** Foo. */`
                if (stripped.endsWith(CLOSE) && stripped.length() > OPEN.length() + CLOSE.length() - 1) {
                    String text = stripped.substring(OPEN.length(), stripped.lastIndexOf(CLOSE)).strip();
                    if (!text.isEmpty() && isBoilerplate(text)) {
                        problems.add(locate(file, i + 1) + "  " + text);
                    }
                    continue;
                }
                inBlock = true;
                startLine = i + 1;
                contentLines = 0;
                continue;
            }

            if (stripped.endsWith(CLOSE)) {
                inBlock = false;
                if (contentLines == 0) {
                    problems.add(locate(file, startLine) + "  空壳 javadoc（删正文忘了删壳）");
                }
                continue;
            }

            String text = content(stripped);
            if (text.isEmpty()) {
                continue;
            }
            contentLines++;
            if (isBoilerplate(text)) {
                problems.add(locate(file, i + 1) + "  " + text);
            }
        }
        return problems;
    }

    /** 去掉行首的 {@code *}；不是注释正文行返回空串 */
    private static String content(String stripped) {
        if (!stripped.startsWith("*")) {
            return "";
        }
        return stripped.substring(1).strip();
    }

    private static boolean isBoilerplate(String text) {
        if (CJK.matcher(text).find()) {
            return false;
        }
        for (Pattern p : BOILERPLATE) {
            if (p.matcher(text).matches()) {
                return true;
            }
        }
        return false;
    }

    private static String locate(Path file, int line) {
        return SERVER_ROOT.relativize(file).toString().replace('\\', '/') + ":" + line;
    }

    /** 每个后端模块的 {@code src/main/java} 下的全部 java 文件 */
    private static List<Path> mainSources() {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> modules = Files.list(SERVER_ROOT)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path root = module.resolve("src/main/java");
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(root)) {
                    files.addAll(walk.filter(p -> p.toString().endsWith(".java")).toList());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }
}
