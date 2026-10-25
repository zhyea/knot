package org.chobit.knot.svc.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CredentialProperties.class)
public class CredentialConfiguration {
}
