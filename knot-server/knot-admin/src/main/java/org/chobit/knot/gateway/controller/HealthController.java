package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.model.HealthResponse;
import org.chobit.knot.gateway.service.HealthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @PostMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", LocalDateTime.now(), healthService.dbTimestamp());
    }
}
