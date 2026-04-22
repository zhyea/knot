package com.knot.gateway.model;

import java.time.LocalDateTime;

public class HealthResponse {
    private String status;
    private LocalDateTime serverTime;
    private String dbTime;

    public HealthResponse(String status, LocalDateTime serverTime, String dbTime) {
        this.status = status;
        this.serverTime = serverTime;
        this.dbTime = dbTime;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getServerTime() {
        return serverTime;
    }

    public String getDbTime() {
        return dbTime;
    }
}
