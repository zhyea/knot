package org.chobit.knot.admin.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.chobit.knot.svc.service.EnumConfigService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 管理端本地缓存。目前只注册 DB 枚举项一处（{@link EnumConfigService#ENUM_ITEMS_CACHE}）。
 *
 * <p>背景：DB 枚举（ks_enum_configs）的下拉选项随时可增删改，前端已改为每次打开抽屉强制回源
 * （不再依赖前端那份 5 分钟模块级缓存——它正是"改完枚举下拉看不到新值"的根因）。
 * 回源压力全部落到 {@code GET /api/system/enums/items/{category}}，故在服务端兜一层短 TTL 的
 * Caffeine：同一分类在一个 TTL 窗口内只读一次 DB。
 *
 * <p>一致性：写操作由 {@link EnumConfigService} 上的 {@code @CacheEvict} 立即整表失效，
 * 因此走接口的改动是即时可见的；即便有人绕过接口直接改库，最坏也只是本窗口的陈旧读。
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

    /** 枚举项缓存窗口：短 TTL 优先保证"改完立刻能看到"，压力交给缓存命中率承担 */
    private static final long ENUM_ITEMS_TTL_SECONDS = 30L;

    /** 分类数很少（DB 维护的仅 4 个），上限只作防御性约束 */
    private static final int ENUM_ITEMS_MAX_SIZE = 64;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(EnumConfigService.ENUM_ITEMS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ENUM_ITEMS_TTL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(ENUM_ITEMS_MAX_SIZE));
        return manager;
    }
}
