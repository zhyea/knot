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
                                 @Param("logicalModelCode") String logicalModelCode);

    /** 报表用：全量非删除规则（含当前版本字段），不分页 */
    List<BillingRuleEntity> listForReport();

    BillingRuleEntity getById(Long id);

    /** 按业务码取规则（跨模块绑定一律用 code） */
    BillingRuleEntity getByCode(String code);

    Long countByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    /** 按业务码统计绑定了该规则的供应商模型数 */
    Long countBoundModels(@Param("code") String code);

    /** 计费路径：按规则业务码取当前生效版本 */
    BillingRuleEntity getActiveByRuleCode(@Param("ruleCode") String ruleCode,
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

    /** 同一规则下历史版本号的最大序号（version_code 形如 v{n}），用于生成 version_code（max+1，物理删版本行不撞号） */
    int maxVersionSeq(Long ruleId);

    int insertVersion(BillingRuleVersionEntity entity);

    int updateVersionStatus(@Param("id") Long id, @Param("status") String status);
}
