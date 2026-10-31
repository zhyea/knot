package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.AppEntity;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.ModelPoolEntity;
import org.chobit.knot.dal.entity.ModelPoolItemEntity;
import org.chobit.knot.dal.entity.ProviderAccountEntity;
import org.chobit.knot.dal.entity.ProviderCredentialEntity;
import org.chobit.knot.dal.entity.RoutingConsumerEntity;
import org.chobit.knot.dal.entity.RoutingRuleEntity;
import org.chobit.knot.dal.entity.RoutingRuleTargetEntity;
import org.chobit.knot.svc.vo.gateway.AppConfigVo;
import org.chobit.knot.svc.vo.gateway.BillingRuleConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelApiBindingConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelPoolConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelPoolItemConfigVo;
import org.chobit.knot.svc.vo.gateway.ProviderAccountConfigVo;
import org.chobit.knot.svc.vo.gateway.ProviderCredentialConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingConsumerConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingRuleConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingRuleTargetConfigVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GatewayConfigConverter} 的守卫：11 组「dal 实体 → svc 配置快照载体」的逐字段搬运。
 *
 * <p>⭐ <b>本测试存在的理由是「字段漏拷」编译期不报错</b>：MapStruct 的字段名对不上时
 * 只报 {@code WARN unmapped target property}，代码照常编译，表现是「新字段读出来恒为 null」，
 * 而且往往只在某个具体路由分支才显形。这与 {@code AsyncTaskConverterTest} 是同一类理由。</p>
 *
 * <p>⭐ <b>为什么用反射而不是手写 131 条 assertEquals</b>：这批载体的存在意义就是
 * 「字段逐字平移实体，一个都不裁」——裁掉任何一个，下游就会为了它回退去 import {@code dal.entity}，
 * {@code LayeringGuardTest} 的棘轮也就白清了。也就是说<b>覆盖率必须是 100%，且必须随实体加字段自动扩展</b>。
 * 手写 131 条的现实结果是「漏几条没人看得见」，而被漏掉的恰恰是下一次加的那个字段。
 * 反射版本把「全覆盖」变成结构性保证。</p>
 *
 * <p>⚠ <b>反射断言最容易掉进的坑：断言恒真。</b>如果实体没填上值、载体也没拷上值，
 * 两边都是 {@code null}，{@code assertEquals(null, null)} 是绿的。
 * 所以这里额外钉了两条「非空」断言（实体侧 + 载体侧），把恒真路径堵死在入口。
 * 另有一条 {@code sentinelValuesAreAllDistinct}：哨兵值一旦重复，「字段串位」就检测不出来，
 * 那条红了说明得换哨兵策略，而不是可以忽略。</p>
 */
class GatewayConfigConverterTest {

    private static final GatewayConfigConverter CONVERTER = Mappers.getMapper(GatewayConfigConverter.class);

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 16, 30, 0);

    /** 一组「实体 → 载体」的搬运关系。{@code convert} 吃 {@code Object} 是为了让参数化流统一。 */
    private record Pair(Class<?> entityType, Class<?> voType, Function<Object, Object> convert) {
    }

    static Stream<Arguments> pairs() {
        return Stream.of(
                new Pair(AppEntity.class, AppConfigVo.class, e -> CONVERTER.toAppVo((AppEntity) e)),
                new Pair(RoutingConsumerEntity.class, RoutingConsumerConfigVo.class,
                        e -> CONVERTER.toConsumerVo((RoutingConsumerEntity) e)),
                new Pair(RoutingRuleEntity.class, RoutingRuleConfigVo.class,
                        e -> CONVERTER.toRuleVo((RoutingRuleEntity) e)),
                new Pair(RoutingRuleTargetEntity.class, RoutingRuleTargetConfigVo.class,
                        e -> CONVERTER.toTargetVo((RoutingRuleTargetEntity) e)),
                new Pair(ModelEntity.class, ModelConfigVo.class, e -> CONVERTER.toModelVo((ModelEntity) e)),
                new Pair(ModelPoolEntity.class, ModelPoolConfigVo.class, e -> CONVERTER.toModelPoolVo((ModelPoolEntity) e)),
                new Pair(ModelPoolItemEntity.class, ModelPoolItemConfigVo.class,
                        e -> CONVERTER.toModelPoolItemVo((ModelPoolItemEntity) e)),
                new Pair(ProviderAccountEntity.class, ProviderAccountConfigVo.class,
                        e -> CONVERTER.toProviderAccountVo((ProviderAccountEntity) e)),
                new Pair(ProviderCredentialEntity.class, ProviderCredentialConfigVo.class,
                        e -> CONVERTER.toCredentialVo((ProviderCredentialEntity) e)),
                new Pair(ModelApiBindingEntity.class, ModelApiBindingConfigVo.class,
                        e -> CONVERTER.toApiBindingVo((ModelApiBindingEntity) e)),
                new Pair(BillingRuleEntity.class, BillingRuleConfigVo.class,
                        e -> CONVERTER.toBillingRuleVo((BillingRuleEntity) e))
        ).map(pair -> Arguments.of(pair.voType().getSimpleName(), pair));
    }

    /**
     * 字段集对账：实体有的字段，载体一个都不能少、也不能多。
     *
     * <p>这条抓的是<b>载体侧的漂移</b>：有人给 {@code ModelEntity} 加了个
     * {@code supportVision} 而忘了同步 {@link ModelConfigVo}，编译不会报错、
     * {@code converterCopiesEveryField} 也不会红（载体根本没有这个组件可比），
     * 只有这条会响。</p>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("pairs")
    @DisplayName("载体的字段集与实体逐字相同（少一个下游就会回退 import 实体，多一个没人填）")
    void voMirrorsEntityFieldSet(String name, Pair pair) {
        Set<String> entityFields = declaredFieldNames(pair.entityType());
        Set<String> voComponents = recordComponentNames(pair.voType());

        assertEquals(entityFields, voComponents,
                name + " 的字段集与 " + pair.entityType().getSimpleName() + " 不一致。"
                        + "少字段 = 下游为了它回退去 import dal.entity（棘轮白清）；多字段 = 载体里有个永远为 null 的坑。");
        assertFalse(voComponents.isEmpty(), "没读到任何 record 组件，反射口径失效");
    }

    /**
     * 内容测试：把实体每个字段填成唯一哨兵值，投影后逐组件比对。
     *
     * <p>抓的是<b>Converter 侧的漏映射/错位</b>——包括「把 A 字段映射给了 B 字段」这种
     * 字段集对账完全看不出、而值恰好都非 null 的错误。</p>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("pairs")
    @DisplayName("Converter 逐字段搬运：不漏、不串、不塞默认值")
    void converterCopiesEveryField(String name, Pair pair) throws Exception {
        Object entity = filledEntity(pair.entityType());

        // 先把「入参真的填上了」钉住，否则下面的镜像断言会退化成 assertEquals(null, null)
        for (Field field : instanceFields(pair.entityType())) {
            assertNotNull(field.get(entity), "实体填充没生效：" + name + "." + field.getName()
                    + "——这一条红了，后面的逐字段断言全部恒真");
        }

        Object vo = pair.convert().apply(entity);

        for (RecordComponent component : pair.voType().getRecordComponents()) {
            Field entityField = pair.entityType().getDeclaredField(component.getName());
            entityField.setAccessible(true);
            Object expected = entityField.get(entity);
            Object actual = component.getAccessor().invoke(vo);

            assertNotNull(actual, name + "." + component.getName() + " 投影后为 null——Converter 漏了这个字段。"
                    + "MapStruct 只在编译期报 WARN，运行期就是「读出来恒为 null」。");
            assertEquals(expected, actual, name + "." + component.getName() + " 投影值不对（串位或类型不符）");
        }
    }

    /**
     * 哨兵互不相同 —— 这条是上面那条「串位检测」的<b>有效性前提</b>。
     *
     * <p>如果两个字段的哨兵值相同，把它们接反了也不会有任何断言变红。
     * 所以哨兵值重复不是「测试写得糙」，而是「这条守卫已经失效」。</p>
     *
     * <p>⚠ <b>口径是「同一组内部互不相同」，不是全局唯一</b>：11 个实体本来就有大量同名字段
     * （{@code id} / {@code name} / {@code status} / {@code remark}），它们分属不同的 Converter 方法，
     * 接反了会落到不同的断言上，不存在串位风险。按全局去重会永久变红，那等于把这条守卫废掉。</p>
     */
    @Test
    @DisplayName("同一组内哨兵值两两不同：否则字段串位检测不出来")
    void sentinelValuesAreAllDistinctWithinEachPair() {
        List<String> collisions = new ArrayList<>();

        for (Arguments arguments : pairs().toList()) {
            Pair pair = (Pair) arguments.get()[1];
            // HashMap 而不是 TreeMap：哨兵值跨 Long/String 两种类型，TreeMap 会在比较时 ClassCast
            Map<Object, String> firstOwner = new HashMap<>();
            for (Field field : instanceFields(pair.entityType())) {
                Object value = sentinel(field.getName(), field.getType());
                String owner = pair.voType().getSimpleName() + "." + field.getName();
                String existing = firstOwner.putIfAbsent(value, owner);
                if (existing != null) {
                    collisions.add(existing + " 与 " + owner + " 得到同一个哨兵值 " + value);
                }
            }
        }
        assertTrue(collisions.isEmpty(),
                "以下哨兵值重复，涉及字段接反也不会变红，请换哨兵策略：\n  " + String.join("\n  ", collisions));
    }

    // ------------------------------------------------------------------
    // 显式哨兵：这几条是「当初必须新建载体、不能复用 admin 的 Item」的理由本身
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ModelApiBindingConfigVo.status 是整型状态码，不是 boolean enabled")
    void bindingStatusIsIntegerCodeNotBoolean() {
        ModelApiBindingEntity entity = new ModelApiBindingEntity();
        entity.setStatus(0);

        // 状态 0 是合法值（EnabledStatusEnum.DISABLED）。如果哪天有人把载体改成 boolean，
        // 这里会编译失败而不是把「停用」读成 false/enabled 的语义漂移。
        ModelApiBindingConfigVo vo = CONVERTER.toApiBindingVo(entity);
        assertEquals(0, vo.status(),
                "网关判定用的是整型状态码；0（停用）是合法值。改成 boolean 会让「停用」与「未配置」不可区分");
    }

    @Test
    @DisplayName("ProviderCredentialConfigVo 带出 encryptedConfig 与 authApplier（敏感，但必须带）")
    void credentialCarriesEncryptedConfigForInProcessUse() {
        ProviderCredentialEntity entity = new ProviderCredentialEntity();
        entity.setEncryptedConfig("enc-blob");
        entity.setAuthApplier("BEARER");

        ProviderCredentialConfigVo vo = CONVERTER.toCredentialVo(entity);

        assertEquals("enc-blob", vo.encryptedConfig(),
                "网关要在进程内解出鉴权参数，密文必须带过来；安全边界靠「不对外暴露」，不靠「载体里没有这个字段」");
        assertEquals("BEARER", vo.authApplier());
    }

    @Test
    @DisplayName("列表投影逐个元素搬运，且保持顺序")
    void listProjectionsKeepOrderAndContent() {
        List<RoutingRuleTargetEntity> targets = List.of(target("A"), target("B"));
        List<ModelPoolItemEntity> items = List.of(item("m1"), item("m2"));
        List<ModelApiBindingEntity> bindings = List.of(binding("P1"), binding("P2"));

        assertEquals(List.of("A", "B"),
                CONVERTER.toTargetVoList(targets).stream().map(RoutingRuleTargetConfigVo::targetCode).toList());
        assertEquals(List.of("m1", "m2"),
                CONVERTER.toModelPoolItemVoList(items).stream().map(ModelPoolItemConfigVo::modelCode).toList());
        assertEquals(List.of("P1", "P2"),
                CONVERTER.toApiBindingVoList(bindings).stream().map(ModelApiBindingConfigVo::protocol).toList());
    }

    // ------------------------------------------------------------------

    private static RoutingRuleTargetEntity target(String code) {
        RoutingRuleTargetEntity entity = new RoutingRuleTargetEntity();
        entity.setTargetCode(code);
        return entity;
    }

    private static ModelPoolItemEntity item(String code) {
        ModelPoolItemEntity entity = new ModelPoolItemEntity();
        entity.setModelCode(code);
        return entity;
    }

    private static ModelApiBindingEntity binding(String protocol) {
        ModelApiBindingEntity entity = new ModelApiBindingEntity();
        entity.setProtocol(protocol);
        return entity;
    }

    /** 按类型给唯一哨兵值；遇到没见过的类型直接炸，不返回 null（返回 null 会让镜像断言恒真） */
    private static Object sentinel(String fieldName, Class<?> type) {
        if (type == String.class) {
            return fieldName + "@v";
        }
        // 数字哨兵取名字哈希的低位：131 个字段名之间碰撞概率极低，且碰撞会被
        // sentinelValuesAreAllDistinct 抓住（那正是它的职责）
        if (type == Long.class) {
            return (long) (Math.abs(fieldName.hashCode()) + 1);
        }
        if (type == Integer.class) {
            return Math.abs(fieldName.hashCode()) % 100_000 + 1;
        }
        if (type == Boolean.class) {
            // 同一组内只有 0~1 个 Boolean，用名字奇偶区分即可；若将来同组出现两个 Boolean
            // 且奇偶相同，sentinelValuesAreAllDistinctWithinEachPair 会红——那时才需要更细的策略
            return fieldName.hashCode() % 2 == 0;
        }
        if (type == LocalDateTime.class) {
            // ⚠ 不能统一给 NOW：RoutingRuleConfigVo 同时有 createdAt / updatedAt，
            // 都用同一个时间戳的话，把它俩接反了不会有任何断言变红
            return NOW.plusSeconds(Math.abs(fieldName.hashCode()) % 10_000);
        }
        throw new IllegalArgumentException("字段 " + fieldName + " 的类型 " + type.getName()
                + " 没有哨兵策略。新增字段类型时请在这里补一条——不要用 null 兜底，那会让逐字段断言恒真");
    }

    private static Object filledEntity(Class<?> type) throws Exception {
        Object entity = type.getDeclaredConstructor().newInstance();
        for (Field field : instanceFields(type)) {
            field.set(entity, sentinel(field.getName(), field.getType()));
        }
        return entity;
    }

    private static List<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        return fields;
    }

    private static Set<String> declaredFieldNames(Class<?> type) {
        return instanceFields(type).stream().map(Field::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> recordComponentNames(Class<?> type) {
        return Stream.of(type.getRecordComponents()).map(RecordComponent::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 兜底：载体必须是 record，否则 {@code getRecordComponents()} 返回 null，上面几条会静默空跑 */
    @Test
    @DisplayName("11 个载体都是 record（否则反射口径整体失效）")
    void allVosAreRecords() {
        Set<String> notRecords = new HashSet<>();
        for (Arguments arguments : pairs().toList()) {
            Pair pair = (Pair) arguments.get()[1];
            if (!pair.voType().isRecord()) {
                notRecords.add(pair.voType().getName());
            }
        }
        assertTrue(notRecords.isEmpty(), "以下载体不是 record，逐字段反射断言会静默空跑：" + notRecords);
    }
}
