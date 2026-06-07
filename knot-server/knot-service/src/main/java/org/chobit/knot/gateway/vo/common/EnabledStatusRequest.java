package org.chobit.knot.gateway.vo.common;

import jakarta.validation.constraints.NotNull;

public record EnabledStatusRequest(@NotNull Boolean enabled) {
}
