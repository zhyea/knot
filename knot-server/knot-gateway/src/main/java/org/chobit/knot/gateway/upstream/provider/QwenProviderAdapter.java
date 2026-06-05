package org.chobit.knot.gateway.upstream.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProviderTypeEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.service.ProviderCredentialSupport;
import org.chobit.knot.gateway.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(20)
public class QwenProviderAdapter implements UpstreamProviderAdapter {

    private static final String API_PATH = "/api/v1/services/aigc/multimodal-generation/generation";
    private static final String IMAGE = "image";
    private static final String IMAGE_URL = "image_url";
    private static final String IMAGE_URLS = "image_urls";
    private static final String B64_JSON = "b64_json";
    private static final String URL = "url";

    private final ProviderCredentialSupport credentialSupport;

    public QwenProviderAdapter(ProviderCredentialSupport credentialSupport) {
        this.credentialSupport = credentialSupport;
    }

    /**
     * Returns whether the adapter supports the supplied provider type.
     */
    @Override
    public boolean supports(String providerType) {
        return ProviderTypeEnum.QWEN.code().equals(StringUtils.upperCase(StringUtils.trim(providerType)));
    }

    /**
     * Resolves the upstream DashScope multimodal generation endpoint.
     */
    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && StringUtils.isNotBlank(context.binding().getApiPath())) {
            return StringUtils.trim(context.binding().getApiPath());
        }
        return API_PATH;
    }

    /**
     * Qwen image APIs use JSON even when the gateway accepts OpenAI-compatible multipart edits.
     */
    @Override
    public MediaType resolveContentType(UpstreamRequestContext context) {
        return MediaType.APPLICATION_JSON;
    }

    /**
     * Builds the DashScope Qwen image request body.
     */
    @Override
    public Object buildRequestBody(UpstreamRequestContext context) {
        ModelApiProtocolEnum protocol = context.protocol().canonical();
        if (ModelApiProtocolEnum.IMAGE_GENERATIONS == protocol) {
            return imageGenerationBody(context);
        }
        if (ModelApiProtocolEnum.IMAGE_EDITS == protocol) {
            return imageEditBody(context);
        }
        return context.requestBody();
    }

    /**
     * Applies DashScope authentication headers.
     */
    @Override
    public void applyHeaders(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context) {
        String apiKey = credentialValue(context.credential(), AuthConstants.API_KEY);
        if (StringUtils.isNotBlank(apiKey)) {
            requestSpec.header(GatewayHeaders.AUTHORIZATION, AuthConstants.BEARER_PREFIX + apiKey);
        }
    }

    /**
     * Converts DashScope image results to an OpenAI-compatible image response.
     */
    @Override
    public String handleResponse(String responseBody, UpstreamRequestContext context) {
        Map<String, Object> body = JsonKit.fromJson(responseBody, new TypeReference<>() {
        });
        if (body == null || body.isEmpty()) {
            return responseBody;
        }
        List<Map<String, Object>> data = extractImages(body, context.requestBody());
        if (data.isEmpty()) {
            return responseBody;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("created", Instant.now().getEpochSecond());
        normalized.put("data", data);
        Object usage = body.get(AiPayloadFields.USAGE);
        if (usage != null) {
            normalized.put(AiPayloadFields.USAGE, usage);
        }
        return JsonKit.toJson(normalized);
    }

    @Override
    public BillingUsage extractUsageFromBody(Map<String, Object> body) {
        BillingUsage usage = UpstreamProviderAdapter.super.extractUsageFromBody(body);
        if (!usage.isEmpty()) {
            return usage;
        }
        Object data = body.get("data");
        long amount = data instanceof List<?> list ? list.size() : 0L;
        return amount > 0 ? new BillingUsage(0L, 0L, 0L, 0L, 0L, amount) : BillingUsage.empty();
    }

    private Map<String, Object> imageGenerationBody(UpstreamRequestContext context) {
        Map<String, Object> source = context.requestBody();
        String prompt = requiredText(source.get(AiPayloadFields.PROMPT), "prompt is required for Qwen image generation");
        Map<String, Object> body = baseBody(context, List.of(Map.of("text", prompt)));
        body.put("parameters", parameters(source));
        return body;
    }

    private Map<String, Object> imageEditBody(UpstreamRequestContext context) {
        Map<String, Object> source = context.requestBody();
        String prompt = requiredText(source.get(AiPayloadFields.PROMPT), "prompt is required for Qwen image edit");
        List<Map<String, Object>> content = new ArrayList<>();
        for (String image : imageInputs(source)) {
            content.add(Map.of(IMAGE, image));
        }
        content.add(Map.of("text", prompt));
        Map<String, Object> body = baseBody(context, content);
        body.put("parameters", parameters(source));
        return body;
    }

    private Map<String, Object> baseBody(UpstreamRequestContext context, List<Map<String, Object>> content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(AiPayloadFields.MODEL, context.model().getModelCode());
        body.put("input", Map.of(
                AiPayloadFields.MESSAGES,
                List.of(Map.of(AiPayloadFields.ROLE, AiPayloadFields.USER, AiPayloadFields.CONTENT, content))
        ));
        return body;
    }

    private Map<String, Object> parameters(Map<String, Object> source) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        moveIfPresent(source, parameters, "size", "size");
        moveIfPresent(source, parameters, "n", "n");
        moveIfPresent(source, parameters, "quality", "quality");
        moveIfPresent(source, parameters, "style", "style");
        moveIfPresent(source, parameters, "seed", "seed");
        moveIfPresent(source, parameters, "negative_prompt", "negative_prompt");
        moveIfPresent(source, parameters, "watermark", "watermark");
        moveIfPresent(source, parameters, "prompt_extend", "prompt_extend");
        moveIfPresent(source, parameters, "input_fidelity", "input_fidelity");
        return parameters;
    }

    private void moveIfPresent(Map<String, Object> source, Map<String, Object> target, String from, String to) {
        Object value = source.get(from);
        if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
            target.put(to, value);
        }
    }

    private List<String> imageInputs(Map<String, Object> source) {
        List<String> result = new ArrayList<>();
        addImageValues(result, source.get(IMAGE));
        addImageValues(result, source.get(IMAGE_URL));
        addImageValues(result, source.get(IMAGE_URLS));
        if (result.isEmpty()) {
            throw new GatewayUpstreamException("image is required for Qwen image edit", ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        }
        if (result.size() > 3) {
            throw new GatewayUpstreamException("Qwen image edit supports at most 3 images", ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        }
        return result;
    }

    private void addImageValues(List<String> target, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof MultipartFile file) {
            target.add(toDataUrl(file));
            return;
        }
        if (value instanceof MultipartFile[] files) {
            for (MultipartFile file : files) {
                addImageValues(target, file);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> addImageValues(target, item));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Object url = map.get(URL);
            if (url == null) {
                url = map.get(IMAGE_URL);
            }
            addImageValues(target, url);
            return;
        }
        String text = StringUtils.trimToNull(String.valueOf(value));
        if (text != null) {
            target.add(text);
        }
    }

    private String toDataUrl(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GatewayUpstreamException("image file must not be empty", ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        }
        try {
            String contentType = StringUtils.defaultIfBlank(file.getContentType(), MediaType.APPLICATION_OCTET_STREAM_VALUE);
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new GatewayUpstreamException("read image file failed: " + e.getMessage(), ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        }
    }

    private List<Map<String, Object>> extractImages(Map<String, Object> body, Map<String, Object> requestBody) {
        List<Map<String, Object>> data = new ArrayList<>();
        Object output = body.get("output");
        if (output instanceof Map<?, ?> outputMap) {
            addOutputImages(data, outputMap);
        }
        addRawImages(data, body.get("results"));
        addRawImages(data, body.get("data"));
        return data.stream()
                .map(item -> normalizeImageItem(item, requestBody))
                .filter(item -> !item.isEmpty())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private void addOutputImages(List<Map<String, Object>> target, Map<?, ?> output) {
        Object choices = output.get("choices");
        if (!(choices instanceof List<?> list)) {
            return;
        }
        for (Object choice : list) {
            if (!(choice instanceof Map<?, ?> choiceMap)) {
                continue;
            }
            Object message = choiceMap.get("message");
            if (!(message instanceof Map<?, ?> messageMap)) {
                continue;
            }
            Object content = messageMap.get(AiPayloadFields.CONTENT);
            if (content instanceof List<?> contentList) {
                for (Object item : contentList) {
                    if (item instanceof Map<?, ?> map) {
                        target.add((Map<String, Object>) map);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addRawImages(List<Map<String, Object>> target, Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    target.add((Map<String, Object>) map);
                }
            }
        }
    }

    private Map<String, Object> normalizeImageItem(Map<String, Object> item, Map<String, Object> requestBody) {
        Object image = firstValue(item, IMAGE, URL, "image_url", "url", B64_JSON, "b64_image");
        if (image == null) {
            return Map.of();
        }
        String value = String.valueOf(image);
        if (isBase64Response(requestBody) || value.startsWith("data:")) {
            return Map.of(B64_JSON, stripDataUrlPrefix(value));
        }
        return Map.of(URL, value);
    }

    private boolean isBase64Response(Map<String, Object> requestBody) {
        Object responseFormat = requestBody.get("response_format");
        return B64_JSON.equals(StringUtils.trimToEmpty(responseFormat == null ? null : String.valueOf(responseFormat)));
    }

    private Object firstValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return value;
            }
        }
        return null;
    }

    private String stripDataUrlPrefix(String value) {
        int index = value.indexOf(',');
        return value.startsWith("data:") && index >= 0 ? value.substring(index + 1) : value;
    }

    private String requiredText(Object value, String message) {
        String text = StringUtils.trimToNull(value == null ? null : String.valueOf(value));
        if (text == null) {
            throw new GatewayUpstreamException(message, ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        }
        return text;
    }

    private String credentialValue(ProviderCredentialEntity credential, String key) {
        Object value = credentialSupport.toAuthConfig(credential).get(key);
        return StringUtils.trimToNull(value == null ? null : String.valueOf(value));
    }
}
