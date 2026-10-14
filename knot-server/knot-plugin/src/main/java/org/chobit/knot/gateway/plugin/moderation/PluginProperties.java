package org.chobit.knot.gateway.plugin.moderation;

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
 * 随 {@code ModerationWordResource} 内联，不走配置。</p>
 */
@Data
@ConfigurationProperties(prefix = "knot.plugin")
public class PluginProperties {

    private Moderation moderation = new Moderation();

    /**
     * 敏感词插件配置：{@code knot.plugin.moderation.*}。
     */
    @Data
    public static class Moderation {

        /**
         * 处置动作：BLOCK / MASK / FLAG（默认 FLAG 影子模式）。
         */
        private String action = ModerationActionEnum.FLAG.code();

        /**
         * 作用方向：REQUEST / RESPONSE / BOTH。
         */
        private String scope = ModerationScopeEnum.BOTH.code();

        /**
         * 生效动作：非法配置值回退 FLAG，不阻断启动。
         */
        public ModerationActionEnum actionEnum() {
            return ModerationActionEnum.ofOrDefault(action, ModerationActionEnum.FLAG);
        }

        /**
         * 生效方向：非法配置值回退 BOTH。
         */
        public ModerationScopeEnum scopeEnum() {
            return ModerationScopeEnum.ofOrDefault(scope, ModerationScopeEnum.BOTH);
        }
    }
}
