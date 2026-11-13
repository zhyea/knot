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

/** javadoc 变更历史守卫：主源码注释只写功能说明，不记变更历史与设计论证。 */
class JavadocHistoryGuardTest {

    /** surefire 的 basedir 是模块目录，上一级即 {@code knot-server} */
    private static final Path SERVER_ROOT = Path.of("..").toAbsolutePath().normalize();

    /**
     * 禁用词表。逐词枚举，命中即失败；确有正当用途时把整句加进 {@link #ALLOWED}，不放宽正则。
     */
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("20\\d{2}-\\d{2}-\\d{2}(?!T\\d{2}:)"),
            Pattern.compile("拍板|立规|普查|(?<!决)定调|(?<![A-Za-z])robin(?![A-Za-z])"),
            Pattern.compile("WebFlux 批\\s*\\d|批次 [A-Z]|改进清单 \\d|二次搬迁|第一版|上一版"),
            Pattern.compile("此前|原先|原本|曾经|旧实现|早期实现|老配置"),
            Pattern.compile("\\d\\.\\d\\s*(→|->)\\s*\\d\\.\\d"),
            Pattern.compile("为什么|背景[:：]|判例[:：]|理由是|依据是|被否|权衡|取舍"),
            Pattern.compile("§\\d|docs/\\S*\\.md|10-backend-java\\.md"));

    /** 逐句白名单：一行注释确实只是功能说明却命中禁用词时，把整句原文加进来。 */
    private static final Set<String> ALLOWED = Set.of();

    private static final String OPEN = "/**";
    private static final String CLOSE = "*/";

    @Test
    void noChangeHistoryInMainSourceJavadoc() {
        List<Path> files = mainSources();
        assertFalse(files.isEmpty(),
                "扫不到任何主源码，路径口径已失效（basedir=" + Path.of("").toAbsolutePath() + "）");

        Set<String> problems = new TreeSet<>();
        for (Path file : files) {
            problems.addAll(scan(file));
        }
        assertTrue(problems.isEmpty(),
                "javadoc 里有变更历史或设计论证（10-backend-java.md §5.3）：\n  "
                        + String.join("\n  ", problems));
    }

    /** 按行状态机扫 javadoc 正文行；结束标记行不产生正文，避免把孤立斜杠算成内容。 */
    private static List<String> scan(Path file) {
        List<String> src;
        try {
            src = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> problems = new ArrayList<>();
        boolean inBlock = false;
        for (int i = 0; i < src.size(); i++) {
            String stripped = src.get(i).strip();
            if (!inBlock) {
                if (!stripped.startsWith(OPEN)) {
                    continue;
                }
                if (stripped.endsWith(CLOSE) && stripped.length() > OPEN.length() + CLOSE.length() - 1) {
                    check(problems, file, i + 1,
                            stripped.substring(OPEN.length(), stripped.lastIndexOf(CLOSE)).strip());
                    continue;
                }
                inBlock = true;
                continue;
            }
            if (stripped.endsWith(CLOSE)) {
                inBlock = false;
                continue;
            }
            if (stripped.startsWith("*")) {
                check(problems, file, i + 1, stripped.substring(1).strip());
            }
        }
        return problems;
    }

    private static void check(List<String> problems, Path file, int line, String text) {
        if (text.isEmpty() || ALLOWED.contains(text)) {
            return;
        }
        for (Pattern p : FORBIDDEN) {
            if (p.matcher(text).find()) {
                problems.add(locate(file, line) + "  " + text);
                return;
            }
        }
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
