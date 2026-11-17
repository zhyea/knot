package org.chobit.knot.gateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.chobit.knot.gateway.config.ConfigCacheDomain;
import org.chobit.knot.svc.service.GatewayConfigQueryService;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.chobit.knot.svc.vo.gateway.AppConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守门：整域失效真的清空该域缓存，且不误伤其它域。
 *
 * <p>两条断言都只有运行时症状——漏登记一个域，配置改了永远不生效且没有任何报错；
 * 误清整个实例则会让每次改配置都触发一次全量回源风暴。</p>
 */
class GatewayConfigCacheServiceInvalidateTest {

    @Test
    void invalidateClearsOnlyTheNamedDomain() throws Exception {
        GatewayConfigCacheService service = newService();
        service.getModelById(1L);
        service.getAppById(1L);
        assertEquals(1, size(service, "modelByIdCache"));
        assertEquals(1, size(service, "appByIdCache"));

        service.invalidate(EnumSet.of(ConfigCacheDomain.MODEL));

        assertEquals(0, size(service, "modelByIdCache"), "MODEL 域应被清空");
        assertEquals(1, size(service, "appByIdCache"), "APP 域不应被连带清空");
    }

    @Test
    void moderationWordDomainIsSkippedInsteadOfThrowing() throws Exception {
        GatewayConfigCacheService service = newService();
        service.getModelById(1L);

        // 词库副本不在本类内，交给 ModerationWordResource；这里必须安静跳过而不是 NPE
        assertDoesNotThrow(() -> service.invalidate(EnumSet.of(ConfigCacheDomain.MODERATION_WORD)));
        assertEquals(1, size(service, "modelByIdCache"));
    }

    @Test
    void everyDomainExceptModerationWordHasAnInvalidator() throws Exception {
        GatewayConfigCacheService service = newService();

        Set<ConfigCacheDomain> registered = EnumSet.copyOf(invalidatorKeys(service));

        assertEquals(EnumSet.complementOf(EnumSet.of(ConfigCacheDomain.MODERATION_WORD)), registered,
                "新增域必须在 buildInvalidators 里登记，否则该域永远不失效");
    }

    @Test
    void nullOrEmptyDomainsIsNoOp() throws Exception {
        GatewayConfigCacheService service = newService();
        service.getModelById(1L);

        assertDoesNotThrow(() -> service.invalidate(null));
        assertDoesNotThrow(() -> service.invalidate(EnumSet.noneOf(ConfigCacheDomain.class)));
        assertEquals(1, size(service, "modelByIdCache"));
    }

    private static GatewayConfigCacheService newService() {
        return new GatewayConfigCacheService(new EmptyQuery(),
                new ResourceTrafficPolicySupport(null, null, null, null),
                Runnable::run);
    }

    @SuppressWarnings("unchecked")
    private static Cache<Object, Object> cacheOf(GatewayConfigCacheService service, String fieldName)
            throws ReflectiveOperationException {
        Field field = GatewayConfigCacheService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Cache<Object, Object>) field.get(service);
    }

    private static long size(GatewayConfigCacheService service, String fieldName) throws ReflectiveOperationException {
        Cache<Object, Object> cache = cacheOf(service, fieldName);
        // estimatedSize 是近似值，invalidateAll 后需先跑维护任务才反映真实条目数
        cache.cleanUp();
        return cache.estimatedSize();
    }

    @SuppressWarnings("unchecked")
    private static Set<ConfigCacheDomain> invalidatorKeys(GatewayConfigCacheService service)
            throws ReflectiveOperationException {
        Field field = GatewayConfigCacheService.class.getDeclaredField("invalidators");
        field.setAccessible(true);
        Map<ConfigCacheDomain, Runnable> invalidators = (Map<ConfigCacheDomain, Runnable>) field.get(service);
        assertTrue(!invalidators.isEmpty(), "失效动作必须完成装配");
        return invalidators.keySet();
    }

    /** 构造器是纯赋值，读方法全部覆写：不需要真实 Mapper。 */
    private static final class EmptyQuery extends GatewayConfigQueryService {

        private EmptyQuery() {
            super(null, null, null, null, null, null, null, null, null, null, null);
        }

        /** 本测试只看缓存的条目数与失效范围，取不到数据即可；不覆写就会落到真 Mapper 上 NPE。 */
        @Override
        public ModelConfigVo getModelById(Long id) {
            return null;
        }

        @Override
        public AppConfigVo getAppById(Long id) {
            return null;
        }
    }
}
