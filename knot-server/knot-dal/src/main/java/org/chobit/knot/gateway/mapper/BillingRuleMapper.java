package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.BillingRuleVersionEntity;
import org.chobit.knot.gateway.entity.BillingRuleVersionItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BillingRuleMapper {

    List<BillingRuleEntity> list(@Param("keyword") String keyword,
                                 @Param("providerId") Long providerId,
                                 @Param("logicalModelId") Long logicalModelId);

    BillingRuleEntity getById(Long id);

    Long countByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    Long countBoundModels(Long id);

    BillingRuleEntity getActiveByRuleId(@Param("ruleId") Long ruleId,
                                        @Param("effectiveAt") LocalDateTime effectiveAt);

    List<BillingRuleEntity> listActiveCandidates(@Param("providerId") Long providerId,
                                                 @Param("modelId") Long modelId,
                                                 @Param("effectiveAt") LocalDateTime effectiveAt);

    int insert(BillingRuleEntity entity);

    int update(BillingRuleEntity entity);

    int softDelete(Long id);

    int updateCurrentVersion(@Param("id") Long id, @Param("currentVersionId") Long currentVersionId);

    Integer maxVersionNo(Long ruleId);

    int insertVersion(BillingRuleVersionEntity entity);

    int insertVersionItem(BillingRuleVersionItemEntity entity);
}
