package org.chobit.knot.gateway.archive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认转储实现：什么都不做，把供应商给的地址原样返回。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "knot.gateway.archive", name = "type",
        havingValue = "passthrough", matchIfMissing = true)
public class PassthroughArchiveStore implements ArchiveStore {

    @Override
    public String archive(ArchiveAsset asset) {
        return asset.sourceUrl();
    }
}
