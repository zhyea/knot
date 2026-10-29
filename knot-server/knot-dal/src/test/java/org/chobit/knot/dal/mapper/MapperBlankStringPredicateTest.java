package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「{@code x != ''} 只许写在 {@code String} 参数上」的全局守卫。
 *
 * <p><b>为什么要有它</b>：{@code <if test="status != null and status != ''">} 对
 * {@code String} 是正确写法（挡空串），但对 {@code Integer} 是<b>静默灾难</b>：
 * OGNL 把 {@code ''} 当数字 0 比较，于是 {@code status = 0} 时整个条件被判 false
 * <b>并丢弃</b>——筛选返回全量、不报错、不告警、日志无痕。状态码数字化之后，
 * 「0」恰好是最有业务含义的那个码（停用 / 运行中 / 未启用），所以这个 bug 一旦发生
 * 必定落在最常用的筛选上。</p>
 *
 * <p>2026-10 已连撞三处（{@code ScheduledTaskMapper} 两处、{@code ModelMapper}、
 * {@code ModelPoolMapper}），单点测试逐个补太慢也太容易漏——所以这里按
 * <b>「XML 里出现 {@code != ''} 的属性，其入参类型必须是字符串」</b>做一次全库扫描。
 * 这是一条可以永久留在 CI 里的判据，而不是又一次「改完这一处就完」。</p>
 *
 * <p><b>判据口径</b>：属性类型按 MyBatis 的真实解析顺序取——先看接口方法上的
 * {@code @Param} 名，再退到「整个入参是实体/VO 时的同名字段」。两者都找不到时
 * <b>直接失败</b>（而不是跳过）：跳过会让新写的表达式永远不被检查，
 * 而这正是这类 bug 能活下来的方式。</p>
 */
class MapperBlankStringPredicateTest {

    private static final Path XML_DIR = Path.of("src/main/resources/mapper");
    private static final String MAPPER_PACKAGE = MapperBlankStringPredicateTest.class.getPackageName();

    /** XML 注释里可能有示例片段，会制造幻觉命中 */
    private static final Pattern XML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern IF_TEST = Pattern.compile("<if\\s+test=\"([^\"]*)\"");
    private static final Pattern BLANK_COMPARISON = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*!=\\s*''");

    @Test
    void blankComparisonIsOnlyUsedOnStringParameters() throws Exception {
        List<String> problems = new ArrayList<>();
        Set<String> scanned = new TreeSet<>();
        for (Path file : xmlFiles()) {
            String xml = XML_COMMENT.matcher(Files.readString(file, StandardCharsets.UTF_8)).replaceAll("");
            String mapperName = file.getFileName().toString().replace(".xml", "");
            Class<?> mapper = loader(mapperName, problems);
            if (mapper == null) {
                continue;
            }
            scanned.add(mapperName);

            Set<String> properties = new LinkedHashSet<>();
            Matcher ifTest = IF_TEST.matcher(xml);
            while (ifTest.find()) {
                Matcher blank = BLANK_COMPARISON.matcher(ifTest.group(1));
                while (blank.find()) {
                    properties.add(blank.group(1));
                }
            }
            for (String property : properties) {
                String type = typeOf(mapper, property);
                if (type == null) {
                    problems.add(mapperName + " 里的 " + property + " != ''：无法判定入参类型"
                            + "（接口方法既没有 @Param(\"" + property + "\")，也没有同名实体字段）——"
                            + "请人工确认这是字符串筛选列，否则换成正确的判空写法");
                } else if (!"java.lang.String".equals(type)) {
                    problems.add(mapperName + " 里的 " + property + " != ''：入参类型是 " + type
                            + " 而不是 String。OGNL 会把 '' 当 0 比较，于是 " + property
                            + "=0 时整个 <if> 被判 false 并静默丢弃——筛选返回全量且不报错。"
                            + "削成 <if test=\"" + property + " != null\">");
                }
            }
        }
        assertTrue(problems.isEmpty(), "XML 动态条件把 '' 用在了非字符串参数上：\n  "
                + String.join("\n  ", problems));
        assertFalse(scanned.isEmpty(), "没扫到任何 Mapper XML，本守卫的扫描口径已失效");
    }

    /** 每个 Mapper 接口都必须能在 XML 里被认出：找不到时上一条会「零命中 = 零违规」地空跑 */
    @Test
    void doesNotSilentlySkipBecauseOfEmptyScan() throws Exception {
        assertTrue(Files.isDirectory(XML_DIR), "找不到 Mapper XML 目录，扫描口径失效：" + XML_DIR.toAbsolutePath());
        assertFalse(xmlFiles().isEmpty(), "XML 目录下没有文件");
    }

    // ==================== 扫描与类型解析 ====================

    private static List<Path> xmlFiles() throws IOException {
        try (Stream<Path> files = Files.list(XML_DIR)) {
            return files.filter(item -> item.toString().endsWith(".xml")).sorted().toList();
        }
    }

    private static Class<?> loader(String mapperName, List<String> problems) {
        try {
            return Class.forName(MAPPER_PACKAGE + "." + mapperName);
        } catch (ClassNotFoundException e) {
            problems.add(mapperName + ".xml 找不到同名接口 " + MAPPER_PACKAGE + "." + mapperName
                    + "——namespace 与文件名约定已失效，本文件的条件没被检查");
            return null;
        }
    }

    /**
     * 属性名 → 入参类型（全限定名）。多个方法声明同名 {@code @Param} 时按
     * <b>最宽口径</b>取：只要有一处不是 String，就算问题——同一个属性名在两处
     * 语义不同本身就是可疑的。
     */
    private static String typeOf(Class<?> mapper, String property) {
        String found = null;
        for (Method method : mapper.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                Param param = parameter.getAnnotation(Param.class);
                if (param != null && property.equals(param.value())) {
                    found = merge(found, parameter.getType().getName());
                }
            }
        }
        if (found != null) {
            return found;
        }
        // 入参是实体/VO：MyBatis 直接按字段名取值，不看 @Param
        for (Method method : mapper.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                if (parameter.getAnnotation(Param.class) != null) {
                    continue;
                }
                Class<?> type = parameter.getType();
                Field field = fieldNamed(type, property);
                if (field != null) {
                    found = merge(found, field.getType().getName());
                }
            }
        }
        return found;
    }

    /** 多个声明点的类型不一致时，只要有一个不是 String 就按「不是 String」报出去 */
    private static String merge(String current, String candidate) {
        if (current == null) {
            return candidate;
        }
        if ("java.lang.String".equals(current)) {
            return candidate;
        }
        return current;
    }

    private static Field fieldNamed(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
