package org.chobit.knot.admin.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docs")
public class ApiDocController {

    /**
     * 返回 OpenAPI 静态骨架（不是运行时扫描生成的）。
     *
     * <p>⚠ 方法名必须跟真实端点一致：{@code /api/health} 在 {@link HealthController} 里是
     * {@code @PostMapping}，这里写 {@code get} 会让按文档接入的调用方直接 405。</p>
     */
    @PostMapping("/openapi.json")
    public Map<String, Object> openApi() {
        return Map.of(
                "openapi", "3.0.3",
                "info", Map.of("title", "Knot AI Gateway API", "version", "v1"),
                "paths", Map.of(
                        "/api/health", Map.of("post", Map.of("summary", "health check"))
                )
        );
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/changelog")
    public List<DocChangeLog> changelog() {
        return List.of(
                new DocChangeLog("v1.0.0", "initial gateway scaffold"),
                new DocChangeLog("v1.1.0", "add billing and routing management APIs")
        );
    }

    public record DocChangeLog(String version, String change) {
    }
}
