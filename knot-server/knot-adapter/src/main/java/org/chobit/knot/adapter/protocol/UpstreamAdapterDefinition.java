package org.chobit.knot.adapter.protocol;

import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;

import java.util.Set;

public record UpstreamAdapterDefinition(String code,
                                        String label,
                                        String className,
                                        Set<ModelApiProtocolEnum> protocol) {
}
