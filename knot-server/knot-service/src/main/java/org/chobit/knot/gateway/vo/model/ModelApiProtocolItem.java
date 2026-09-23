package org.chobit.knot.gateway.vo.model;

public record ModelApiProtocolItem(String code,
                                   String name,
                                   String defaultPath,
                                   boolean streamSupported,
                                   String canonicalCode) {
}
