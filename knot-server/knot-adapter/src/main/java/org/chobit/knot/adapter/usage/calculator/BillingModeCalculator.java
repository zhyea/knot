package org.chobit.knot.adapter.usage.calculator;

import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;

public interface BillingModeCalculator {

    BillingModeEnum mode();

    NormalizedBillingAmount calculate(NormalizedUsageContext context);
}
