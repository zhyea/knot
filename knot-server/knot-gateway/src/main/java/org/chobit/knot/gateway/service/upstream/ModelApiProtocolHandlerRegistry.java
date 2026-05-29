package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.ModelApiProtocol;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ModelApiProtocolHandlerRegistry {

    private final List<ModelApiProtocolHandler> handlers;

    public ModelApiProtocolHandlerRegistry(List<ModelApiProtocolHandler> handlers) {
        this.handlers = handlers;
    }

    public ModelApiProtocolHandler resolve(ModelApiProtocol protocol) {
        return handlers.stream()
                .filter(handler -> handler.supports(protocol))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No API protocol handler found: " + protocol.code()));
    }
}
