package com.knot.gateway.service;

import com.knot.gateway.mapper.PingMapper;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final PingMapper pingMapper;

    public HealthService(PingMapper pingMapper) {
        this.pingMapper = pingMapper;
    }

    public String dbTimestamp() {
        return pingMapper.selectNow();
    }
}
