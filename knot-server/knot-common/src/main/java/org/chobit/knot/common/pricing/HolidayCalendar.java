package org.chobit.knot.common.pricing;

import java.time.LocalDate;

/**
 * 节假日日历：高低峰判定的外部数据来源，由调用方注入，判定器本身不持有状态或缓存。
 * {@link #EMPTY} 是「无日历」的空实现：节假日与调休都不命中，只按星期与时段判峰谷。
 * 日期一律按日历自身口径判定（中国法定节假日按 {@code Asia/Shanghai} 的自然日），不随时区配置漂移。
 */
public interface HolidayCalendar {

    /** 无日历：任何日期都不是节假日，也不是调休工作日 */
    HolidayCalendar EMPTY = new EmptyCalendar();

    /** 是否为节假日（含法定放假日） */
    boolean isHoliday(LocalDate date);

    /** 是否为调休补班日（周末上班、按工作日口径处理） */
    boolean isMakeUpWorkday(LocalDate date);

    /** {@link #EMPTY} 的实现：恒定返回 false */
    record EmptyCalendar() implements HolidayCalendar {

        @Override
        public boolean isHoliday(LocalDate date) {
            return false;
        }

        @Override
        public boolean isMakeUpWorkday(LocalDate date) {
            return false;
        }
    }
}
