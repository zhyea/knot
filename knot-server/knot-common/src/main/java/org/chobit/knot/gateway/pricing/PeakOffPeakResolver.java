package org.chobit.knot.gateway.pricing;

import org.chobit.knot.gateway.model.BillingConfig;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 高低峰判定器：把 {@code pricing} 配置 + 发生时点映射成一个 {@link PhaseDecision}。
 *
 * <p>纯函数：**无成员字段、无缓存、不用 SimpleDateFormat 等非线程安全工具**，
 * 相同输入输出恒定，天然并发安全；数据源（节假日日历）由调用方以参数注入，本类不持有。
 *
 * <p>判定链（详见 docs/高低峰计费方案-2026-09-30.md §四）：
 * <pre>
 * local = occurredAt(ZoneId(pricing.timezone))              // 首期 timezone 恒为 UTC
 * if  holiday(local.date)                                         -> OFF_PEAK  HOLIDAY
 * elif makeUpWorkday(local.date) &amp;&amp; policy == OFF_PEAK           -> OFF_PEAK  MAKE_UP_WORKDAY
 * elif ∃ 高峰规则：dow ∈ weekdays ∧ time ∈ [start, end)            -> PEAK     PEAK_WINDOW
 * else                                                            -> OFF_PEAK  DEFAULT
 * </pre>
 *
 * <p>已知边界：时段按 UTC 解释，节假日按日历自身日期判定，故 UTC 夜间可能落在次日节假日上，
 * 这是既定语义（方案文档 §四 取舍 3），实现不做特判。
 */
public final class PeakOffPeakResolver {

    private static final DateTimeFormatter CLOCK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** 调休日沿用所调休星期的高峰窗口策略名 */
    private static final String MAKE_UP_FOLLOW_WEEKDAY = "FOLLOW_WEEKDAY_WINDOWS";

    private PeakOffPeakResolver() {
    }

    /**
     * 判定给定时刻的相位；缺少 pricing 或 occurredAt 时返回 {@link PhaseDecision#noTimestamp()}（倍率 1）。
     */
    public static PhaseDecision resolve(BillingConfig.Pricing pricing,
                                        Instant occurredAt,
                                        HolidayCalendar calendar) {
        if (pricing == null || occurredAt == null) {
            return PhaseDecision.noTimestamp();
        }
        ZonedDateTime local = localTime(occurredAt, pricing.timezone());
        if (local == null) {
            return PhaseDecision.noTimestamp();
        }
        HolidayCalendar effective = calendar == null ? HolidayCalendar.EMPTY : calendar;
        List<BillingConfig.PhaseRule> phases = pricing.phases() == null ? List.of() : pricing.phases();
        LocalDate date = local.toLocalDate();
        LocalTime time = local.toLocalTime();

        if (effective.isHoliday(date)) {
            return offPeak(PhaseReason.HOLIDAY, phases);
        }
        if (effective.isMakeUpWorkday(date)) {
            if (!followsWeekdayWindows(phases)) {
                return offPeak(PhaseReason.MAKE_UP_WORKDAY, phases);
            }
            // FOLLOW_WEEKDAY_WINDOWS：调休日按工作日窗口判定，忽略当天星期
            PhaseDecision followed = matchPeakWindow(phases, date, time, true);
            return followed != null ? followed : offPeak(PhaseReason.DEFAULT, phases);
        }
        PhaseDecision matched = matchPeakWindow(phases, date, time, false);
        return matched != null ? matched : offPeak(PhaseReason.DEFAULT, phases);
    }

    /**
     * 在模式层给出的基础单价上应用相位倍率。
     *
     * <p>「方案层不持有价格」的落点：这里只做一次乘法，价格本体始终来自模式层解析链。
     */
    public static BigDecimal apply(BigDecimal basePrice,
                                   BillingConfig.Pricing pricing,
                                   BillingConfig.PricingContext context,
                                   HolidayCalendar calendar) {
        if (basePrice == null) {
            return null;
        }
        Instant occurredAt = context == null ? null : context.occurredAt();
        return basePrice.multiply(resolve(pricing, occurredAt, calendar).multiplier());
    }

    /** 遍历高峰规则找命中窗口；{@code ignoreWeekday} 用于调休日放宽星期限制 */
    private static PhaseDecision matchPeakWindow(List<BillingConfig.PhaseRule> phases,
                                                 LocalDate date,
                                                 LocalTime time,
                                                 boolean ignoreWeekday) {
        for (BillingConfig.PhaseRule rule : phases) {
            if (rule == null || rule.condition() == null || rule.condition().isDefault()) {
                continue;
            }
            if (PricingPhase.PEAK != PricingPhase.fromCode(rule.phase())) {
                continue;
            }
            if (!ignoreWeekday && !hitsWeekday(rule.condition(), date)) {
                continue;
            }
            if (hitsWindow(rule.condition(), time)) {
                return new PhaseDecision(PricingPhase.PEAK, PhaseReason.PEAK_WINDOW, multiplier(rule));
            }
        }
        return null;
    }

    private static boolean hitsWeekday(BillingConfig.PhaseCondition condition, LocalDate date) {
        List<DayOfWeek> weekdays = condition.weekdays();
        return weekdays != null && weekdays.contains(date.getDayOfWeek());
    }

    private static boolean hitsWindow(BillingConfig.PhaseCondition condition, LocalTime time) {
        List<BillingConfig.Window> windows = condition.windows();
        if (windows == null || windows.isEmpty()) {
            return false;
        }
        for (BillingConfig.Window window : windows) {
            LocalTime start = parseClock(window.start());
            LocalTime end = parseClock(window.end());
            if (start == null || end == null) {
                continue;
            }
            // 左闭右开：01:00 命中 01:00-04:00，04:00 不命中
            if (!time.isBefore(start) && time.isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    /** 低峰判定：优先取兜底项的倍率，没有兜底项则不打折 */
    private static PhaseDecision offPeak(PhaseReason reason, List<BillingConfig.PhaseRule> phases) {
        BigDecimal multiplier = BigDecimal.ONE;
        for (int index = phases.size() - 1; index >= 0; index--) {
            BillingConfig.PhaseRule rule = phases.get(index);
            if (rule != null && rule.condition() != null && rule.condition().isDefault()) {
                multiplier = multiplier(rule);
                break;
            }
        }
        return new PhaseDecision(PricingPhase.OFF_PEAK, reason, multiplier);
    }

    /** 调休策略取自高峰规则的 condition；缺失按 OFF_PEAK 处理 */
    private static boolean followsWeekdayWindows(List<BillingConfig.PhaseRule> phases) {
        for (BillingConfig.PhaseRule rule : phases) {
            if (rule == null || rule.condition() == null || rule.condition().isDefault()) {
                continue;
            }
            return MAKE_UP_FOLLOW_WEEKDAY.equals(rule.condition().makeUpWorkdayPolicy());
        }
        return false;
    }

    private static BigDecimal multiplier(BillingConfig.PhaseRule rule) {
        return rule.multiplier() == null ? BigDecimal.ONE : rule.multiplier();
    }

    /**
     * 按配置时区换算本地时间；时区不在白名单（首期仅 UTC）时返回 null。
     *
     * <p>白名单在保存期已由 {@code BillingConfig#validatePricing} 拦过一道，这里再拦是**防御**：
     * 历史脏数据、直接改库、以及 {@code +08:00} 这类 {@link ZoneId#of} 合法但语义不符的固定偏移，
     * 都不该参与判定；落到 null 会一路降级到「不调整」（倍率 1），即宁可不打折也不乱打折。
     */
    private static ZonedDateTime localTime(Instant instant, String timezone) {
        String zoneId = timezone == null || timezone.isBlank() ? "UTC" : timezone.trim();
        if (!BillingConfig.SUPPORTED_TIMEZONES.contains(zoneId)) {
            return null;
        }
        try {
            return instant.atZone(ZoneId.of(zoneId));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static LocalTime parseClock(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(text, CLOCK_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
