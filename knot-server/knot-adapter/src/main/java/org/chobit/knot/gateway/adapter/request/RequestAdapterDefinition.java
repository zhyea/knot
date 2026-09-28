package org.chobit.knot.gateway.adapter.request;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;

import java.util.Set;

public record RequestAdapterDefinition(String code,
                                       String label,
                                       String className,
                                       Set<ModelApiProtocolEnum> protocol) {
}
