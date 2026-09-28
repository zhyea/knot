package org.chobit.knot.gateway.vo.model;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;

import java.util.Set;

public record RequestAdapterItem(String code,
                                 String label,
                                 String className,
                                 Set<ModelApiProtocolEnum> protocol) {
}
