package org.chobit.knot.gateway.adapter.usage.calculator;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.gateway.adapter.usage.NormalizedUsageContext;

public interface BillingModeCalculator {

    BillingModeEnum mode();

    NormalizedBillingAmount calculate(NormalizedUsageContext context);
}
