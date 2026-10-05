package org.chobit.knot.gateway.constants.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;

/**
 * 额度统计窗口：窗口结束即清零，重新累计。
 *
 * <p>一个额度策略只有一个窗口，{@code max_tokens} 与 {@code cost_limit} 共用它。
 * 窗口边界按配置时区（默认 {@code Asia/Shanghai}）换算，多节点必须配成同一个时区，
 * 否则同一时刻会落进不同窗口。</p>
 */
public enum QuotaWindowEnum implements EnumOption {

    MINUTE("MINUTE", "每分钟"),
    HOUR("HOUR", "每小时"),
    DAY("DAY", "每天"),
    WEEK("WEEK", "每周"),
    MONTH("MONTH", "每月");

    private static final DateTimeFormatter MINUTE_ID = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter HOUR_ID = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_ID = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_ID = DateTimeFormatter.ofPattern("yyyyMM");

    private final String code;
    private final String label;

    QuotaWindowEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * 前端展示名。
     */
    @Override
    public String label() {
        return label;
    }

    /**
     * 缺省窗口：未配置或值非法时按月统计。
     */
    public static QuotaWindowEnum defaultWindow() {
        return MONTH;
    }

    /**
     * 按 code 解析；空值或非法值一律回落 {@link #defaultWindow()}，不让脏数据把额度判死。
     */
    public static QuotaWindowEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return defaultWindow();
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElse(defaultWindow());
    }

    /**
     * 当前时刻所在窗口的标识，用于拼计数键；换窗口即换键，旧键随 TTL 过期。
     *
     * <p>周窗口用「本周周一」作为标识，与 {@code WEEK} 的自然语义一致。</p>
     */
    public String windowId(ZonedDateTime now) {
        return switch (this) {
            case MINUTE -> now.format(MINUTE_ID);
            case HOUR -> now.format(HOUR_ID);
            case DAY -> now.format(DAY_ID);
            case WEEK -> weekStart(now).format(DAY_ID);
            case MONTH -> now.format(MONTH_ID);
        };
    }

    /**
     * 当前窗口的结束时刻（毫秒）。
     */
    public long expireAtMillis(ZonedDateTime now) {
        return switch (this) {
            case MINUTE -> now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1).toInstant().toEpochMilli();
            case HOUR -> now.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli();
            case DAY -> now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant().toEpochMilli();
            case WEEK -> weekStart(now).plusWeeks(1).toInstant().toEpochMilli();
            case MONTH -> YearMonth.from(now).plusMonths(1)
                    .atDay(1).atStartOfDay(now.getZone()).toInstant().toEpochMilli();
        };
    }

    private static ZonedDateTime weekStart(ZonedDateTime now) {
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay(now.getZone());
    }
}
