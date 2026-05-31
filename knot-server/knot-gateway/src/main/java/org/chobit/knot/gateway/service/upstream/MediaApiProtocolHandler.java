package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.EnumSet;
import java.util.Set;

@Component
@Order(30)
public class MediaApiProtocolHandler extends AbstractApiProtocolHandler {

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = EnumSet.of(
            ModelApiProtocolEnum.IMAGE_GENERATIONS,
            ModelApiProtocolEnum.IMAGE_EDITS,
            ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS,
            ModelApiProtocolEnum.AUDIO_TRANSLATIONS,
            ModelApiProtocolEnum.AUDIO_SPEECH,
            ModelApiProtocolEnum.VIDEO_GENERATIONS
    );

    /**
     * Constructs a new instance.
     */
    public MediaApiProtocolHandler(RestClient restClient) {
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
