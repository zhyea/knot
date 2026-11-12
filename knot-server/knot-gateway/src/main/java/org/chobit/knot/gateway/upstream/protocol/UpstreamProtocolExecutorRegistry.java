package org.chobit.knot.gateway.upstream.protocol;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UpstreamProtocolExecutorRegistry {

    private final List<UpstreamProtocolExecutor> executors;

    public UpstreamProtocolExecutor resolve(ModelApiProtocolEnum protocol) {
        return executors.stream()
                .filter(executor -> executor.supports(protocol))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No upstream protocol executor found: " + protocol.code()));
    }
}
