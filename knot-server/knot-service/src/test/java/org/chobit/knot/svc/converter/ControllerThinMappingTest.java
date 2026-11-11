package org.chobit.knot.svc.converter;

import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;
import org.chobit.knot.svc.dto.moderation.ModerationWordDto;
import org.chobit.knot.svc.dto.provider.DiscountPolicyDto;
import org.chobit.knot.svc.dto.routing.RoutingConsumerDto;
import org.chobit.knot.svc.vo.moderation.ModerationWordItem;
import org.chobit.knot.svc.vo.provider.DiscountPolicy;
import org.chobit.knot.svc.vo.routing.RoutingConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「从 controller 搬进 converter」的三组映射的守卫（配合 {@code ControllerThinGuardTest} 的
 * {@code new XxxDto} 规则）。
 *
 * <p>⭐ <b>为什么这些映射需要测试而不是搬完就算</b>：三个映射都是「同名同型逐字段平移」，
 * 出错时编译期一声不响——MapStruct 只会 {@code WARN unmapped target property}，
 * 手写的那份则是「某个字段悄悄恒为 null」。而它们的调用点全在 controller，
 * 只有起了 MVC 才触达，所以漏拷字段的表现是「前端某个格子永远是空」，排查成本极高。
 * 这与 {@code GatewayConfigConverterTest} / {@code AsyncTaskConverterTest} 是同一类理由。</p>
 *
 * <p>这里不做那两份的反射全覆盖（它们是 11 组 × 百余字段的批量搬运，值得上框架），
 * 只钉三件事：<b>字段集两侧对齐</b>（有人只改一侧时编译不报错）、
 * <b>逐字段值准确</b>（串位/漏拷）、<b>请求体不能写回服务端字段</b>（这是搬出来时唯一带上语义的地方）。</p>
 */
class ControllerThinMappingTest {

    /**
     * ⚠ {@link ModelConverter} 是 Spring 组件模型、且有 {@code uses = SharedMappings}：
     * 用 {@code Mappers.getMapper} 拿到的是「依赖未注入」的实例。
     * 这里只调折扣策略那两个方法，它们不碰 {@code SharedMappings}，所以不用起容器。
     * 若将来新加的断言走到 status/enabled 或 JSON 策略列，就必须改成起 Spring 上下文。
     */
    private static final ModelConverter MODEL_CONVERTER = Mappers.getMapper(ModelConverter.class);

    private static final RoutingConsumerConverter CONSUMER_CONVERTER =
            Mappers.getMapper(RoutingConsumerConverter.class);

    private static final ModerationWordConverter WORD_CONVERTER = new ModerationWordConverter();

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 11, 9, 30);

    // ==================== 路由消费者 ====================

    @Test
    @DisplayName("消费者 DTO ↔ 视图：11 个字段逐字搬运，两个策略对象原样穿过")
    void routingConsumerMappingIsFieldAccurateBothWays() {
        RateLimitPolicy rateLimit = new RateLimitPolicy(11, 22);
        List<QuotaPolicy> quotas = List.of(new QuotaPolicy(33L, new BigDecimal("4.5"), "USD", "MONTH"));
        RoutingConsumerDto dto = new RoutingConsumerDto(
                1L, "consumer-cs", "客服场景", "robin", "游承峰",
                "sk-consumer", true, false, 7L, rateLimit, quotas);

        RoutingConsumer vo = CONSUMER_CONVERTER.toVO(dto);

        assertEquals(Long.valueOf(1L), vo.id());
        assertEquals("consumer-cs", vo.consumerCode());
        assertEquals("客服场景", vo.name());
        assertEquals("robin", vo.userUsername());
        assertEquals("游承峰", vo.userName());
        assertEquals("sk-consumer", vo.secretKey(), "详情接口按需返回密钥，搬运不能把它丢掉");
        assertTrue(vo.returnUsageDetail());
        assertFalse(vo.enabled());
        assertEquals(Long.valueOf(7L), vo.ruleCount());
        assertSame(rateLimit, vo.rateLimitPolicy());
        // ⚠ MapStruct 对集合是「重建一个新列表 + 元素原样放入」，所以这里只能断言内容相等。
        // 别指望拿到入参的同一个 List 实例——哪天有人改成按引用透传，这条也不会红，那没关系；
        // 但反过来（把列表写成空/漏拷）必须红。
        assertEquals(quotas, vo.quotaPolicies());
        assertSame(quotas.get(0), vo.quotaPolicies().get(0), "元素本身必须是同一个引用，不能被重新构造");

        RoutingConsumerDto back = CONSUMER_CONVERTER.toDto(vo);

        assertEquals(dto, back, "往返一次必须回到原值：任何字段被漏拷或串位这里就不等了");
    }

    @Test
    @DisplayName("消费者 DTO/视图的字段集一致：只改一侧时编译不报错，只能靠这条拦")
    void routingConsumerFieldSetsMatch() {
        assertEquals(recordComponentNames(RoutingConsumerDto.class), recordComponentNames(RoutingConsumer.class),
                "两侧字段集不一致：MapStruct 会把未知的一侧留空（只报 WARN），表现为「某个字段恒为 null」");
    }

    // ==================== 折扣策略 ====================

    @Test
    @DisplayName("折扣策略请求体无法写回 id：归属只认路径变量 model_code")
    void discountPolicyRequestCannotSmuggleId() {
        DiscountPolicyDto dto = MODEL_CONVERTER.toDiscountPolicyDto(
                new DiscountPolicy(99L, "PERCENTAGE", new BigDecimal("0.85"), 1));

        assertNull(dto.id(),
                "请求体带来的 id 必须丢掉：归属由路径变量 model_code + policyId 决定，"
                        + "把请求体 id 带进去等于允许跨策略改");
        assertEquals("PERCENTAGE", dto.discountType());
        assertEquals(new BigDecimal("0.85"), dto.discountValue());
        assertEquals(Integer.valueOf(1), dto.status());
    }

    @Test
    @DisplayName("折扣策略响应视图保留 id（前端要靠它发 update/delete）")
    void discountPolicyResponseKeepsId() {
        DiscountPolicy vo = MODEL_CONVERTER.toDiscountPolicyVO(
                new DiscountPolicyDto(99L, "FIXED", new BigDecimal("2.5"), 0));

        assertEquals(Long.valueOf(99L), vo.id());
        assertEquals("FIXED", vo.discountType());
        assertEquals(new BigDecimal("2.5"), vo.discountValue());
        assertEquals(Integer.valueOf(0), vo.status(), "状态 0（停用）是合法值，不能被当成未配置");
    }

    @Test
    @DisplayName("折扣策略 DTO/视图的字段集一致")
    void discountPolicyFieldSetsMatch() {
        assertEquals(recordComponentNames(DiscountPolicyDto.class), recordComponentNames(DiscountPolicy.class));
    }

    // ==================== 敏感词 ====================

    @Test
    @DisplayName("敏感词请求体无法写回 normalizedWord / createdAt / updatedAt")
    void moderationWordRequestCannotWriteServerManagedFields() {
        ModerationWordItem item = new ModerationWordItem(
                5L, "坏词", "Spam", 2, "zh", true, false, "备注", CREATED, CREATED);

        ModerationWordDto dto = WORD_CONVERTER.toDto(item);

        assertEquals(Long.valueOf(5L), dto.id());
        assertEquals("坏词", dto.word());
        assertEquals("Spam", dto.categoryCode());
        assertEquals(Integer.valueOf(2), dto.severity());
        assertEquals("zh", dto.lang());
        assertTrue(dto.enabled());
        assertFalse(dto.deleted());
        assertEquals("备注", dto.remark());
        assertNull(dto.normalizedWord(),
                "归一化键由服务端按 word 现算：客户端送来的值与判定算法不一致时，"
                        + "库里就会出现一条永远匹配不上的词");
        assertNull(dto.createdAt(), "创建时间由库侧维护");
        assertNull(dto.updatedAt(), "更新时间由库侧 ON UPDATE 维护");
    }

    @Test
    @DisplayName("视图不暴露 normalizedWord，但仍要带出 createdAt / updatedAt")
    void moderationWordViewHidesNormalizedWord() {
        ModerationWordDto dto = new ModerationWordDto(
                5L, "坏词", "Spam", 2, "zh", true, false, "备注", "坏词", CREATED, CREATED.plusHours(1));

        ModerationWordItem vo = WORD_CONVERTER.toVO(dto);

        assertEquals(CREATED, vo.createdAt());
        assertEquals(CREATED.plusHours(1), vo.updatedAt());
        assertEquals(Set.of("id", "word", "categoryCode", "severity", "lang", "enabled",
                        "deleted", "remark", "createdAt", "updatedAt"),
                recordComponentNames(ModerationWordItem.class),
                "视图字段集变了的话，前端表单与 DTO 的边界要一起复核（normalizedWord 不能加回来）");
    }

    // ==================== 共同口径 ====================

    @Test
    @DisplayName("null 进 null 出：三个转换器都不允许把 null 变成「造一个空对象」")
    void nullMapsToNull() {
        assertNull(CONSUMER_CONVERTER.toVO(null));
        assertNull(CONSUMER_CONVERTER.toDto(null));
        assertNull(MODEL_CONVERTER.toDiscountPolicyVO(null));
        assertNull(MODEL_CONVERTER.toDiscountPolicyDto(null));
        assertNull(WORD_CONVERTER.toDto((ModerationWordItem) null));
        assertNull(WORD_CONVERTER.toVO(null));
    }

    private static Set<String> recordComponentNames(Class<?> type) {
        return Stream.of(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
