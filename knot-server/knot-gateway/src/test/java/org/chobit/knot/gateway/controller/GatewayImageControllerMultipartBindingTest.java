package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多模态 multipart 入站契约。
 *
 * <p><b>为什么值得单独钉</b>：2026-10-20 批2 之前，本端点的表单字段用的是 {@code @RequestParam}
 * ——Servlet 栈上它照样能从 multipart 表单取值，一切正常。切到 WebFlux 后这是<b>静默失效</b>的：
 * WebFlux 的 {@code @RequestParam} <b>只绑查询参数</b>（SPR-16190 至今未修），
 * 任何 multipart 图片编辑请求都会在进入方法体之前被拦成 400
 * {@code Required query parameter 'prompt' is not present}
 * （"query parameter" 这个措辞极具误导性，实际是表单字段压根没被解析）。
 * 单测同样看不出来——只有真启 multipart 请求才暴露。</p>
 *
 * <p>换 {@code @RequestPart} 之后又连着踩了两个同族坑（都是真启 s10 才暴露，单测同样哑口）：
 * 数组形态 {@code FilePart[]} 与标量类型 {@code Integer} / {@code Boolean}，
 * 都会 415 {@code UnsupportedMediaTypeStatusException}。三个坑的共同点：
 * <b>Servlet 栈的参数绑定语义（ConversionService + MVC 专用 resolver）在 WebFlux 侧
 * 全部不存在</b>，逐参数核对形态、不能照抄签名。</p>
 *
 * <p>这里用四层守卫把它钉死：</p>
 * <ol>
 *   <li>{@link #multipartEndpointsMustNotBindFormFieldsWithRequestParam()}：反射扫描所有控制器，
 *       multipart 端点只要出现 {@code @RequestParam} 就直接失败（附 WebFlux 规则说明）；</li>
 *   <li>{@link #multipartEndpointsMustNotUseArrayRequestParts()}：数组形态的 {@code @RequestPart}
 *       直接失败——Spring 的解析器只认 {@code FilePart} / {@code List<FilePart>} /
 *       {@code Flux<FilePart>} / {@code Mono<FilePart>，数组会掉进「单 part 当整体 body 解码」
 *       分支，415 收场（批2 真启 s10 实测）；</li>
 *   <li>{@link #multipartEndpointsMustNotUseScalarRequestParts()}：标量类型的 {@code @RequestPart}
 *       直接失败——只有 {@code String} 有 reader，{@code Integer} / {@code Boolean} 等
 *       415 收场（批2 真启 s10 实测）；</li>
 *   <li>{@link #editsAssemblesBodyFromFilePartsAndFormFields()}：直接调控制器方法，
 *       断言 {@code imageEditBody} 的组装结果（文件部分列表 + 表单字段 + 缺省字段不出现）。</li>
 * </ol>
 *
 * <p>为什么不走 {@code WebTestClient.bindToController}：mock server 的 codec 管线跑不通 multipart
 * （固定回 415、响应体为空），测出的失败与产品无关，只会误导。直接调方法 + 反射契约检查是确定性的，
 * 真启侧的端到端证据由冒烟场景 s10 负责。</p>
 */
class GatewayImageControllerMultipartBindingTest {

    private CapturingImageController controller;

    @BeforeEach
    void setUp() {
        controller = new CapturingImageController();
    }

    @Test
    @DisplayName("注解契约：multipart 端点不得用 @RequestParam 绑表单字段（WebFlux 只认查询参数）")
    void multipartEndpointsMustNotBindFormFieldsWithRequestParam() throws Exception {
        forEachMultipartEndpoint((className, method, parameter) ->
                assertFalse(parameter.isAnnotationPresent(RequestParam.class),
                        () -> ("%s#%s 是 multipart 端点，参数 %s 却用了 @RequestParam。"
                                + "WebFlux 的 @RequestParam 只绑查询参数（SPR-16190），"
                                + "表单字段必须改用 @RequestPart，否则任何 multipart 请求都会被拦成 400。")
                                .formatted(className, method.getName(), parameter.getName())));
    }

    @Test
    @DisplayName("注解契约：multipart 端点不得用数组形态的 @RequestPart（Spring 只认单值/集合/反应式）")
    void multipartEndpointsMustNotUseArrayRequestParts() throws Exception {
        forEachMultipartEndpoint((className, method, parameter) ->
                assertFalse(parameter.getType().isArray() && parameter.isAnnotationPresent(RequestPart.class),
                        () -> ("%s#%s 的 %s 是数组形态的 @RequestPart。"
                                + "Spring 的 RequestPartMethodArgumentResolver 只支持 FilePart（单值）/ "
                                + "List<FilePart>（集合）/ Flux<FilePart> / Mono<FilePart>（反应式包装），"
                                + "数组不属于任何一支，会掉进「把单个 part 的内容当整体 body 解码」的分支，"
                                + "拿 part 自己的 image/png 去读数组类型，真启直接 415 "
                                + "UnsupportedMediaTypeStatusException（批2 s10 实测）。"
                                + "Servlet 栈的 MultipartFile[] 由 MVC 专用 resolver 兜着，没有这个限制。")
                                .formatted(className, method.getName(), parameter.getName())));
    }

    @Test
    @DisplayName("注解契约：multipart 端点不得把 @RequestPart 绑成 Integer/Boolean 等标量（无 reader → 415）")
    void multipartEndpointsMustNotUseScalarRequestParts() throws Exception {
        forEachMultipartEndpoint((className, method, parameter) ->
                assertFalse(parameter.isAnnotationPresent(RequestPart.class)
                                && isScalarWithoutMessageReader(parameter.getType()),
                        () -> ("%s#%s 的 %s 把 @RequestPart 绑成了 %s。"
                                + "@RequestPart 的解码走 HttpMessageReader 管线，默认 codec 里只有 "
                                + "StringDecoder.allMimeTypes() 兜得住表单字段那种无 Content-Type 的 part，"
                                + "Integer/Boolean/Long 等标量没有对应 reader，真启直接 415 "
                                + "UnsupportedMediaTypeStatusException（批2 s10 实测）。"
                                + "Servlet 栈走 ConversionService 没有这个限制；绑 String 即可，"
                                + "multipart 字段在线上本就是文本，上送字节与旧实现一致。")
                                .formatted(className, method.getName(), parameter.getName(), parameter.getType().getSimpleName())));
    }

    /**
     * 表单字段类 part（无 Content-Type）只有 {@code String} 有 reader；
     * 其余标量类型（数字 / 布尔 / 字符）都读不出来。
     */
    private static boolean isScalarWithoutMessageReader(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || type == Boolean.class
                || type == Character.class;
    }

    /**
     * 扫描 {@code org.chobit.knot.admin.controller} 包下所有控制器，对每个
     * {@code consumes=multipart/form-data} 的端点逐参数执行断言。
     */
    private void forEachMultipartEndpoint(MultipartEndpointParameterConsumer consumer) throws Exception {
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(RestController.class));
        Set<String> checked = new java.util.HashSet<>();
        for (var definition : scanner.findCandidateComponents(GatewayImageController.class.getPackageName())) {
            String className = definition.getBeanClassName();
            if (className == null || !checked.add(className)) {
                continue;
            }
            Class<?> type = Class.forName(className);
            // 必须用 findMergedAnnotation：@PostMapping 是 @RequestMapping 的元注解，
            // 直接 getAnnotation(RequestMapping.class) 拿不到（恒为 null → 扫描被整段跳过，
            // 守卫会变成空转——这个坑是靠必错探针复验才发现的）
            RequestMapping classMapping =
                    org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                            type, RequestMapping.class);
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping methodMapping =
                        org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                                method, RequestMapping.class);
                if (methodMapping == null) {
                    continue;
                }
                if (!consumesMultipart(classMapping) && !consumesMultipart(methodMapping)) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    consumer.accept(className, method, parameter);
                }
            }
        }
        assertTrue(checked.size() >= 5, "至少应扫到 5 个控制器，实际=" + checked.size());
    }

    private interface MultipartEndpointParameterConsumer {
        void accept(String className, Method method, Parameter parameter);
    }

    private static boolean consumesMultipart(RequestMapping mapping) {
        if (mapping == null) {
            return false;
        }
        for (String consumes : mapping.consumes()) {
            if (consumes.contains(MediaType.MULTIPART_FORM_DATA_VALUE)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("body 组装：FilePart 列表 + 表单字段进入 body，未传的可选字段不出现")
    void editsAssemblesBodyFromFilePartsAndFormFields() {
        FilePart image = stubFilePart("image", "pixel.png");
        ResponseEntity<Object> response = controller.edits(
                "Bearer sk-test", "rule-code",
                null, null, List.of(image), null,
                "make it red", null, null, null, "2", null, null, null, null, null,
                "512x512", "false", null, null)
                .block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> body = controller.capturedBody;
        assertNotNull(body, "控制器应把入参交给 handleMultipartRequest");

        Object images = body.get("image");
        // imageEditBody 原样放 List<FilePart>，由下游 AbstractUpstreamProtocolExecutor
        // 拆成 MultipartBodyBuilder 的多个 part（数组形态 FilePart[] 不被 Spring 支持，见类 javadoc）
        assertInstanceOf(List.class, images);
        List<?> parts = (List<?>) images;
        assertEquals(1, parts.size());
        assertEquals("pixel.png", ((FilePart) parts.get(0)).filename());

        assertEquals("make it red", body.get("prompt"));
        assertEquals("512x512", body.get("size"));
        // 数字 / 布尔表单字段绑 String（@RequestPart 没有 Integer/Boolean reader，
        // 见 GatewayImageController#edits javadoc），上送字节与 Servlet 时代一致
        assertEquals("2", body.get("n"));
        assertEquals("false", body.get("stream"));
        // 未上传的可选字段不应塞进 body
        assertFalse(body.containsKey("mask"));
        assertFalse(body.containsKey("model"));
        assertFalse(body.containsKey("user"));
    }

    private static FilePart stubFilePart(String name, String filename) {
        return new FilePart() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String filename() {
                return filename;
            }

            @Override
            public HttpHeaders headers() {
                return new HttpHeaders();
            }

            @Override
            public Flux<DataBuffer> content() {
                return Flux.empty();
            }

            @Override
            public Mono<Void> transferTo(java.nio.file.Path path) {
                return Mono.empty();
            }
        };
    }

    /**
     * 只覆写分发出口，把控制器组装好的入参原样收下来。
     */
    private static final class CapturingImageController extends GatewayImageController {

        private String capturedRule;
        private Map<String, Object> capturedBody;

        CapturingImageController() {
            // 14 个参数全部为 null：本用例只关心入站参数绑定与 body 组装，不会走到 handler
            super(new GatewayRequestHandler(null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null));
        }

        @Override
        protected Mono<ResponseEntity<Object>> handleMultipartRequest(
                String authorization, String rule, String traceparent, String stickyKey,
                Map<String, Object> request,
                org.chobit.knot.common.constants.enums.ModelApiProtocolEnum protocol,
                ServerWebExchange exchange) {
            this.capturedRule = rule;
            this.capturedBody = new LinkedHashMap<>(request);
            return Mono.just(ResponseEntity.ok(Map.of("captured", true, "protocol", protocol.name())));
        }
    }
}
