package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.mapper.ProviderCredentialMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Historical hook kept for startup visibility after credential storage was
 * consolidated into encrypted_config.
 */
@Component
@Order(100)
public class ProviderCredentialEncryptionMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderCredentialEncryptionMigrator.class);

    private final ProviderCredentialMapper providerCredentialMapper;
    /**
     * Constructs a new instance.
     */
    public ProviderCredentialEncryptionMigrator(ProviderCredentialMapper providerCredentialMapper) {
        this.providerCredentialMapper = providerCredentialMapper;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public void run(ApplicationArguments args) {
        List<ProviderCredentialEntity> credentials = providerCredentialMapper.listActiveAll();
        log.debug("provider credential encryption migration is no longer needed after encrypted_config consolidation; {} active row(s) found",
                credentials.size());
    }
}
