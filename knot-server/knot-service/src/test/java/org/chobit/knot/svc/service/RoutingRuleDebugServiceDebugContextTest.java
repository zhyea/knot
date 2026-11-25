package org.chobit.knot.svc.service;

import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.ModelPoolEntity;
import org.chobit.knot.dal.entity.ModelPoolItemEntity;
import org.chobit.knot.dal.mapper.ModelApiBindingMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.dal.mapper.ModelPoolMapper;
import org.chobit.knot.svc.dto.routing.RoutingRuleDto;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.svc.dto.routing.RoutingTestPresetDto;
import org.chobit.knot.svc.vo.routing.RoutingDebugTargetItem;
import org.chobit.knot.svc.vo.routing.RoutingRuleDebugContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 调试上下文的协议 / 预设归属：一次调用取齐「逐目标协议 + 模型族 + 已筛预设」，且不落分页。
 *
 * <p>为什么必须钉住：抽屉切目标时协议与预设必须同步收窄。
 * 曾经的口径是「协议按目标取、预设按单一模型族取」，两处判据不同源，于是会出现
 * 「协议下拉允许发、预设却全是别的族的请求体」；模型池更是把族取自池归属的统一模型，
 * 而不是池内实际模型。这里的断言全部落在<b>目标实际关联的模型</b>这条判据上。</p>
 *
 * <p>具体类（{@link RoutingRuleService} / {@link RoutingTestPresetService}）在 JDK 25 下不能 mock，
 * 用子类覆写被调用的只读方法当替身；只有 Mapper 接口用 Mockito。</p>
 */
class RoutingRuleDebugServiceDebugContextTest {

    private static final Long RULE_ID = 3L;

    private final ModelMapper modelMapper = mock(ModelMapper.class);
    private final ModelPoolMapper poolMapper = mock(ModelPoolMapper.class);
    private final ModelApiBindingMapper bindingMapper = mock(ModelApiBindingMapper.class);
    private final StubRoutingRuleService ruleService = new StubRoutingRuleService();
    private final StubPresetService presetService = new StubPresetService();

    private RoutingRuleDebugService service() {
        return new RoutingRuleDebugService(ruleService, null, bindingMapper, modelMapper, poolMapper,
            presetService, null);
    }

    @Test
    void directModelKeepsOnlySameFamilyPresets() {
        modelMapper("m-openai", 11L, "openai", "CHAT_COMPLETIONS");
        ruleService.targets = List.of(modelTarget("m-openai", 11L));
        presetService.presets = List.of(
            preset(1L, "同族", "CHAT_COMPLETIONS", "openai"),
            preset(2L, "异族", "CHAT_COMPLETIONS", "anthropic"),
            preset(3L, "通用", "CHAT_COMPLETIONS", null),
            preset(4L, "别的协议", "RESPONSES", null)
        );

        RoutingRuleDebugContext context = service().listDebugContext(RULE_ID);

        RoutingDebugTargetItem target = onlyTarget(context);
        assertEquals(List.of("CHAT_COMPLETIONS"), target.protocols());
        assertEquals(List.of("openai"), target.modelFamilies());
        // 同族 + 通用；异族与「本目标不支持的协议」都不进
        assertEquals(List.of(1L, 3L), presetIds(target));
    }

    @Test
    void modelWithoutFamilyFallsBackToProtocolFiltering() {
        modelMapper("m-any", 12L, null, "CHAT_COMPLETIONS");
        ruleService.targets = List.of(modelTarget("m-any", 12L));
        presetService.presets = List.of(
            preset(1L, "族A", "CHAT_COMPLETIONS", "openai"),
            preset(2L, "族B", "CHAT_COMPLETIONS", "anthropic"),
            preset(3L, "通用", "CHAT_COMPLETIONS", null),
            preset(4L, "别的协议", "VIDEO_GENERATIONS", null)
        );

        RoutingRuleDebugContext context = service().listDebugContext(RULE_ID);

        RoutingDebugTargetItem target = onlyTarget(context);
        assertTrue(target.modelFamilies().isEmpty());
        // 目标一个族都没绑 → 只按协议筛，族维度完全放开
        assertEquals(List.of(1L, 2L, 3L), presetIds(target));
    }

    @Test
    void modelPoolTakesUnionOfPoolModels() {
        ModelPoolEntity pool = pool(21L, "pool-a");
        when(poolMapper.getById(21L)).thenReturn(pool);
        when(poolMapper.listItemsByPoolCode("pool-a")).thenReturn(List.of(
            poolItem("m-chat", 1),
            poolItem("m-response", 1),
            poolItem("m-disabled", 0)
        ));
        modelMapper("m-chat", 31L, "openai", "CHAT_COMPLETIONS");
        modelMapper("m-response", 32L, "anthropic", "RESPONSES");
        // 停用条目的模型不该参与并集：给它绑定一个别族协议做探针
        modelMapper("m-disabled", 33L, "google", "VIDEO_GENERATIONS");
        ruleService.targets = List.of(poolTarget("pool-a", 21L));
        presetService.presets = List.of(
            preset(1L, "chat-openai", "CHAT_COMPLETIONS", "openai"),
            preset(2L, "responses-anthropic", "RESPONSES", "anthropic"),
            preset(3L, "chat-google", "CHAT_COMPLETIONS", "google"),
            preset(4L, "video-google", "VIDEO_GENERATIONS", "google"),
            preset(5L, "通用", "RESPONSES", null)
        );

        RoutingRuleDebugContext context = service().listDebugContext(RULE_ID);

        RoutingDebugTargetItem target = onlyTarget(context);
        assertEquals(List.of("CHAT_COMPLETIONS", "RESPONSES"), target.protocols());
        assertEquals(List.of("openai", "anthropic"), target.modelFamilies());
        // 池内模型的族并集 + 通用；停用条目带来的 google / VIDEO_GENERATIONS 一律不出现
        assertEquals(List.of(1L, 2L, 5L), presetIds(target));
    }

    @Test
    void allTargetsUnionCoversProtocolsAndPresetsOfEveryTarget() {
        modelMapper("m-chat", 11L, "openai", "CHAT_COMPLETIONS");
        ModelPoolEntity pool = pool(21L, "pool-a");
        when(poolMapper.getById(21L)).thenReturn(pool);
        when(poolMapper.listItemsByPoolCode("pool-a")).thenReturn(List.of(poolItem("m-video", 1)));
        modelMapper("m-video", 41L, "kling", "VIDEO_GENERATIONS");
        ruleService.targets = List.of(modelTarget("m-chat", 11L), poolTarget("pool-a", 21L));
        presetService.presets = List.of(
            preset(1L, "chat", "CHAT_COMPLETIONS", "openai"),
            preset(2L, "video", "VIDEO_GENERATIONS", "kling"),
            preset(3L, "chat-异族", "CHAT_COMPLETIONS", "anthropic")
        );

        RoutingRuleDebugContext context = service().listDebugContext(RULE_ID);

        assertEquals(2, context.targets().size());
        // 「全部目标」= 两个目标的并集：协议按目标顺序累积，预设按族与协议双重收窄
        assertEquals(List.of("CHAT_COMPLETIONS", "VIDEO_GENERATIONS"), context.protocols());
        assertEquals(List.of(1L, 2L), context.presets().stream().map(p -> p.id()).toList());
    }

    @Test
    void missingPoolYieldsEmptyCapabilityInsteadOfGuessing() {
        when(poolMapper.getById(99L)).thenReturn(null);
        ruleService.targets = List.of(poolTarget("pool-gone", 99L));
        presetService.presets = List.of(preset(1L, "chat", "CHAT_COMPLETIONS", null));

        RoutingRuleDebugContext context = service().listDebugContext(RULE_ID);

        RoutingDebugTargetItem target = onlyTarget(context);
        assertTrue(target.protocols().isEmpty());
        assertTrue(target.presets().isEmpty());
        // 无协议 = 无可调试入口：并集也必须为空，不能凭模型类型兜底造出一个能选却发不出去的协议
        assertTrue(context.protocols().isEmpty());
        assertTrue(context.presets().isEmpty());
    }

    // ===================== 夹具 =====================

    private static RoutingDebugTargetItem onlyTarget(RoutingRuleDebugContext context) {
        assertEquals(1, context.targets().size());
        return context.targets().get(0);
    }

    private static List<Long> presetIds(RoutingDebugTargetItem target) {
        return target.presets().stream().map(p -> p.id()).toList();
    }

    private void modelMapper(String code, Long id, String family, String protocol) {
        ModelEntity model = new ModelEntity();
        model.setId(id);
        model.setModelCode(code);
        model.setModelFamilyCode(family);
        model.setStatus(1);
        when(modelMapper.getByCode(code)).thenReturn(model);
        when(bindingMapper.listByModelId(id)).thenReturn(List.of(binding(protocol)));
    }

    private static ModelApiBindingEntity binding(String protocol) {
        ModelApiBindingEntity entity = new ModelApiBindingEntity();
        entity.setProtocol(protocol);
        entity.setStatus(1);
        return entity;
    }

    private static ModelPoolEntity pool(Long id, String poolCode) {
        ModelPoolEntity entity = new ModelPoolEntity();
        entity.setId(id);
        entity.setPoolCode(poolCode);
        entity.setStatus(1);
        return entity;
    }

    private static ModelPoolItemEntity poolItem(String modelCode, int status) {
        ModelPoolItemEntity entity = new ModelPoolItemEntity();
        entity.setModelCode(modelCode);
        entity.setStatus(status);
        return entity;
    }

    private static RoutingRuleTargetDto modelTarget(String modelCode, Long modelId) {
        return new RoutingRuleTargetDto("MODEL", modelId, modelCode, modelCode, modelCode,
            null, null, 100, true, null);
    }

    private static RoutingRuleTargetDto poolTarget(String poolCode, Long poolId) {
        return new RoutingRuleTargetDto("MODEL_POOL", poolId, poolCode, null, poolCode,
            null, null, 100, true, poolCode);
    }

    private static RoutingTestPresetDto preset(Long id, String name, String protocol, String family) {
        return new RoutingTestPresetDto(id, "p" + id, name, protocol, family, null, "{}", null, 1);
    }

    /** 只提供 {@code getById}：本测试关心的是目标解析与预设筛选，不碰规则的读写路径。 */
    private static final class StubRoutingRuleService extends RoutingRuleService {

        private List<RoutingRuleTargetDto> targets = List.of();

        StubRoutingRuleService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public RoutingRuleDto getById(Long id) {
            return new RoutingRuleDto(id, "r-1", "规则", null, null, null, null, null, null, null,
                null, null, true, targets, null, List.of(), null, null, "PRIORITY", null, null, 0L);
        }
    }

    /** 只提供启用预设列表：用例筛选口径全部在调试服务里，预设服务不该有第二份实现。 */
    private static final class StubPresetService extends RoutingTestPresetService {

        private List<RoutingTestPresetDto> presets = List.of();

        StubPresetService() {
            super(null, null, null);
        }

        @Override
        public List<RoutingTestPresetDto> listActiveOptions() {
            return presets;
        }
    }
}
