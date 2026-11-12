package org.chobit.knot.svc.service;

import org.chobit.knot.dal.mapper.PingMapper;
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

    public String dbTimestamp() {
        return pingMapper.selectNow();
    }
}
