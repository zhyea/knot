package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.BillingRuleVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BillingRuleMapper {

    List<BillingRuleEntity> list(@Param("keyword") String keyword,
                                 @Param("providerCode") String providerCode,
                                 @Param("logicalModelCode") String logicalModelCode);

    BillingRuleEntity getById(Long id);

    Long countByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    Long countBoundModels(Long id);

    BillingRuleEntity getActiveByRuleId(@Param("ruleId") Long ruleId,
                                        @Param("effectiveAt") LocalDateTime effectiveAt);

    List<BillingRuleEntity> listActiveCandidates(@Param("providerCode") String providerCode,
                                                 @Param("modelId") Long modelId,
                                                 @Param("effectiveAt") LocalDateTime effectiveAt);

    int insert(BillingRuleEntity entity);

    int update(BillingRuleEntity entity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status);

    /** 生命周期删除：status 置 DELETED，查询侧排除 */
    int deleteRule(Long id);

    BillingRuleVersionEntity getLatestVersion(Long ruleId);

    BillingRuleVersionEntity getVersionByHash(@Param("ruleId") Long ruleId,
                                              @Param("uniqHash") String uniqHash);

    /** 激活指定版本并刷新生效时间（规则内其余 ACTIVE 版本由 disableOtherActiveVersions 收敛） */
    int activateVersion(@Param("id") Long id, @Param("effectiveFrom") LocalDateTime effectiveFrom);

    /** 维持「同一规则仅一个 ACTIVE 版本」不变量 */
    int disableOtherActiveVersions(@Param("ruleId") Long ruleId, @Param("keepId") Long keepId);

    /** 同一规则下的版本数量，用于生成 version_code（v{n}） */
    int countVersions(Long ruleId);

    int insertVersion(BillingRuleVersionEntity entity);

    int updateVersionStatus(@Param("id") Long id, @Param("status") String status);
}
