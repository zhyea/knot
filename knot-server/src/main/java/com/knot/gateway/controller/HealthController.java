package com.knot.gateway.controller;

import com.knot.gateway.common.model.HealthResponse;
import com.knot.gateway.mapper.PingMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final PingMapper pingMapper;

    public HealthController(PingMapper pingMapper) {
        this.pingMapper = pingMapper;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", LocalDateTime.now(), pingMapper.selectNow());
    }
}
