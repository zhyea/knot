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
                                 @Param("code") String code,
                                 @Param("includeDeleted") Boolean includeDeleted);

    /** 报表用：全量非删除规则（含当前版本字段），不分页 */
    List<BillingRuleEntity> listForReport();

    BillingRuleEntity getById(Long id);

    /** 含已删除：恢复前置校验用（查询侧唯一的「越过 is_deleted」入口） */
    BillingRuleEntity getByIdIncludingDeleted(@Param("id") Long id);

    /** 按业务码取规则（跨模块绑定一律用 code） */
    BillingRuleEntity getByCode(String code);

    Long countByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    /** 按业务码统计绑定了该规则的供应商模型数 */
    Long countBoundModels(@Param("code") String code);

    /** 计费路径：按规则业务码取当前生效版本 */
    BillingRuleEntity getActiveByRuleCode(@Param("ruleCode") String ruleCode,
                                          @Param("effectiveAt") LocalDateTime effectiveAt);

    /**
     * 计费路径回退：模型未显式绑定规则时按模型族取当前生效规则。
     *
     * <p>族精确匹配优先，其次回退 {@code model_family} 为空的默认规则（覆盖所有族）。</p>
     */
    BillingRuleEntity getActiveByModelFamily(@Param("modelFamilyCode") String modelFamilyCode,
                                             @Param("effectiveAt") LocalDateTime effectiveAt);

    int insert(BillingRuleEntity entity);

    int update(BillingRuleEntity entity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status);

    /** 逻辑删除：is_deleted = 1 且 status = 0（删时强制停用），条件更新保证幂等 */
    int logicalDelete(Long id);

    /** 恢复：is_deleted = 0，status 保持 0（是否启用由调用方显式决定） */
    int restore(Long id);

    BillingRuleVersionEntity getLatestVersion(@Param("ruleCode") String ruleCode);

    /** 原地更新版本配置内容（计费字段 + config_json + 生效期 + 状态），不生成新版本 */
    int updateVersionContent(BillingRuleVersionEntity entity);

    /** 同一规则下历史版本号的最大序号（version_code 形如 v{n}），用于生成初始 version_code（max+1） */
    int maxVersionSeq(@Param("ruleCode") String ruleCode);

    int insertVersion(BillingRuleVersionEntity entity);

    int updateVersionStatus(@Param("id") Long id, @Param("status") Integer status);
}
