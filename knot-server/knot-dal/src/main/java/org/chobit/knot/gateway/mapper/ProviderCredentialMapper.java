package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;

import java.util.List;

@Mapper
public interface ProviderCredentialMapper {

    ProviderCredentialEntity getActiveByProviderId(@Param("providerId") Long providerId);

    List<ProviderCredentialEntity> listActiveByProviderIds(@Param("providerIds") List<Long> providerIds);

    List<ProviderCredentialEntity> listActiveAll();

    int insert(ProviderCredentialEntity entity);

    int update(ProviderCredentialEntity entity);

    int deactivateByProviderId(@Param("providerId") Long providerId);
}
