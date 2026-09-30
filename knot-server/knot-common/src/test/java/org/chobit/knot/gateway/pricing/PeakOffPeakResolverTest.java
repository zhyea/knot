package org.chobit.knot.gateway.pricing;

import org.chobit.knot.gateway.model.BillingConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 高低峰判定器契约测试：相位判定链、时段边界、节假日与调休、缺时间的兜底。
 *
 * <p>时区一律按配置里的 UTC 解释；日期由调用方给的 Instant 决定，故「跨自然日落在次日节假日」
 * 这类边界通过构造不同时刻来覆盖。
 */
class PeakOffPeakResolverTest {

    /** MONDAY/WEDNESDAY 01:00-04:00 与 06:00-10:00 为高峰，其余兜底 0.8 */
    private static final String PRICING_JSON = """
            {"rateMode":"MULTIPLIER","timezone":"UTC","phases":[
              {"condition":{"weekdays":["MONDAY","WEDNESDAY"],
                            "windows":[{"start":"01:00","end":"04:00"},{"start":"06:00","end":"10:00"}],
                            "holidayPolicy":"OFF_PEAK","makeUpWorkdayPolicy":"%s"},
               "phase":"PEAK","multiplier":1},
              {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}
            """;

    @Test
    void peakWindowHitOnConfiguredWeekday() {
        BillingConfig.Pricing pricing = pricing("OFF_PEAK");
        // 2026-09-30 周三
        PhaseDecision hit = resolve(pricing, instant(2026, 9, 30, 3, 0), HolidayCalendar.EMPTY);
        assertEquals(PricingPhase.PEAK, hit.phase());
        assertEquals(PhaseReason.PEAK_WINDOW, hit.reason());
        assertEquals(0, BigDecimal.ONE.compareTo(hit.multiplier()));

        // 第二个窗口同样命中
        assertEquals(PricingPhase.PEAK,
                resolve(pricing, instant(2026, 9, 30, 8, 30), HolidayCalendar.EMPTY).phase());
    }

    @Test
    void windowBoundaryIsLeftClosedRightOpen() {
        BillingConfig.Pricing pricing = pricing("OFF_PEAK");
        // 起点 01:00 命中
        assertEquals(PricingPhase.PEAK,
                resolve(pricing, instant(2026, 9, 30, 1, 0), HolidayCalendar.EMPTY).phase());
        // 终点 04:00 不命中
        assertEquals(PricingPhase.OFF_PEAK,
                resolve(pricing, instant(2026, 9, 30, 4, 0), HolidayCalendar.EMPTY).phase());
        assertEquals(PhaseReason.DEFAULT,
                resolve(pricing, instant(2026, 9, 30, 4, 0), HolidayCalendar.EMPTY).reason());
    }

    @Test
    void nonConfiguredWeekdayFallsToOffPeak() {
        BillingConfig.Pricing pricing = pricing("OFF_PEAK");
        // 2026-10-03 周六，即便落在高峰时段内也只是低峰
        PhaseDecision weekend = resolve(pricing, instant(2026, 10, 3, 3, 0), HolidayCalendar.EMPTY);
        assertEquals(PricingPhase.OFF_PEAK, weekend.phase());
        assertEquals(PhaseReason.DEFAULT, weekend.reason());
        assertEquals(0, new BigDecimal("0.8").compareTo(weekend.multiplier()));
        // 2026-09-29 周二（未配置的星期）
        assertEquals(PricingPhase.OFF_PEAK,
                resolve(pricing, instant(2026, 9, 29, 3, 0), HolidayCalendar.EMPTY).phase());
    }

    /** 记录判定器实际问出去的日期，用于验证日期口径 */
    private record RecordingCalendar(Set<LocalDate> holidays,
                                     Set<LocalDate> makeUpWorkdays,
                                     java.util.List<LocalDate> asked) implements HolidayCalendar {
        @Override
        public boolean isHoliday(LocalDate date) {
            asked.add(date);
            return holidays.contains(date);
        }

        @Override
        public boolean isMakeUpWorkday(LocalDate date) {
            return makeUpWorkdays.contains(date);
        }
    }

    @Test
    void holidayOverridesPeakWindow() {
        // 周三 03:00Z 本该命中高峰，但当日为节假日 -> 整日低峰且原因是 HOLIDAY（覆盖星期与时段）
        HolidayCalendar calendar = calendarOf(Set.of(LocalDate.of(2026, 9, 30)), Set.of());
        PhaseDecision decision = resolve(pricing("OFF_PEAK"), instant(2026, 9, 30, 3, 0), calendar);
        assertEquals(PricingPhase.OFF_PEAK, decision.phase());
        assertEquals(PhaseReason.HOLIDAY, decision.reason());
        assertEquals(0, new BigDecimal("0.8").compareTo(decision.multiplier()));
    }

    @Test
    void calendarReceivesDateConvertedInConfiguredTimezone() {
        // 日期口径：pricing.timezone=UTC，故判定器按 UTC 日期问日历；
        // 「节假日按北京时间判定」这层语义由日历实现负责，resolver 不做二次换算。
        RecordingCalendar cal = new RecordingCalendar(Set.of(), Set.of(), new java.util.ArrayList<>());
        resolve(pricing("OFF_PEAK"), instant(2026, 9, 30, 17, 0), cal);
        assertEquals(LocalDate.of(2026, 9, 30), cal.asked().get(0), "17:00Z 仍按 UTC 自然日问询");
        cal.asked().clear();
        resolve(pricing("OFF_PEAK"), instant(2026, 10, 1, 0, 30), cal);
        assertEquals(LocalDate.of(2026, 10, 1), cal.asked().get(0), "跨过 UTC 午夜后按新自然日问询");
    }

    @Test
    void makeUpWorkdayFollowsPolicy() {
        BillingConfig.Pricing pricing = pricing("OFF_PEAK");
        HolidayCalendar calendar = calendarOf(Set.of(), Set.of(LocalDate.of(2026, 10, 3)));
        // 调休补班日（周六）且策略为整日低峰
        PhaseDecision offPeak = resolve(pricing, instant(2026, 10, 3, 3, 0), calendar);
        assertEquals(PricingPhase.OFF_PEAK, offPeak.phase());
        assertEquals(PhaseReason.MAKE_UP_WORKDAY, offPeak.reason());

        // 策略改为沿用星期窗口：补班日命中窗口即高峰（忽略当天是周六）
        BillingConfig.Pricing follow = pricing("FOLLOW_WEEKDAY_WINDOWS");
        PhaseDecision peak = resolve(follow, instant(2026, 10, 3, 3, 0), calendar);
        assertEquals(PricingPhase.PEAK, peak.phase());
        assertEquals(PhaseReason.PEAK_WINDOW, peak.reason());
    }

    @Test
    void missingOrInvalidInputsFallBackToNoAdjustment() {
        BillingConfig.Pricing pricing = pricing("OFF_PEAK");
        // 无 occurredAt -> 不放大不打折
        PhaseDecision noTime = PeakOffPeakResolver.resolve(pricing, null, HolidayCalendar.EMPTY);
        assertEquals(PhaseDecision.noTimestamp(), noTime);
        assertEquals(0, BigDecimal.ONE.compareTo(noTime.multiplier()));
        // pricing 缺失
        assertEquals(PhaseDecision.noTimestamp(),
                PeakOffPeakResolver.resolve(null, Instant.now(), HolidayCalendar.EMPTY));
        // 非法时区（+08:00 这类固定偏移）-> 不做调整
        assertEquals(PhaseDecision.noTimestamp(),
                PeakOffPeakResolver.resolve(pricingWithTimezone("+08:00"), instant(2026, 9, 30, 3, 0), HolidayCalendar.EMPTY));
    }

    @Test
    void applyMultipliesBasePrice() {
        BillingConfig.Pricing pricing = pricing("OFF_PEAK");
        BigDecimal base = new BigDecimal("4");
        // 高峰倍率 1
        assertEquals(0, new BigDecimal("4.0").compareTo(
                PeakOffPeakResolver.apply(base, pricing, context(instant(2026, 9, 30, 3, 0)), HolidayCalendar.EMPTY)));
        // 低峰 4 × 0.8 = 3.2，BigDecimal 精确运算无 double 误差
        assertEquals(0, new BigDecimal("3.2").compareTo(
                PeakOffPeakResolver.apply(base, pricing, context(instant(2026, 9, 30, 5, 0)), HolidayCalendar.EMPTY)));
    }

    private static BillingConfig.Pricing pricing(String makeUpPolicy) {
        return pricingWithTimezoneAndPolicy("UTC", makeUpPolicy);
    }

    /** 用指定时区构造 pricing：UTC 外的值会被判定器视为不可用（不做峰谷调整） */
    private static BillingConfig.Pricing pricingWithTimezone(String timezone) {
        return pricingWithTimezoneAndPolicy(timezone, "OFF_PEAK");
    }

    private static BillingConfig.Pricing pricingWithTimezoneAndPolicy(String timezone, String makeUpPolicy) {
        String body = String.format(PRICING_JSON, makeUpPolicy).replace("\"timezone\":\"UTC\"", "\"timezone\":\"" + timezone + "\"");
        BillingConfig config = BillingConfig.parse("{\"pricing\":" + body + "}");
        assertNotNull(config);
        BillingConfig.Pricing result = config.pricing();
        assertNotNull(result);
        return result;
    }

    private static PhaseDecision resolve(BillingConfig.Pricing pricing, Instant instant, HolidayCalendar calendar) {
        return PeakOffPeakResolver.resolve(pricing, instant, calendar);
    }

    private static BillingConfig.PricingContext context(Instant instant) {
        return new BillingConfig.PricingContext(100L, instant);
    }

    private static Instant instant(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant();
    }

    /** 用给定日期集合构造日历，便于覆盖节假日与调休分支 */
    private static HolidayCalendar calendarOf(Set<LocalDate> holidays, Set<LocalDate> makeUpWorkdays) {
        record TestCalendar(Set<LocalDate> holidays, Set<LocalDate> makeUpWorkdays) implements HolidayCalendar {
            @Override
            public boolean isHoliday(LocalDate date) {
                return holidays.contains(date);
            }

            @Override
            public boolean isMakeUpWorkday(LocalDate date) {
                return makeUpWorkdays.contains(date);
            }
        }
        return new TestCalendar(holidays, makeUpWorkdays);
    }
}
