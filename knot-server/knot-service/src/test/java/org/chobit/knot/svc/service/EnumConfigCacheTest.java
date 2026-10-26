package org.chobit.knot.svc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.chobit.knot.dal.entity.EnumCategoryEntity;
import org.chobit.knot.dal.entity.EnumConfigEntity;
import org.chobit.knot.dal.mapper.EnumCategoryMapper;
import org.chobit.knot.dal.mapper.EnumConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 枚举项缓存契约测试：证明 {@code @Cacheable} 真的命中、{@code @CacheEvict} 真的失效。
 *
 * <p>前端下拉改为每次打开抽屉强制回源后，DB 压力全靠这层缓存吸收，所以"缓存有没有生效"是
 * 硬要求而不是优化项：
 * <ul>
 *   <li>命中：同一分类第二次读取不再打 Mapper（否则等于没加缓存）；</li>
 *   <li>失效：create/update/delete 之后必须重新回源（否则改完枚举下拉看不到新值，
 *       正是本次要修的缺陷的后端镜像）。</li>
 * </ul>
 * 缓存实现用 {@link ConcurrentMapCacheManager}：本测试验证的是**注解语义**，不关心 TTL 策略；
 * 真实 Caffeine 配置由 knot-admin 的 CacheConfiguration 提供（AppControllerTest 启动上下文即验证）。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EnumConfigCacheTest.CacheTestConfiguration.class)
class EnumConfigCacheTest {

    private static final String CATEGORY = "model_family";
    private static final Long CATEGORY_ID = 7L;

    @Autowired
    private EnumConfigService service;

    @Autowired
    private EnumConfigMapper enumConfigMapper;

    @Autowired
    private EnumCategoryMapper enumCategoryMapper;

    @Autowired
    private CacheManager cacheManager;

    /**
     * 上下文在本测试类内复用（Spring 的 context 缓存），缓存条目与 mock 桩都是单例、
     * 会跨方法残留：不清的话上一个方法缓存的空列表会污染下一个方法的首次读取。
     */
    @BeforeEach
    void resetCacheAndMocks() {
        reset(enumConfigMapper, enumCategoryMapper);
        cacheManager.getCache(EnumConfigService.ENUM_ITEMS_CACHE).clear();
    }

    private EnumConfigEntity item(String itemCode) {
        EnumConfigEntity entity = new EnumConfigEntity();
        entity.setItemCode(itemCode);
        entity.setItemLabel(itemCode + " 显示名");
        return entity;
    }

    private void stubCategory() {
        EnumCategoryEntity category = new EnumCategoryEntity();
        category.setId(CATEGORY_ID);
        category.setCategory(CATEGORY);
        when(enumCategoryMapper.selectByCategory(CATEGORY)).thenReturn(category);
    }

    @Test
    @DisplayName("同一分类连续读取只回源一次，第二次命中缓存")
    void listByCategoryServesSecondReadFromCache() {
        stubCategory();
        when(enumConfigMapper.listByCategoryId(CATEGORY_ID)).thenReturn(List.of(item("gpt")));

        assertEquals(1, service.listByCategory(CATEGORY).size());
        assertEquals(1, service.listByCategory(CATEGORY).size());

        verify(enumConfigMapper, times(1)).listByCategoryId(CATEGORY_ID);
    }

    @Test
    @DisplayName("新增枚举项后缓存整表失效，下一次读取重新回源")
    void createEvictsCache() {
        stubCategory();
        when(enumConfigMapper.listByCategoryId(CATEGORY_ID)).thenReturn(List.of(item("gpt")));
        assertEquals(1, service.listByCategory(CATEGORY).size());

        EnumConfigEntity request = new EnumConfigEntity();
        request.setCategory(CATEGORY);
        request.setItemCode("claude");
        when(enumConfigMapper.getByCategoryAndCode(CATEGORY, "claude")).thenReturn(null);
        when(enumCategoryMapper.selectIdByCategory(CATEGORY)).thenReturn(CATEGORY_ID);
        when(enumConfigMapper.insert(any(EnumConfigEntity.class))).thenReturn(1);
        service.create(request);

        when(enumConfigMapper.listByCategoryId(CATEGORY_ID))
                .thenReturn(List.of(item("gpt"), item("claude")));
        assertEquals(2, service.listByCategory(CATEGORY).size());
        verify(enumConfigMapper, times(2)).listByCategoryId(CATEGORY_ID);
    }

    @Test
    @DisplayName("更新枚举项后缓存整表失效")
    void updateEvictsCache() {
        stubCategory();
        when(enumConfigMapper.listByCategoryId(CATEGORY_ID)).thenReturn(List.of(item("gpt")));
        assertEquals(1, service.listByCategory(CATEGORY).size());

        EnumConfigEntity existing = new EnumConfigEntity();
        existing.setId(11L);
        existing.setCategory(CATEGORY);
        existing.setItemCode("gpt");
        existing.setIsSystem(false);
        when(enumConfigMapper.getById(11L)).thenReturn(existing);
        when(enumConfigMapper.countItemCodeBindings(anyString())).thenReturn(0L);

        EnumConfigEntity request = new EnumConfigEntity();
        request.setItemLabel("GPT 新版显示名");
        service.update(11L, request);

        when(enumConfigMapper.listByCategoryId(CATEGORY_ID)).thenReturn(List.of(item("gpt")));
        service.listByCategory(CATEGORY);
        verify(enumConfigMapper, times(2)).listByCategoryId(CATEGORY_ID);
    }

    @Test
    @DisplayName("删除枚举项后缓存整表失效")
    void deleteEvictsCache() {
        stubCategory();
        when(enumConfigMapper.listByCategoryId(CATEGORY_ID)).thenReturn(List.of(item("gpt")));
        assertEquals(1, service.listByCategory(CATEGORY).size());

        EnumConfigEntity existing = new EnumConfigEntity();
        existing.setId(12L);
        existing.setCategory(CATEGORY);
        existing.setItemCode("gpt");
        existing.setIsSystem(false);
        when(enumConfigMapper.getById(12L)).thenReturn(existing);
        when(enumConfigMapper.logicalDelete(anyLong())).thenReturn(1);

        service.deleteReturning(12L);

        when(enumConfigMapper.listByCategoryId(CATEGORY_ID)).thenReturn(List.of());
        assertEquals(0, service.listByCategory(CATEGORY).size());
        verify(enumConfigMapper, times(2)).listByCategoryId(CATEGORY_ID);
    }

    /**
     * 只装配"service + mock mapper + 缓存基础设施"的最小上下文，不牵扯数据源。
     */
    @Configuration
    @EnableCaching
    static class CacheTestConfiguration {

        @Bean
        EnumConfigMapper enumConfigMapper() {
            return mock(EnumConfigMapper.class);
        }

        @Bean
        EnumCategoryMapper enumCategoryMapper() {
            return mock(EnumCategoryMapper.class);
        }

        @Bean
        EnumConfigService enumConfigService(EnumConfigMapper enumConfigMapper,
                                            EnumCategoryMapper enumCategoryMapper) {
            return new EnumConfigService(enumConfigMapper, enumCategoryMapper);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(EnumConfigService.ENUM_ITEMS_CACHE);
        }
    }
}
