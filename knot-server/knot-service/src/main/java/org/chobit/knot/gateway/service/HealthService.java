package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.mapper.PingMapper;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final PingMapper pingMapper;

    /**
     * Constructs a new instance.
     */
    public HealthService(PingMapper pingMapper) {
        this.pingMapper = pingMapper;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String dbTimestamp() {
        return pingMapper.selectNow();
    }
}
