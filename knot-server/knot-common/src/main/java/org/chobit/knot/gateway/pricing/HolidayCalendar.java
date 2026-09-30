package org.chobit.knot.gateway.pricing;

import java.time.LocalDate;

/**
 * 节假日日历：高低峰判定的外部数据来源。
 *
 * <p>判定器本身不得持有状态或缓存（见 {@link PeakOffPeakResolver}），数据来源通过本接口注入。
 * 项目当前不提供默认实现 —— {@link #EMPTY} 是「无日历」的空实现，意味着节假日与调休判定都不命中，
 * 只按星期与时段判峰谷。后续接入真实日历（DB / 配置 / 外部服务）只需新增实现，判定逻辑不用改。
 *
 * <p>日期一律按日历自身口径判定，不随时区漂移：中国法定节假日按 {@code Asia/Shanghai} 的自然日，
 * 即便配置里 pricing.timezone 写的是 UTC。
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
