package org.chobit.knot.svc.vo.gateway;

/**
 * 网关配置快照：供应商模型（上游 model 参数、计费规则绑定、模型族回退）。
 *
 * <p>⭐ <b>为什么不复用 admin 的同名 {@code Item}</b>：那批 record 是<b>展示语义</b>
 * ——例如 {@code ModelApiBindingItem} 是 {@code boolean enabled}，而网关读的是
 * {@code status}（{@code EnabledStatusEnum} 整型码）。字段名与类型都不同，
 * 硬套会倒逼下游改判定口径，比让下游 import 实体更糟。</p>
 *
 * <p>⚠ <b>字段逐字平移 {@code ModelEntity}，不裁剪</b>：裁掉任何一个字段，下游就会为了它回退去
 * import {@code dal.entity}，{@code LayeringGuardTest} 的实体棘轮也就白清了。
 * 加实体字段时 {@code GatewayConfigConverter} 必须同步——漏一个不报错，
 * 表现是「新字段读出来恒为 null」，靠 {@code GatewayConfigConverterTest} 的逐字段断言钉住。</p>
 */
public record ModelConfigVo(
        Long id,
        String providerAccountCode,
        String providerName,
        String providerCode,
        String modelCode,
        String upstreamModel,
        String logicalModelCode,
        String modelType,
        String modelFamilyCode,
        String version,
        String basePath,
        String remark,
        String billingRuleCode,
        Integer status,
        Integer isDeleted
) {
}
