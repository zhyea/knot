package org.chobit.knot.gateway.model;

import java.math.BigDecimal;

public record NormalizedUsageDetail(String type,
                                    Long tokens,
                                    BigDecimal cost,
                                    BigDecimal pricePerMillion) {
}
