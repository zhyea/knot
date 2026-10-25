package org.chobit.knot.svc.service.support;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 路由规则调试的纯函数集合：协议准入白名单、网关路径、请求体补全、curl 文本、multipart 装配。
 *
 * <p>全部为静态无状态方法——本类不持有 mapper、不发起 HTTP，因此可被能力下发接口与真实调试
 * 调用同时复用，杜绝「面板展示的路径」与「实际请求的路径」两份实现漂移。</p>
 */
public final class RoutingRuleDebugSupport {

    /** 管理端调试链路固定的 traceparent：便于在网关日志中一次性捞全一条测试链路 */
    public static final String TRACEPARENT = "00-00000000000000000000000000000001-0000000000000001-01";

    public static final String DEFAULT_TEST_PROMPT = "你好，这是一条路由规则测试消息";

    /** 调试面板允许发起的协议集合；不在其中的协议由写入口直接拒绝 */
    public static final Set<ModelApiProtocolEnum> DEBUGGABLE_PROTOCOLS = Set.of(
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES,
            ModelApiProtocolEnum.COMPLETIONS,
            ModelApiProtocolEnum.EMBEDDINGS,
            ModelApiProtocolEnum.IMAGE_GENERATIONS,
            ModelApiProtocolEnum.IMAGE_EDITS,
            ModelApiProtocolEnum.IMAGE_VARIATIONS,
            ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS,
            ModelApiProtocolEnum.AUDIO_TRANSLATIONS,
            ModelApiProtocolEnum.AUDIO_SPEECH,
            ModelApiProtocolEnum.VIDEO_GENERATIONS,
            ModelApiProtocolEnum.RERANK,
            ModelApiProtocolEnum.MODERATIONS,
            ModelApiProtocolEnum.DECISIONS
    );

    /**
     * 调试面板的协议说明文案。与能力接口一并下发，前端不再自带 PROTOCOL_HINTS。
     */
    private static final Map<ModelApiProtocolEnum, String> PROTOCOL_DEBUG_HINTS = Map.ofEntries(
            Map.entry(ModelApiProtocolEnum.CHAT_COMPLETIONS, "适用于标准对话请求，通常需要 messages。"),
            Map.entry(ModelApiProtocolEnum.RESPONSES, "适用于 OpenAI Responses 协议，通常使用 input。"),
            Map.entry(ModelApiProtocolEnum.MESSAGES, "适用于 Anthropic Messages 协议，通常需要 messages 和 max_tokens。"),
            Map.entry(ModelApiProtocolEnum.COMPLETIONS, "适用于传统文本补全协议，通常使用 prompt。"),
            Map.entry(ModelApiProtocolEnum.EMBEDDINGS, "适用于向量化请求，通常使用 input。"),
            Map.entry(ModelApiProtocolEnum.IMAGE_GENERATIONS, "适用于文生图，通常使用 prompt。"),
            Map.entry(ModelApiProtocolEnum.IMAGE_EDITS, "适用于图像编辑，通常需要 prompt 和 image。image 可先用 URL、data URL 或占位值维护模板。"),
            Map.entry(ModelApiProtocolEnum.IMAGE_VARIATIONS, "适用于图像变体生成，通常需要 image。"),
            Map.entry(ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS, "适用于语音转录，通常需要 file。"),
            Map.entry(ModelApiProtocolEnum.AUDIO_TRANSLATIONS, "适用于语音翻译，通常需要 file。"),
            Map.entry(ModelApiProtocolEnum.AUDIO_SPEECH, "适用于语音合成，通常使用 input 和 voice。"),
            Map.entry(ModelApiProtocolEnum.VIDEO_GENERATIONS, "适用于视频生成，通常使用 prompt。"),
            Map.entry(ModelApiProtocolEnum.RERANK, "适用于重排序，通常使用 query 和 documents。"),
            Map.entry(ModelApiProtocolEnum.MODERATIONS, "适用于内容安全审核，通常使用 input。"),
            Map.entry(ModelApiProtocolEnum.DECISIONS, "适用于决策评估请求，通常使用 state 和 questions。")
    );

    /** multipart 协议里按文件处理、其余字段按普通表单项处理的字段名 */
    private static final Set<String> MULTIPART_FILE_FIELDS = Set.of("image", "mask");

    private RoutingRuleDebugSupport() {
    }

    /**
     * 协议说明文案；无文案返回 {@code null}。
     */
    public static String hintOf(ModelApiProtocolEnum protocol) {
        return PROTOCOL_DEBUG_HINTS.get(protocol);
    }

    /**
     * 管理端到网关的调试路径。
     */
    public static String buildGatewayTestPath(ModelApiProtocolEnum protocol) {
        return switch (protocol) {
            case CHAT_COMPLETIONS -> "/openai/v1/chat/completions";
            case RESPONSES -> "/openai/v1/responses";
            case MESSAGES -> "/anthropic/v1/messages";
            case COMPLETIONS -> "/openai/v1/completions";
            case EMBEDDINGS -> "/openai/v1/embeddings";
            case IMAGE_GENERATIONS -> "/openai/v1/images/generations";
            case IMAGE_EDITS -> "/openai/v1/images/edits";
            case IMAGE_VARIATIONS -> "/openai/v1/images/variations";
            case AUDIO_TRANSCRIPTIONS -> "/openai/v1/audio/transcriptions";
            case AUDIO_TRANSLATIONS -> "/openai/v1/audio/translations";
            case AUDIO_SPEECH -> "/openai/v1/audio/speech";
            case VIDEO_GENERATIONS -> "/openai/v1/videos/generations";
            case RERANK -> "/v1/rerank";
            case MODERATIONS -> "/openai/v1/moderations";
            case DECISIONS -> "/v1/decisions";
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前调试暂不支持该接口协议");
        };
    }

    /**
     * 请求体中承载 prompt 的字段路径；无 prompt 语义的协议（语音转录、图像变体）返回 null。
     * 与 {@link #fillDefaultPromptFields} 的取值口径保持一致。
     */
    public static String promptFieldOf(ModelApiProtocolEnum protocol) {
        return switch (protocol) {
            case CHAT_COMPLETIONS, MESSAGES -> "messages[0].content";
            case RESPONSES, EMBEDDINGS, AUDIO_SPEECH, MODERATIONS -> "input";
            case COMPLETIONS, IMAGE_GENERATIONS, IMAGE_EDITS, VIDEO_GENERATIONS -> "prompt";
            case RERANK -> "query";
            default -> null;
        };
    }

    public static String normalizeTestPrompt(String prompt) {
        String normalized = prompt == null ? "" : prompt.trim();
        return normalized.isEmpty() ? DEFAULT_TEST_PROMPT : normalized;
    }

    /**
     * 组装调试请求体：保留调用方提交的字段、剔除 {@code model}（由网关按路由目标覆盖），再补协议缺省 prompt 字段。
     */
    public static Map<String, Object> buildRequestBody(ModelApiProtocolEnum protocol,
                                                       String prompt,
                                                       Map<String, Object> requestBody) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (requestBody != null && !requestBody.isEmpty()) {
            body.putAll(requestBody);
        }
        body.remove("model");
        fillDefaultPromptFields(protocol, body, prompt);
        return body;
    }

    private static void fillDefaultPromptFields(ModelApiProtocolEnum protocol, Map<String, Object> body, String prompt) {
        switch (protocol) {
            case CHAT_COMPLETIONS, MESSAGES -> {
                if (!body.containsKey("messages")) {
                    body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
                }
            }
            case RESPONSES, EMBEDDINGS, AUDIO_SPEECH, MODERATIONS -> body.putIfAbsent("input", prompt);
            case COMPLETIONS, IMAGE_GENERATIONS, IMAGE_EDITS, VIDEO_GENERATIONS -> body.putIfAbsent("prompt", prompt);
            case RERANK -> {
                body.putIfAbsent("query", prompt);
                body.putIfAbsent("documents", List.of("文档 1", "文档 2"));
                body.putIfAbsent("top_n", 2);
            }
            default -> {
            }
        }
    }

    /**
     * 生成可复制的 curl 文本：图像编辑走 multipart，其余走 JSON。
     */
    public static String buildTestCurl(String baseUrl,
                                       String gatewayPath,
                                       String secretKey,
                                       String ruleCode,
                                       ModelApiProtocolEnum protocol,
                                       Map<String, Object> body) {
        if (ModelApiProtocolEnum.IMAGE_EDITS == protocol) {
            return buildMultipartCurl(baseUrl, gatewayPath, secretKey, ruleCode, body);
        }
        String json = JsonKit.toJson(body);
        String escapedJson = json == null ? "{}" : json.replace("'", "'\\''");
        return "curl -X POST '" + baseUrl + gatewayPath + "' \\\n"
                + "  -H 'Authorization: Bearer " + secretKey + "' \\\n"
                + "  -H 'Rule: " + ruleCode + "' \\\n"
                + "  -H 'traceparent: " + TRACEPARENT + "' \\\n"
                + "  -H 'Content-Type: application/json' \\\n"
                + "  -d '" + escapedJson + "'";
    }

    private static String buildMultipartCurl(String baseUrl,
                                             String gatewayPath,
                                             String secretKey,
                                             String ruleCode,
                                             Map<String, Object> body) {
        StringBuilder curl = new StringBuilder()
                .append("curl -X POST '").append(baseUrl).append(gatewayPath).append("' \\\n")
                .append("  -H 'Authorization: Bearer ").append(secretKey).append("' \\\n")
                .append("  -H 'Rule: ").append(ruleCode).append("' \\\n")
                .append("  -H 'traceparent: ").append(TRACEPARENT).append("' ");
        appendMultipartFields(curl, "model", body.get("model"));
        appendMultipartFields(curl, "prompt", body.get("prompt"));
        appendMultipartFields(curl, "image", body.get("image"));
        appendMultipartFields(curl, "mask", body.get("mask"));
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            if (Set.of("model", "prompt", "image", "mask").contains(key)) {
                continue;
            }
            appendMultipartFields(curl, key, entry.getValue());
        }
        return curl.toString().trim();
    }

    private static void appendMultipartFields(StringBuilder curl, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                appendMultipartFields(curl, key, item);
            }
            return;
        }
        String text = String.valueOf(value);
        if (MULTIPART_FILE_FIELDS.contains(key) && new File(text).exists()) {
            curl.append("\\\n  -F '").append(key).append("=@").append(text.replace("\\", "/")).append("' ");
            return;
        }
        curl.append("\\\n  -F '").append(key).append("=").append(text.replace("'", "'\\''")).append("' ");
    }

    /**
     * multipart 请求体装配：{@code image} / {@code mask} 转文件资源，其余按原值提交。
     */
    public static MultiValueMap<String, Object> toMultipartBody(Map<String, Object> body) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            addMultipartPart(parts, entry.getKey(), entry.getValue());
        }
        return parts;
    }

    private static void addMultipartPart(MultiValueMap<String, Object> parts, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addMultipartPart(parts, key, item);
            }
            return;
        }
        if (MULTIPART_FILE_FIELDS.contains(key)) {
            parts.add(key, toFileResource(value, key));
            return;
        }
        parts.add(key, value);
    }

    private static FileSystemResource toFileResource(Object value, String fieldName) {
        File file = new File(String.valueOf(value));
        if (!file.exists() || !file.isFile()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 需要提供可访问的本地文件路径");
        }
        return new FileSystemResource(file);
    }
}
