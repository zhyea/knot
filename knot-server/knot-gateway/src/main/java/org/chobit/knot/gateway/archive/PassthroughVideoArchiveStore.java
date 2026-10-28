package org.chobit.knot.gateway.archive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认转储实现：什么都不做，把供应商给的地址原样返回。
 *
 * <p>这不是「占位实现」，而是<b>正确实现</b>：没有配置对象存储时，供应商地址就是唯一存在的地址。
 * 换个说法——客户端本来就该拿到它，转储层在这里的职责是「不添乱」，不是「假装做过事」。</p>
 *
 * <p>之所以仍要有一个 bean 而不是让调用方判空：判空会让「没配存储」与「配了但注入失败」
 * 变成同一个分支，后者是启动期缺陷却被静默当成正常状态。有实现就有确定行为，
 * 要换存储只需换 bean（见 {@link VideoArchiveStore} 关于配置切换的说明）。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "knot.gateway.video-archive", name = "type",
        havingValue = "passthrough", matchIfMissing = true)
public class PassthroughVideoArchiveStore implements VideoArchiveStore {

    @Override
    public String archive(VideoAsset asset) {
        return asset.sourceUrl();
    }
}
