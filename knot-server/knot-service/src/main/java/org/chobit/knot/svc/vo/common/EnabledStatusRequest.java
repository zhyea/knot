package org.chobit.knot.svc.vo.common;

import jakarta.validation.constraints.NotNull;

public record EnabledStatusRequest(@NotNull Boolean enabled) {
}
