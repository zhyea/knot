package org.chobit.knot.gateway.rw;

import org.chobit.knot.gateway.ApiResponse;
import org.chobit.knot.gateway.util.JsonKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

/**
 * 参考 zhy-spring-boot-starter 的 {@code ResponseWrapperAdvice}：将业务返回值统一包装为 {@link ApiResponse}。
 */
@RestControllerAdvice
public class ApiResponseWrapperAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(ApiResponseWrapperAdvice.class);

    private final RwProperties rwProperties;

    public ApiResponseWrapperAdvice(RwProperties rwProperties) {
        this.rwProperties = rwProperties;
        log.debug("ApiResponseWrapperAdvice enabled, silentMode={}", rwProperties.isSilentMode());
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!rwProperties.isEnabled()) {
            return false;
        }
        if (returnType.getParameterType() == void.class) {
            return false;
        }
        if (ApiResponse.class.isAssignableFrom(returnType.getParameterType())) {
            return false;
        }
        if (org.springframework.http.ResponseEntity.class.isAssignableFrom(returnType.getParameterType())) {
            return false;
        }
        boolean isApiController = AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), RestController.class)
                || returnType.hasMethodAnnotation(ResponseBody.class);

        boolean effectiveWrapper = AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), ResponseWrapper.class)
                || returnType.hasMethodAnnotation(ResponseWrapper.class);
        effectiveWrapper = rwProperties.isSilentMode() || effectiveWrapper;

        return isApiController && effectiveWrapper;
    }

    @Override
    public Object beforeBodyWrite(
            @Nullable Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof ApiResponse) {
            return body;
        }
        String tagStr = resolveTags(returnType);
        ApiResponse<Object> wrapped = ApiResponse.ok(body, rwProperties.getSuccessCode(), tagStr);
        if (selectedConverterType.equals(StringHttpMessageConverter.class)) {
            String json = JsonKit.toJson(wrapped);
            if (json == null) {
                log.warn("Failed to serialize ApiResponse as JSON string");
                return wrapped;
            }
            return json;
        }
        return wrapped;
    }

    private String resolveTags(MethodParameter returnType) {
        Tags methodTags = returnType.getMethodAnnotation(Tags.class);
        if (methodTags != null && methodTags.value().length > 0) {
            return String.join(",", methodTags.value());
        }
        List<String> global = rwProperties.getTags();
        if (global != null && !global.isEmpty()) {
            return String.join(",", global);
        }
        return null;
    }
}
