package org.chobit.knot.gateway.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docs")
public class ApiDocController {

    @PostMapping("/openapi.json")
    public Map<String, Object> openApi() {
        return Map.of(
                "openapi", "3.0.3",
                "info", Map.of("title", "Knot AI Gateway API", "version", "v1"),
                "paths", Map.of(
                        "/api/health", Map.of("get", Map.of("summary", "health check"))
                )
        );
    }

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
