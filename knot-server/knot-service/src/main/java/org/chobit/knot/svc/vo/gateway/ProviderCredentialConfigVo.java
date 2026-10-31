package org.chobit.knot.svc.vo.gateway;

/**
 * 网关配置快照：供应商凭据（鉴权策略与加密配置）。
 *
 * <p>⭐ <b>为什么不复用 admin 的同名 {@code Item}</b>：那批 record 是<b>展示语义</b>
 * ——例如 {@code ModelApiBindingItem} 是 {@code boolean enabled}，而网关读的是
 * {@code status}（{@code EnabledStatusEnum} 整型码）。字段名与类型都不同，
 * 硬套会倒逼下游改判定口径，比让下游 import 实体更糟。</p>
 *
 * <p>⚠ <b>字段逐字平移 {@code ProviderCredentialEntity}，不裁剪</b>：裁掉任何一个字段，下游就会为了它回退去
 * import {@code dal.entity}，{@code LayeringGuardTest} 的实体棘轮也就白清了。
 * 加实体字段时 {@code GatewayConfigConverter} 必须同步——漏一个不报错，
 * 表现是「新字段读出来恒为 null」，靠 {@code GatewayConfigConverterTest} 的逐字段断言钉住。</p>
 *
 * <p>⚠⚠ <b>本快照含 {@code encryptedConfig} 与 {@code authApplier}，属于敏感数据</b>：
 * 它只在网关进程内被上游鉴权链路消费，<b>绝不能当 HTTP 响应体吐出去</b>。
 * 之所以不在这里裁剪，是因为裁掉它网关就没法向上游发请求——安全边界靠「不对外暴露」，
 * 不靠「载体里没有这个字段」。</p>
 */
public record ProviderCredentialConfigVo(
        Long id,
        Long providerId,
        String providerAccountCode,
        String credentialType,
        String authApplier,
        String encryptedConfig,
        Integer status
) {
}
