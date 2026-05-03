package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.mapper.PingMapper;
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
