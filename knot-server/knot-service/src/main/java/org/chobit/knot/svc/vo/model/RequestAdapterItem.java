package org.chobit.knot.svc.vo.model;

import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;

import java.util.Set;

public record RequestAdapterItem(String code,
                                 String label,
                                 String className,
                                 Set<ModelApiProtocolEnum> protocol) {
}
