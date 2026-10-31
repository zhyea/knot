package org.chobit.knot.svc.vo.gateway;

/**
 * 网关配置快照：模型池（池内选择与粘性路由策略）。
 *
 * <p>⭐ <b>为什么不复用 admin 的同名 {@code Item}</b>：那批 record 是<b>展示语义</b>
 * ——例如 {@code ModelApiBindingItem} 是 {@code boolean enabled}，而网关读的是
 * {@code status}（{@code EnabledStatusEnum} 整型码）。字段名与类型都不同，
 * 硬套会倒逼下游改判定口径，比让下游 import 实体更糟。</p>
 *
 * <p>⚠ <b>字段逐字平移 {@code ModelPoolEntity}，不裁剪</b>：裁掉任何一个字段，下游就会为了它回退去
 * import {@code dal.entity}，{@code LayeringGuardTest} 的实体棘轮也就白清了。
 * 加实体字段时 {@code GatewayConfigConverter} 必须同步——漏一个不报错，
 * 表现是「新字段读出来恒为 null」，靠 {@code GatewayConfigConverterTest} 的逐字段断言钉住。</p>
 */
public record ModelPoolConfigVo(
        Long id,
        String poolCode,
        String name,
        String logicalModelCode,
        String logicalModelName,
        String modelType,
        String selectionStrategy,
        String stickyPolicy,
        Integer status,
        Integer isDeleted,
        String remark
) {
}
