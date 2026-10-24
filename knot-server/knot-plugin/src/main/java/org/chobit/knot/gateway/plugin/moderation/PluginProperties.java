package org.chobit.knot.gateway.plugin.moderation;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.chobit.knot.gateway.constants.enums.ModerationActionEnum;
import org.chobit.knot.gateway.constants.enums.ModerationScopeEnum;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 敏感词插件配置：绑定 {@code knot.plugin.*} 前缀，随插件落在 knot-plugin
 * （2026-10-14 由 knot-gateway 迁入——配置消费方只剩守卫）。
 *
 * <p>敏感词插件的 action / scope 归到 {@code knot.plugin.moderation.*} 下。
 * 是否拦截由插件实例启停控制，不在此配置；词库时序参数（网关侧缓存刷新）
 * 随 {@link ModerationWordResource} 内联，不走配置。</p>
 */
@Data
@ConfigurationProperties(prefix = "knot.plugin")
public class PluginProperties {

    private Moderation moderation = new Moderation();

    /**
     * 启动期校验：{@code action} 必须是已知动作，否则直接失败。
     *
     * <p>不兜底、不降级——配置写错（含拼写错误、已移除的 MASK）必须在启动时暴露，
     * 而不是等到请求进来按默认值静默跑（2026-10-24 MASK 移除后确立）。</p>
     */
    @PostConstruct
    private void validateAction() {
        if (moderation == null) {
            return;
        }
        ModerationActionEnum resolved = ModerationActionEnum.fromCode(moderation.getAction());
        if (resolved == null) {
            throw new IllegalStateException(String.format(
                    "knot.plugin.moderation.action 取值非法: %s（合法值: BLOCK / FLAG）。"
                            + "该配置不兜底不降级，写错即启动失败",
                    moderation.getAction()));
        }
    }

    /**
     * 敏感词插件配置：{@code knot.plugin.moderation.*}。
     */
    @Data
    public static class Moderation {

        /**
         * 处置动作：BLOCK / FLAG（默认 FLAG 影子模式）。
         */
        private String action = ModerationActionEnum.FLAG.code();

        /**
         * 作用方向：REQUEST / RESPONSE / BOTH。
         */
        private String scope = ModerationScopeEnum.BOTH.code();

        /**
         * 生效动作：值已在启动期校验（见 {@link PluginProperties#validateAction()}），此处直接解析。
         */
        public ModerationActionEnum actionEnum() {
            return ModerationActionEnum.fromCode(action);
        }

        /**
         * 生效方向：非法配置值回退 BOTH。
         */
        public ModerationScopeEnum scopeEnum() {
            return ModerationScopeEnum.ofOrDefault(scope, ModerationScopeEnum.BOTH);
        }
    }
}
