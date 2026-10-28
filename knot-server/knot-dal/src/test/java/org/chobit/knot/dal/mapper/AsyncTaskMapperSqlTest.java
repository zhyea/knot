package org.chobit.knot.dal.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code AsyncTaskMapper.xml} 动态节点的解析守卫。
 *
 * <p><b>为什么需要它</b>：gateway 侧所有 {@code AsyncTaskMapper} 调用都是 mock，
 * 真启也只证明 XML 能被解析（{@code <include refid>} 对得上），而 {@code <foreach>}
 * 是<b>执行期才展开</b>的动态节点。也就是说「{@code collection="inFlightCodes"} 写错了名字」
 * 或「有人把参数改回硬编码 {@code in (0, 1)}」这两件事，在 456 例 gateway 单测 + 真启之后
 * 仍然可以是坏的，且第一次显形是线上积压。</p>
 *
 * <p><b>它判什么</b>：用真实 {@link Configuration} 解析 XML 并取 {@link BoundSql}，
 * 断言「在途态谓词确实由 {@code inFlightCodes} 参数展开成两个占位符」。
 * 这一步不需要数据库——mybatis 自带 shaded 的 {@code org.apache.ibatis.ognl}，
 * 动态节点求值不依赖外部 ognl jar，所以可以在纯单测里跑。</p>
 *
 * <p><b>它不判什么</b>（别把它当成 SQL 已经测过了）：列名、join、{@code now(3)}
 * 的时区口径、MySQL 方言下的 {@code order by ... limit} 是否合法，都仍然只能由
 * {@code schema.sql} 启动执行 + 真库冒烟来判（见 {@link MapperStatementCoverageTest} 类注释）。
 * 这里只补「参数绑定」这一块静态可判的缺口。</p>
 *
 * <p>⚠ 断言里的 {@code 2}（两个占位符）和 {@code List.of(0, 1)} 是<b>故意写死</b>的裸值：
 * 若改成引用 {@code AsyncTaskStatusEnum#inFlightCodes()}，将来往枚举加一个在途态时
 * 这里会一起变，护栏就失效了。真要加在途态，这个测试<b>应当红</b>，改的时候看一眼是不是
 * 真的要两个以上的占位符。</p>
 */
class AsyncTaskMapperSqlTest {

    private static final String NAMESPACE = "org.chobit.knot.dal.mapper.AsyncTaskMapper";
    private static final String RESOURCE = "mapper/AsyncTaskMapper.xml";

    /** 在途态：写死裸值，不引 gateway 枚举（dal 不认识状态语义，也不该认识） */
    private static final List<Integer> IN_FLIGHT = List.of(0, 1);

    /** 所有带在途态谓词的 statement：加新方法时必须同步进这个清单，否则不会有人替你验证 */
    private static final List<String> IN_FLIGHT_STATEMENTS =
            List.of("claim", "claimExpired", "scheduleNext", "complete", "deleteCompletedBefore");

    /** {@code status in (?, ?)}：允许 foreach 生成的任意空白形态，group(2) 是括号里的内容 */
    private static final Pattern IN_FLIGHT_PREDICATE = Pattern.compile("status\\s+(not\\s+)?in\\s*\\(([^)]*)\\)");

    /** 括号里只允许占位符、逗号、空白 */
    private static final Pattern PLACEHOLDERS_ONLY = Pattern.compile("[\\s?,]+");

    private static Configuration parse() {
        Configuration configuration = new Configuration();
        try (InputStream in = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(in, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        } catch (Exception e) {
            throw new IllegalStateException("解析 " + RESOURCE + " 失败", e);
        }
        return configuration;
    }

    private static String sqlOf(Configuration configuration, String id, Map<String, Object> params) {
        MappedStatement statement = configuration.getMappedStatement(NAMESPACE + "." + id);
        return statement.getBoundSql(params).getSql().replaceAll("\\s+", " ").trim();
    }

    /** 扫描类语句（claim / claimExpired）的入参：在途码 + 分批处理需要的边界值 */
    private static Map<String, Object> scanning() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("claimId", "claim-1");
        params.put("claimExpires", LocalDateTime.now());
        params.put("limit", 100);
        params.put("inFlightCodes", IN_FLIGHT);
        return params;
    }

    /**
     * 各 statement 的入参。允许含 null（{@code errorCode} / {@code errorMessage}），
     * 所以不能用 {@code Map.of}——那会在构造期就抛 NPE。
     */
    private static Map<String, Object> paramsOf(String id) {
        Map<String, Object> params = new LinkedHashMap<>();
        switch (id) {
            case "claim", "claimExpired" -> params.putAll(scanning());
            case "scheduleNext" -> {
                params.putAll(scanning());
                params.put("id", 1L);
                params.put("nextPollAt", LocalDateTime.now());
                params.put("lastPolledAt", LocalDateTime.now());
                params.put("nextStatus", 1);
            }
            case "complete" -> {
                params.putAll(scanning());
                params.put("id", 1L);
                params.put("status", 2);
                params.put("resultJson", "{}");
                params.put("errorCode", null);
                params.put("errorMessage", null);
                params.put("completedAt", LocalDateTime.now());
            }
            case "deleteCompletedBefore" -> {
                params.put("before", LocalDateTime.now());
                params.put("limit", 100);
                params.put("inFlightCodes", IN_FLIGHT);
            }
            default -> throw new IllegalArgumentException("未知 statement：" + id);
        }
        return params;
    }

    @Test
    void claimExpandsInFlightCodesToTwoPlaceholders() {
        Configuration configuration = parse();
        String claim = sqlOf(configuration, "claim", paramsOf("claim"));
        assertTrue(claim.contains("status in"),
                "claim 里没有在途态谓词，status 条件可能被整段删掉了：\n  " + claim);

        Matcher predicate = IN_FLIGHT_PREDICATE.matcher(claim);
        assertTrue(predicate.find(), "claim 的在途态谓词没展开（foreach 没生效或参数名不匹配）：\n  " + claim);
        assertEquals(2, predicate.group(2).chars().filter(character -> character == '?').count(),
                "claim 的在途态谓词占位符数不是 2：[" + predicate.group(2) + "]\n  " + claim);
    }

    /**
     * 在途态谓词的括号里<b>只能有占位符</b>，不能出现任何字面量状态码。
     *
     * <p>⚠ 别写成「整条 SQL 里不许出现数字」：{@code now(3)}（毫秒精度）里的 3 会立刻误报。
     * 只盯 {@code status in} / {@code status not in} 后面那对括号，才是精确的判据。</p>
     */
    @Test
    void everyInFlightPredicateContainsOnlyPlaceholders() {
        Configuration configuration = parse();

        for (String id : IN_FLIGHT_STATEMENTS) {
            String sql = sqlOf(configuration, id, paramsOf(id));
            Matcher predicate = IN_FLIGHT_PREDICATE.matcher(sql);
            assertTrue(predicate.find(), id + " 里找不到在途态谓词：\n  " + sql);
            String inside = predicate.group(2);
            assertTrue(PLACEHOLDERS_ONLY.matcher(inside).matches(),
                    id + " 的在途态谓词里混进了字面量——口径是走 inFlightCodes 参数，"
                            + "写死就不会跟着枚举走：[" + inside + "] 完整 SQL：\n  " + sql);
            assertEquals(2, inside.chars().filter(character -> character == '?').count(),
                    id + " 的在途态谓词占位符数不是 2：[" + inside + "]\n  " + sql);
        }
    }

    @Test
    void deleteCompletedBeforeNegatesTheInFlightSet() {
        Configuration configuration = parse();
        String sql = sqlOf(configuration, "deleteCompletedBefore", paramsOf("deleteCompletedBefore"));
        assertTrue(sql.contains("status not in"),
                "终态清理必须用 not in 在途集合，逐个列终态码会在加状态时漏行：\n  " + sql);
    }

    /** XML 没被解析到时，上面所有断言都会「空跑通过」，所以这里显式钉住解析结果 */
    @Test
    void doesNotSilentlySkipBecauseNothingWasParsed() {
        Configuration configuration = parse();
        long expected = Arrays.stream(AsyncTaskMapper.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic()
                        && !Modifier.isStatic(method.getModifiers())
                        && !method.isDefault())
                .count();
        long actual = configuration.getMappedStatementNames().stream()
                .filter(name -> name.startsWith(NAMESPACE + "."))
                .count();
        assertEquals(expected, actual,
                "AsyncTaskMapper 解析出的 statement 数与接口抽象方法数不一致——"
                        + "XML 没被解析到（或接口加了方法没写语句）");
    }
}
