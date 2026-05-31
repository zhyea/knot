package org.chobit.knot.gateway.upstream;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.EnumSet;
import java.util.Set;

@Component
@Order(10)
public class TextGenerationApiProtocolHandler extends AbstractApiProtocolHandler {

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = EnumSet.of(
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES,
            ModelApiProtocolEnum.COMPLETIONS
    );

    /**
     * Constructs a new instance.
     */
    public TextGenerationApiProtocolHandler(RestClient restClient) {
        super(restClient);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public boolean supports(ModelApiProtocolEnum protocol) {
        return protocol != null && PROTOCOLS.contains(protocol.canonical());
    }
}
