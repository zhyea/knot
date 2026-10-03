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
                                 @Param("modelFamilyCode") String modelFamilyCode,
                                 @Param("code") String code);

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

    /** 原地更新版本配置内容（计费字段 + config_json + 生效期 + 状态），不生成新版本 */
    int updateVersionContent(BillingRuleVersionEntity entity);

    /** 同一规则下历史版本号的最大序号（version_code 形如 v{n}），用于生成初始 version_code（max+1） */
    int maxVersionSeq(Long ruleId);

    int insertVersion(BillingRuleVersionEntity entity);

    int updateVersionStatus(@Param("id") Long id, @Param("status") String status);
}
