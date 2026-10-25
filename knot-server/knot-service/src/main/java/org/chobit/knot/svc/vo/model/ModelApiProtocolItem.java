package org.chobit.knot.svc.vo.model;

public record ModelApiProtocolItem(String code,
                                   String name,
                                   String defaultPath,
                                   boolean streamSupported,
                                   String canonicalCode) {
}
