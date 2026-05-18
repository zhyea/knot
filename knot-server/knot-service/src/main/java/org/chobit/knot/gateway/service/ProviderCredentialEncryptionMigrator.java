package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.crypto.AesGcmCipher;
import org.chobit.knot.gateway.crypto.CredentialEncryption;
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
 * 启动时将库内历史明文凭证加密为 {@code ENC:} 格式（幂等，已加密则跳过）。
 */
@Component
@Order(100)
public class ProviderCredentialEncryptionMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderCredentialEncryptionMigrator.class);

    private final ProviderCredentialMapper providerCredentialMapper;
    private final CredentialEncryption credentialEncryption;

    public ProviderCredentialEncryptionMigrator(ProviderCredentialMapper providerCredentialMapper,
                                                CredentialEncryption credentialEncryption) {
        this.providerCredentialMapper = providerCredentialMapper;
        this.credentialEncryption = credentialEncryption;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ProviderCredentialEntity> credentials = providerCredentialMapper.listActiveAll();
        int migrated = 0;
        for (ProviderCredentialEntity entity : credentials) {
            if (migrateEntity(entity)) {
                providerCredentialMapper.update(entity);
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("provider credential encryption migration: updated {} row(s)", migrated);
        }
    }

    private boolean migrateEntity(ProviderCredentialEntity entity) {
        boolean changed = false;
        if (needsEncrypt(entity.getEncryptedKey())) {
            entity.setEncryptedKey(credentialEncryption.encrypt(entity.getEncryptedKey()));
            changed = true;
        }
        if (needsEncrypt(entity.getEncryptedSecret())) {
            entity.setEncryptedSecret(credentialEncryption.encrypt(entity.getEncryptedSecret()));
            changed = true;
        }
        if (needsEncrypt(entity.getTokenValue())) {
            entity.setTokenValue(credentialEncryption.encrypt(entity.getTokenValue()));
            changed = true;
        }
        return changed;
    }

    private static boolean needsEncrypt(String stored) {
        return stored != null && !stored.isBlank() && !AesGcmCipher.isEncrypted(stored);
    }
}
