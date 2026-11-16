package org.chobit.knot.gateway.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定时任务入口的守卫：{@code @Scheduled} 只准出现在 {@code schedule} 包，且只挂在 {@link GatewayScheduledEntryRunner} 上。
 *
 * <p>注解散落在业务类上的后果是「这个进程到底跑几个定时任务」没有单点答案：
 * 新增一个节拍不必通知任何人，运维也只能靠翻全仓才知道。这条约束纯静态可判——
 * 注解出现在哪个文件是一行扫描的事，能判的就不该停在口头约定里。
 * 同 {@code ControllerThinGuardTest} / {@code LayeringGuardTest} 的处理方式。</p>
 *
 * <p><b>双向探针</b>：给任一业务 Runner 加回 {@code @Scheduled} →
 * {@link #scheduledAnnotationOnlyInSchedulePackage} 必红；在入口类加减一个节拍方法 →
 * {@link #entryMethodsArePinned} 必红并要求同步清单。</p>
 */
class ScheduledEntryGuardTest {

    /** surefire 的 basedir 是模块目录 */
    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    private static final Path SCHEDULE_PACKAGE = Path.of("src/main/java/org/chobit/knot/gateway/schedule");

    private static final Path ENTRY_FILE = SCHEDULE_PACKAGE.resolve("GatewayScheduledEntryRunner.java");

    /** 只认注解本体；javadoc 里的 {@code @Scheduled} 文字不含左括号，因此不会被误判 */
    private static final Pattern SCHEDULED = Pattern.compile("@Scheduled\\s*\\(");

    /**
     * 入口方法清单：四个节拍各一个。
     *
     * <p>⭐ 名单是<b>故意的第二份</b>——直接拿反射结果和它自己比不会有任何断言意义。
     * 新增节拍必须同时改这里，等于强制回答一次「新任务是不是也该走统一入口」。</p>
     */
    private static final Set<String> ENTRY_METHODS = Set.of(
            "asyncTaskTick", "asyncTaskArchive", "asyncTaskDeliver", "configRefreshTick");

    @Test
    void scheduledAnnotationOnlyInSchedulePackage() throws IOException {
        List<String> outside = new ArrayList<>();
        Set<String> inside = new TreeSet<>();
        for (Path file : javaFiles()) {
            if (!SCHEDULED.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                continue;
            }
            if (file.normalize().startsWith(SCHEDULE_PACKAGE.normalize())) {
                inside.add(file.getFileName().toString());
            } else {
                outside.add(relative(file));
            }
        }
        assertTrue(outside.isEmpty(),
                "定时任务必须走 GatewayScheduledEntryRunner 这一个入口，不要在业务类上直接写 @Scheduled：\n  "
                        + String.join("\n  ", outside));
        assertEquals(Set.of(ENTRY_FILE.getFileName().toString()), inside,
                "schedule 包内也只准入口类持有 @Scheduled");
    }

    @Test
    void entryMethodsArePinned() {
        assertEquals(ENTRY_METHODS, scheduledMethodNames(),
                "入口里的节拍方法集合变了：新增/删除任务时，请同步 ENTRY_METHODS 与对应的 Runner 委托");
        for (Method method : scheduledMethods()) {
            String cron = method.getAnnotation(Scheduled.class).cron();
            assertTrue(cron.startsWith("${") && cron.endsWith("}"),
                    method.getName() + " 的 cron 必须写成占位符（可配置），实际为: " + cron);
            assertTrue(cron.contains(":"),
                    method.getName() + " 的 cron 缺少兜底默认值，去掉 yml 配置就会启动失败: " + cron);
            CronExpression.parse(cron.substring(cron.indexOf(':') + 1, cron.length() - 1));
        }
    }

    /** 扫描路径写错会让上面两条「空跑通过」，所以显式钉住扫描规模 */
    @Test
    void doesNotSilentlySkipBecauseOfEmptyScan() throws IOException {
        assertTrue(Files.isDirectory(SCHEDULE_PACKAGE),
                "找不到 schedule 包，扫描口径失效：" + SCHEDULE_PACKAGE.toAbsolutePath());
        assertTrue(Files.isRegularFile(ENTRY_FILE), "找不到入口类，清单已失效：" + ENTRY_FILE);
        List<Path> files = javaFiles();
        assertTrue(files.size() > 50, "只扫到 " + files.size() + " 个源文件，疑似路径不对");
        assertFalse(ENTRY_METHODS.isEmpty(), "清单空了，等于什么都没守");
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(SOURCE_ROOT)) {
            return walk.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<Method> scheduledMethods() {
        List<Method> methods = new ArrayList<>();
        for (Method method : GatewayScheduledEntryRunner.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Scheduled.class)) {
                methods.add(method);
            }
        }
        assertFalse(methods.isEmpty(), "入口类上一个 @Scheduled 方法都没扫到，反射口径已失效");
        return methods;
    }

    private static Set<String> scheduledMethodNames() {
        Set<String> names = new TreeSet<>();
        scheduledMethods().forEach(method -> names.add(method.getName()));
        return names;
    }

    private static String relative(Path file) {
        return file.toString().replace('\\', '/');
    }
}
