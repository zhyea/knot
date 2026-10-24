package org.chobit.knot.gateway.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper 接口与 XML statement 的一致性守卫。
 *
 * <p><b>为什么不给 knot-dal 写 Mockito 单测</b>（改进清单 4.4 判定结论）：本模块
 * 76 个类里 40 个是 {@code @Data} 字段容器（Entity）、34 个是纯抽象方法的 MyBatis 接口
 * （无注解 SQL），<b>没有任何含分支/算术的逻辑</b>。对接口 {@code mock()} 一版只能证明
 * 「接口能加载」，对 Entity 测 getter/setter 只能证明「Lombok 装了」——都是零信息量测试，
 * 却要付维护成本。真正的风险不在这里，而在下面两条<b>编译期不报错、运行期才炸</b>的失配。</p>
 *
 * <p>本测试覆盖的正是这两条：</p>
 * <ol>
 *   <li><b>接口方法没有对应 statement</b>：删/改 XML 里的 {@code id} 或忘写新语句，
 *       调用时才抛 {@code BindingException: Invalid bound statement}。</li>
 *   <li><b>namespace 与接口不匹配</b>：XML 换个包名或拼错，整个文件的语句全部绑定不上，
 *       而 Spring 启动<b>不会</b>报错（只有被调用时才失败）。</li>
 * </ol>
 *
 * <p>SQL 本身的正确性（列名、join、where 口径）不在静态可判范围内，由
 * {@code schema.sql} 启动执行 + 真启冒烟（{@code .workbuddy/audit/bv_smoke_run.sh}，
 * 真库 {@code knot_smoke_options}，8 场景）+ CI 的 {@code mariadb:10.6} 覆盖。</p>
 */
class MapperStatementCoverageTest {

    private static final Path JAVA_DIR = Path.of("src/main/java/org/chobit/knot/gateway/mapper");
    private static final Path XML_DIR = Path.of("src/main/resources/mapper");
    private static final String MAPPER_PACKAGE = "org.chobit.knot.gateway.mapper";

    private static final Pattern NAMESPACE = Pattern.compile("<mapper\\s[^>]*namespace=\"([^\"]+)\"");
    private static final Pattern STATEMENT =
            Pattern.compile("<(select|insert|update|delete)\\b[^>]*\\bid=\"([^\"]+)\"");
    /** XML 注释里可能写着示例语句，必须先剥掉，否则解析哑火（会把注释里的 id 当成真语句） */
    private static final Pattern XML_COMMENT = Pattern.compile("(?s)<!--.*?-->");

    @Test
    void everyMapperMethodHasMatchingStatement() throws Exception {
        Map<String, Set<String>> statementsByNamespace = statementsByNamespace();
        Set<String> knownInterfaces = mapperInterfaceNames();

        List<String> problems = new ArrayList<>();
        for (String iface : knownInterfaces) {
            Set<String> statements = statementsByNamespace.get(iface);
            if (statements == null) {
                problems.add(iface + "：没有任何 XML 声明该 namespace（该接口的方法全部无法绑定）");
                continue;
            }
            for (String method : abstractMethodNames(iface)) {
                if (!statements.contains(method)) {
                    problems.add(iface + "#" + method + "：XML 里没有同名 statement");
                }
            }
        }
        assertTrue(problems.isEmpty(), "Mapper 接口与 XML statement 失配：\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyStatementHasMatchingMapperMethod() throws Exception {
        Set<String> knownInterfaces = mapperInterfaceNames();

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : statementsByNamespace().entrySet()) {
            String namespace = entry.getKey();
            if (!knownInterfaces.contains(namespace)) {
                problems.add(namespace + "：namespace 指向的接口不存在（该文件所有语句都绑定不上）");
                continue;
            }
            Set<String> methods = abstractMethodNames(namespace);
            for (String statement : entry.getValue()) {
                if (!methods.contains(statement)) {
                    problems.add(namespace + "#" + statement + "：接口里没有同名方法（死语句或接口已改名）");
                }
            }
        }
        assertTrue(problems.isEmpty(), "XML statement 与 Mapper 接口失配：\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyMapperInterfaceIsBoundToSomeXml() {
        // 兜底：新增接口但忘了写 XML（或忘了 @Mapper）时，前两个测试会各自报一堆，
        // 这里先给出一个干净的「谁没有被任何 XML 认领」清单。
        Set<String> bound = statementsByNamespace().keySet();
        Set<String> unbound = new TreeSet<>(mapperInterfaceNames());
        unbound.removeAll(bound);
        assertTrue(unbound.isEmpty(), "以下 Mapper 接口未被任何 XML 的 namespace 认领：" + unbound);
    }

    @Test
    void doesNotSilentlySkipBecauseOfEmptyScan() throws Exception {
        // 扫描路径基于 surefire 的 cwd（模块根）。目录不存在会让前几个断言「空跑也通过」，
        // 所以这里显式失败，避免测试变成永远绿灯的摆设。
        assertTrue(Files.isDirectory(JAVA_DIR), "找不到 Mapper 源码目录，扫描口径失效：" + JAVA_DIR.toAbsolutePath());
        assertTrue(Files.isDirectory(XML_DIR), "找不到 Mapper XML 目录，扫描口径失效：" + XML_DIR.toAbsolutePath());
        assertFalse(mapperInterfaceNames().isEmpty(), "没扫到任何 Mapper 接口");
        assertFalse(statementsByNamespace().isEmpty(), "没扫到任何 XML statement");
    }

    // ==================== 扫描 ====================

    /** XML 里的 statement：namespace -> statement id 集合 */
    private static Map<String, Set<String>> statementsByNamespace() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(XML_DIR)) {
            for (Path file : files.filter(item -> item.toString().endsWith(".xml")).sorted().toList()) {
                String xml = XML_COMMENT.matcher(Files.readString(file, StandardCharsets.UTF_8)).replaceAll("");
                Matcher namespace = NAMESPACE.matcher(xml);
                if (!namespace.find()) {
                    result.computeIfAbsent("<无 namespace: " + file.getFileName() + ">", key -> new LinkedHashSet<>());
                    continue;
                }
                Set<String> ids = result.computeIfAbsent(namespace.group(1), key -> new LinkedHashSet<>());
                Matcher statement = STATEMENT.matcher(xml);
                while (statement.find()) {
                    ids.add(statement.group(2));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 Mapper XML 失败", e);
        }
        return result;
    }

    /** 源码目录下的 Mapper 接口全限定名 */
    private static Set<String> mapperInterfaceNames() {
        Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(JAVA_DIR)) {
            for (Path file : files.filter(item -> item.toString().endsWith("Mapper.java")).sorted().toList()) {
                String simpleName = file.getFileName().toString().replace(".java", "");
                names.add(MAPPER_PACKAGE + "." + simpleName);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 Mapper 源码目录失败", e);
        }
        return names;
    }

    /** 接口的抽象方法名（排除 static / default / 编译器合成方法） */
    private static Set<String> abstractMethodNames(String className) throws ClassNotFoundException {
        Class<?> type = Class.forName(className);
        Set<String> names = new LinkedHashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || method.isDefault()) {
                continue;
            }
            names.add(method.getName());
        }
        return names;
    }
}
