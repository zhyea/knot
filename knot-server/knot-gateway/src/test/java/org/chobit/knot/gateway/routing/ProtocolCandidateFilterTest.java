package org.chobit.knot.gateway.routing;

import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 协议候选过滤：口径只认 kb_model_api_bindings 的显式声明（启用 + 协议归一后匹配），
 * 无绑定即视为不支持任何协议。
 */
class ProtocolCandidateFilterTest {

    private static final String MODEL = "MODEL";

    /** 模型 A：CHAT_COMPLETIONS 与 MESSAGES 双绑定；模型 B：仅 CHAT_COMPLETIONS；模型 C：无绑定 */
    private static final long A = 1L;
    private static final long B = 2L;
    private static final long C = 3L;

    private final Map<Long, List<ModelApiBindingEntity>> bindings = Map.of(
            A, List.of(binding("CHAT_COMPLETIONS", 1), binding("MESSAGES", 1)),
            B, List.of(binding("CHAT_COMPLETIONS", 1))
    );

    private final ProtocolCandidateFilter filter = new ProtocolCandidateFilter(modelId ->
            bindings.getOrDefault(modelId, List.of()));

    @Test
    void keepsOnlyCandidatesDeclaringTheProtocol() {
        List<RoutingRuleTargetDto> candidates = List.of(candidate("a", A), candidate("b", B));

        List<String> kept = filter.filter(candidates, ModelApiProtocolEnum.MESSAGES).stream()
                .map(RoutingRuleTargetDto::targetCode)
                .toList();

        assertEquals(List.of("a"), kept, "MESSAGES 只有 A 声明过，B 应被剔除");
    }

    @Test
    void candidateWithoutAnyBindingIsNotSupported() {
        List<RoutingRuleTargetDto> candidates = List.of(candidate("a", A), candidate("c", C));

        List<String> kept = filter.filter(candidates, ModelApiProtocolEnum.CHAT_COMPLETIONS).stream()
                .map(RoutingRuleTargetDto::targetCode)
                .toList();

        assertEquals(List.of("a"), kept, "无绑定的模型不支持任何协议，配置缺失显式暴露而非推测可用");
    }

    @Test
    void disabledBindingDoesNotCount() {
        List<ModelApiBindingEntity> withDisabled = new ArrayList<>();
        withDisabled.add(binding("CHAT_COMPLETIONS", 1));
        withDisabled.add(binding("EMBEDDINGS", 0));
        ProtocolCandidateFilter disabledAware = new ProtocolCandidateFilter(modelId ->
                modelId == A ? withDisabled : List.of());

        List<String> kept = disabledAware.filter(List.of(candidate("a", A)), ModelApiProtocolEnum.EMBEDDINGS).stream()
                .map(RoutingRuleTargetDto::targetCode)
                .toList();

        assertTrue(kept.isEmpty(), "停用绑定不构成协议声明");
    }

    @Test
    void nullProtocolSkipsFiltering() {
        List<RoutingRuleTargetDto> candidates = List.of(candidate("a", A), candidate("b", B), candidate("c", C));

        List<String> kept = filter.filter(candidates, null).stream()
                .map(RoutingRuleTargetDto::targetCode)
                .toList();

        assertEquals(List.of("a", "b", "c"), kept, "未识别协议不过滤，保持既有候选链");
    }

    @Test
    void nullTargetIdCannotMatch() {
        RoutingRuleTargetDto noId = new RoutingRuleTargetDto(MODEL, null, "x", "upstream", "名称", "CHAT", null, 1, true, null);

        assertTrue(filter.filter(List.of(noId), ModelApiProtocolEnum.CHAT_COMPLETIONS).isEmpty(),
                "派生 id 缺失（目标被删/JOIN 未命中）时不放行");
    }

    private static RoutingRuleTargetDto candidate(String code, Long modelId) {
        return new RoutingRuleTargetDto(MODEL, modelId, code, "upstream-" + code,
                "名称-" + code, "CHAT", null, 1, true, null);
    }

    private static ModelApiBindingEntity binding(String protocol, int status) {
        ModelApiBindingEntity entity = new ModelApiBindingEntity();
        entity.setProtocol(protocol);
        entity.setStatus(status);
        return entity;
    }
}
