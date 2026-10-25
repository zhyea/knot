package org.chobit.knot.gateway.controller;

import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.model.vo.request.ImageGenerationRequest;
import org.chobit.knot.gateway.model.vo.request.ImageVariationRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/openai/v1/images")
public class GatewayImageController extends GatewayControllerSupport {

    public GatewayImageController(GatewayRequestHandler requestHandler) {
        super(requestHandler);
    }

    /**
     * Handles image generation requests.
     */
    @PostMapping("/generations")
    public Mono<ResponseEntity<Object>> generations(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody ImageGenerationRequest requestBody,
        ServerWebExchange exchange) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.IMAGE_GENERATIONS, exchange);
    }

    /**
     * Handles image edit requests.
     *
     * <p>⚠ 2026-10-20（WebFlux 批2）：表单字段必须用 {@code @RequestPart} 而不是 {@code @RequestParam}。
     * Servlet 栈上两者都能从 multipart 表单取值，但 <b>WebFlux 的 {@code @RequestParam} 只绑查询参数</b>
     * （SPR-16190 至今未修）——继续用 {@code @RequestParam} 的话，任何 multipart 图片编辑请求都会在
     * 进入方法体之前就被拦成 400 {@code Required query parameter 'prompt' is not present}
     * （"query parameter" 这个措辞极具误导性，实际是表单字段压根没被解析）。
     * 文件部分与普通字段统一走 {@code @RequestPart}，是 WebFlux 侧对 multipart 的唯一正确姿势。</p>
     *
     * <p>⚠ 2026-10-20（WebFlux 批2，真启 s10 踩出来的第二个坑）：多文件 part <b>不能用数组</b>
     * {@code FilePart[]}。Spring 的 {@code RequestPartMethodArgumentResolver} 只认四种形态——
     * {@code FilePart}（单值）、{@code List<FilePart>}（集合）、{@code Flux<FilePart>} /
     * {@code Mono<FilePart>}（反应式包装）；数组不属于任何一支，会掉进「把单个 part 的内容
     * 当整体 body 解码」的分支，拿 part 自己的 {@code image/png} 去读 {@code FilePart[]}，
     * 直接 415 {@code UnsupportedMediaTypeStatusException}（错误消息里的 bodyType 就是线索）。
     * Servlet 栈的 {@code MultipartFile[]} 由 MVC 专用 resolver 兜着，没有这个限制——
     * <b>切栈时逐参数核对形态，不能照抄签名</b>。</p>
     *
     * <p>⚠ 2026-10-20（WebFlux 批2，真启 s10 踩出来的第三个坑）：<b>数字 / 布尔表单字段也必须绑
     * {@code String}</b>（{@code n} / {@code output_compression} / {@code partial_images} /
     * {@code stream}）。{@code @RequestPart} 的解码走 HttpMessageReader 管线，默认 codec 里只有
     * {@code StringDecoder.allMimeTypes()} 能兜住表单字段那种无 Content-Type（{@code octet-stream}）
     * 的 part，{@code Integer} / {@code Boolean} 没有对应 reader，直接 415
     * {@code UnsupportedMediaTypeStatusException}（错误消息里的 bodyType 就是线索）。
     * Servlet 栈的 {@code @RequestParam Integer} 走 ConversionService，没有这个限制。
     * 绑成 {@code String} 后上送字节与旧实现逐字节一致——multipart 字段在线上本就是文本，
     * 旧代码的 {@code builder.part(key, 2)} 落出来也是 {@code "2"}。</p>
     */
    @PostMapping(value = "/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> edits(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestPart("image") List<FilePart> image,
        @RequestPart(value = "mask", required = false) FilePart mask,
        @RequestPart("prompt") String prompt,
        @RequestPart(value = "background", required = false) String background,
        @RequestPart(value = "input_fidelity", required = false) String inputFidelity,
        @RequestPart(value = "model", required = false) String model,
        @RequestPart(value = "n", required = false) String n,
        @RequestPart(value = "output_compression", required = false) String outputCompression,
        @RequestPart(value = "output_format", required = false) String outputFormat,
        @RequestPart(value = "partial_images", required = false) String partialImages,
        @RequestPart(value = "quality", required = false) String quality,
        @RequestPart(value = "response_format", required = false) String responseFormat,
        @RequestPart(value = "size", required = false) String size,
        @RequestPart(value = "stream", required = false) String stream,
        @RequestPart(value = "user", required = false) String user,
        ServerWebExchange exchange) {

        Map<String, Object> body = imageEditBody(image, mask, prompt,
            background, inputFidelity, model, n, outputCompression, outputFormat, partialImages,
            quality, responseFormat, size, stream, user);

        return handleMultipartRequest(authorization, rule, traceparent, stickyKey, body,
            ModelApiProtocolEnum.IMAGE_EDITS, exchange);
    }

    /**
     * Handles image variation requests.
     */
    @PostMapping("/variations")
    public Mono<ResponseEntity<Object>> variations(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody ImageVariationRequest requestBody,
        ServerWebExchange exchange) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.IMAGE_VARIATIONS, exchange);
    }

    private Map<String, Object> imageEditBody(List<FilePart> image,
                                              FilePart mask,
                                              String prompt,
                                              String background,
                                              String inputFidelity,
                                              String model,
                                              String n,
                                              String outputCompression,
                                              String outputFormat,
                                              String partialImages,
                                              String quality,
                                              String responseFormat,
                                              String size,
                                              String stream,
                                              String user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("image", image);
        putIfPresent(body, "mask", mask);
        body.put("prompt", prompt);
        putIfPresent(body, "background", background);
        putIfPresent(body, "input_fidelity", inputFidelity);
        putIfPresent(body, "model", model);
        putIfPresent(body, "n", n);
        putIfPresent(body, "output_compression", outputCompression);
        putIfPresent(body, "output_format", outputFormat);
        putIfPresent(body, "partial_images", partialImages);
        putIfPresent(body, "quality", quality);
        putIfPresent(body, "response_format", responseFormat);
        putIfPresent(body, "size", size);
        putIfPresent(body, "stream", stream);
        putIfPresent(body, "user", user);
        return body;
    }

    private void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
