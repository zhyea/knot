package org.chobit.knot.dal.mapper;

import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
 * <p><b>它判什么</b>：用真实 {@code Configuration} 解析 XML 并取 {@link BoundSql}，
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

    /** 在途态：写死裸值，不引 gateway 枚举（dal 不认识状态语义，也不该认识） */
    private static final List<Integer> IN_FLIGHT = List.of(0, 1);

    /**
     * 可重开的终态（{@code FAILED=3} / {@code EXPIRED=4}）：同样写死裸值。
     *
     * <p>⭐ 与 {@link #IN_FLIGHT} 分开两个常量，是为了让「谁在展开哪个集合」在断言里也一目了然：
     * 合并成一个「状态集合」后，调用处传错集合不会有任何信号，而两者的语义恰好相反。</p>
     */
    private static final List<Integer> RETRYABLE = List.of(3, 4);

    /**
     * 待投递的交付态（{@code PENDING=1}）：同样写死裸值，理由与上面两个常量一致。
     *
     * <p>⭐ 交付态与任务态是两套编码，共用同一个「裸值」原则但<b>不能共用常量</b>——
     * 合并会让「任务态集合传给了交付谓词」这种错位完全没有信号，而两套码都是从 0 开始的连续整数。</p>
     */
    private static final List<Integer> DELIVERABLE = List.of(1);

    /** 所有带在途态谓词的 statement：加新方法时必须同步进这个清单，否则不会有人替你验证 */
    private static final List<String> IN_FLIGHT_STATEMENTS =
            List.of("claim", "claimExpired", "scheduleNext", "complete", "deleteCompletedBefore");

    /** {@code status in (?, ?)}：允许 foreach 生成的任意空白形态，group(2) 是括号里的内容 */
    private static final Pattern IN_FLIGHT_PREDICATE = Pattern.compile("status\\s+(not\\s+)?in\\s*\\(([^)]*)\\)");

    /** 括号里只允许占位符、逗号、空白 */
    private static final Pattern PLACEHOLDERS_ONLY = Pattern.compile("[\\s?,]+");

    /**
     * 交付维度的谓词：{@code delivery_status [not] in (?, ...)}，group(1) 是 {@code not}（正向时为 null）。
     *
     * <p>⚠ 必须单独一条正则，不能让 {@link #IN_FLIGHT_PREDICATE} 兼职：那个模式的
     * {@code status\s+in} 会命中 {@code delivery_status in}，两套编码混判后，
     * 「任务态集合被传给交付谓词」这种错位反而查不出来。</p>
     */
    private static final Pattern DELIVERY_PREDICATE =
            Pattern.compile("delivery_status\\s+(not\\s+)?in\\s*\\(([^)]*)\\)");

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
                params.put("terminalBody", "{\"output\":{\"duration\":5}}");
                params.put("settlementJson", null);
                params.put("errorCode", null);
                params.put("errorMessage", null);
                params.put("completedAt", LocalDateTime.now());
            }
            case "deleteCompletedBefore" -> {
                params.put("before", LocalDateTime.now());
                params.put("limit", 100);
                params.put("inFlightCodes", IN_FLIGHT);
                params.put("deliverableCodes", DELIVERABLE);
            }
            case "reopen" -> {
                params.put("id", 1L);
                params.put("nextStatus", 0);
                params.put("nextPollAt", LocalDateTime.now());
                params.put("expiresAt", LocalDateTime.now());
                params.put("retryableCodes", RETRYABLE);
                params.put("deliveryNoneCode", 0);
            }
            default -> throw new IllegalArgumentException("未知 statement：" + id);
        }
        return params;
    }

    @Test
    void claimExpandsInFlightCodesToTwoPlaceholders() {
        String claim = MapperDynamicSql.of(AsyncTaskMapper.class).sql("claim", paramsOf("claim"));
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
        MapperDynamicSql mapper = MapperDynamicSql.of(AsyncTaskMapper.class);

        for (String id : IN_FLIGHT_STATEMENTS) {
            String sql = mapper.sql(id, paramsOf(id));
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
        String sql = MapperDynamicSql.of(AsyncTaskMapper.class)
                .sql("deleteCompletedBefore", paramsOf("deleteCompletedBefore"));
        assertTrue(sql.contains("status not in"),
                "终态清理必须用 not in 在途集合，逐个列终态码会在加状态时漏行：\n  " + sql);
    }

    /**
     * 清理必须排掉「仍待回调」的终态行——否则结果连同 {@code result_json} 一起被静默丢弃。
     *
     * <p>⭐ 这条守的是<b>两个维度都要判</b>：只判 {@code status} 的清理在线上一声不响地
     * 吃掉「已生成成功、回调还在退避重试」的行，客户端既收不到回调、也查不到轮询，
     * 而运维只会看到「任务查不到」。参数名错（比如写成 {@code deliverableStatus}）时
     * OGNL 求值直接抛异常，所以这里钉住的是「谓词存在且由参数展开」，
     * 不是「字面量换了个写法」。</p>
     */
    @Test
    void deleteCompletedBeforeSkipsRowsStillOwedACallback() {
        String sql = MapperDynamicSql.of(AsyncTaskMapper.class)
                .sql("deleteCompletedBefore", paramsOf("deleteCompletedBefore"));
        Matcher predicate = DELIVERY_PREDICATE.matcher(sql);
        assertTrue(predicate.find(),
                "清理没有交付维度谓词——待回调的行会被连结果一起删掉：\n  " + sql);
        assertTrue(predicate.group(1) != null,
                "清理对交付维度的口径必须是 not in（待投递集合），当前是正向的：\n  " + sql);
        assertTrue(PLACEHOLDERS_ONLY.matcher(predicate.group(2)).matches(),
                "交付谓词里混进了字面量——口径是走 deliverableCodes 参数：[" + predicate.group(2) + "]\n  " + sql);
        assertEquals(DELIVERABLE.size(),
                predicate.group(2).chars().filter(character -> character == '?').count(),
                "交付谓词占位符数应与 deliverableCodes 一致：[" + predicate.group(2) + "]\n  " + sql);
    }

    /**
     * 重试（{@code reopen}）谓词必须由 {@code retryableCodes} 展开，而不是写成 {@code in (3, 4)}。
     *
     * <p>⭐ 这条守的是「可重试集合只有一个出处」：写死字面量时，将来把某个终态改成可重试
     * （或反过来），枚举改了 SQL 不会改，症状是「重试按钮点了没反应」或「已计费的任务能被重开」
     * ——后者是资损。占位符数也要钉死：多一个少一个都说明集合被换了。</p>
     */
    @Test
    void reopenExpandsRetryableCodesToTwoPlaceholders() {
        String sql = MapperDynamicSql.of(AsyncTaskMapper.class).sql("reopen", paramsOf("reopen"));
        Matcher predicate = IN_FLIGHT_PREDICATE.matcher(sql);
        assertTrue(predicate.find(), "reopen 里找不到状态集合谓词：\n  " + sql);
        String inside = predicate.group(2);
        assertTrue(PLACEHOLDERS_ONLY.matcher(inside).matches(),
                "reopen 的可重试谓词里混进了字面量——口径是走 retryableCodes 参数：[" + inside + "]\n  " + sql);
        assertEquals(2, inside.chars().filter(character -> character == '?').count(),
                "reopen 的可重试谓词占位符数不是 2：[" + inside + "]\n  " + sql);
        assertTrue(sql.contains("retry_round = retry_round + 1"),
                "重试必须推进轮次：attempts 归零后 task_query_seq 会与上一轮同键，少了轮次数仓就分不开两轮\n  " + sql);
        // ⚠ 断言里写 "expires_at = ?"（渲染后的占位符），不是 "#{expiresAt}"：
        //   取的是 BoundSql，参数已经变成 ?。若有人改回 now(3)，这里会看到字面量 —— 那正是要红的形态
        assertTrue(sql.contains("expires_at = ?"),
                "重试必须由调用方重算 expires_at（dal 读不到档案超时）：沿用旧值会被 claimExpired 立刻收掉，"
                        + "表现为「重试没反应」\n  " + sql);
    }

    /**
     * 重试必须把交付维度一起复位，且<b>保留 {@code callback_url}</b>。
     *
     * <p>⭐ 不复位的后果是同一个任务回调两次：行停在待回调（{@code delivery_status=1}）被重开 →
     * 仍在退避中的交付节拍拿着已清空的 {@code result_json} 先推一次空结果 →
     * 新终态落库时 {@code complete} 再原子武装一次 → 又推一次真结果。
     * 客户端按 {@code task_id} 幂等也救不回来：第一次推的就是错的。</p>
     *
     * <p>⚠ 这里断言的是「六个字段都在 set 里」，不是「值等于 0」：值由 {@code deliveryNoneCode}
     * 参数给（SQL 里禁写字面量状态码），所以看占位符而不看数字。</p>
     */
    @Test
    void reopenResetsDeliveryDimensionButKeepsCallbackUrl() {
        String sql = MapperDynamicSql.of(AsyncTaskMapper.class).sql("reopen", paramsOf("reopen"));

        assertTrue(sql.contains("delivery_status = ?"),
                "重试没有复位交付状态——待回调的行重开后会用空结果再推一次：\n  " + sql);
        for (String reset : List.of("delivery_attempts = 0", "next_delivery_at = null",
                "delivered_at = null", "delivery_error = null",
                "delivery_claim_id = null", "delivery_claim_expires_at = null")) {
            assertTrue(sql.contains(reset),
                    "重试漏了交付字段复位：[" + reset + "]。残留的 attempt / 租约会让新终态一武装就判次数耗尽，"
                            + "或投递被旧租约挡住\n  " + sql);
        }
        assertTrue(!sql.contains("callback_url"),
                "callback_url 是任务固有属性，重试不该动它——清掉等于用户重试一次就永久失去回调：\n  " + sql);
    }

    /**
     * 列表检索只投影摘要列：四个 JSON 段一个都不许出现。
     *
     * <p>⭐ 这条不是性能洁癖，是<b>契约</b>：列表接口返回的 {@code requestJson} 恒为 null 是设计，
     * 一旦有人「顺手」把 JSON 加回投影，详情与列表的差别就消失了，而「列表接口很慢」
     * 会在数据量涨上来之后才显形。</p>
     */
    @Test
    void searchProjectsSummaryColumnsOnly() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", "tc");
        params.put("profileCode", null);
        params.put("protocol", null);
        params.put("status", null);
        params.put("submittedFrom", null);
        params.put("submittedTo", null);
        String sql = MapperDynamicSql.of(AsyncTaskMapper.class).sql("search", params);

        for (String column : List.of("request_json", "poll_spec_json", "context_json", "result_json")) {
            assertTrue(!sql.contains(column),
                    "列表投影里出现了 " + column + "：一页 20 行 × 数十 KB 的 JSON 全无人展示\n  " + sql);
        }
        assertTrue(sql.contains("retry_round"),
                "列表要展示重试轮次（运维判断「这条被重试过几次」靠它）\n  " + sql);
        assertTrue(sql.contains("task_code like"),
                "关键字要能模糊匹配任务句柄：运维手里常常只有一个上游 id 或一段句柄\n  " + sql);
    }

    /** XML 没被解析到时，上面所有断言都会「空跑通过」，所以这里显式钉住解析结果 */
    @Test
    void doesNotSilentlySkipBecauseNothingWasParsed() {
        MapperDynamicSql.of(AsyncTaskMapper.class).assertParsedEveryInterfaceMethod();
    }
}
