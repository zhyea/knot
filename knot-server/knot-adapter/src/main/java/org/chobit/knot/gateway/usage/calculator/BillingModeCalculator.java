package org.chobit.knot.gateway.usage.calculator;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.model.NormalizedBillingAmount;
import org.chobit.knot.gateway.usage.NormalizedUsageContext;

public interface BillingModeCalculator {

    BillingModeEnum mode();

    NormalizedBillingAmount calculate(NormalizedUsageContext context);
}
