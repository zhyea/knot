package org.chobit.knot.gateway.service.schedule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;

@Component
public class NodeIdentity {

    private final String nodeId;

    public NodeIdentity(@Value("${knot.node-id:}") String configuredNodeId) {
        if (configuredNodeId != null && !configuredNodeId.isBlank()) {
            this.nodeId = configuredNodeId.trim();
        } else {
            this.nodeId = detectNodeId();
        }
    }

    public String nodeId() {
        return nodeId;
    }

    private String detectNodeId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            return host + "-" + pid;
        } catch (Exception e) {
            return "node-" + System.currentTimeMillis();
        }
    }
}
