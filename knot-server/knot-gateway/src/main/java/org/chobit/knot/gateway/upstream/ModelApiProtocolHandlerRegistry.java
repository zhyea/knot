package org.chobit.knot.gateway.upstream;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ModelApiProtocolHandlerRegistry {

    private final List<ModelApiProtocolHandler> handlers;

    /**
     * Resolves the requested value from current context and configuration. Executes the public operation.
     */
    public ModelApiProtocolHandler resolve(ModelApiProtocolEnum protocol) {
        return handlers.stream()
                .filter(handler -> handler.supports(protocol))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No API protocol handler found: " + protocol.code()));
    }
}
