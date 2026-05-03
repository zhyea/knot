package com.knot.gateway.common.model;

public class RateLimitPolicy {
    private int perSecond;
    private int perMinute;
    private String timeWindow;

    public RateLimitPolicy() {
    }

    public RateLimitPolicy(int perSecond, int perMinute, String timeWindow) {
        this.perSecond = perSecond;
        this.perMinute = perMinute;
        this.timeWindow = timeWindow;
    }

    public int getPerSecond() {
        return perSecond;
    }

    public void setPerSecond(int perSecond) {
        this.perSecond = perSecond;
    }

    public int getPerMinute() {
        return perMinute;
    }

    public void setPerMinute(int perMinute) {
        this.perMinute = perMinute;
    }

    public String getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(String timeWindow) {
        this.timeWindow = timeWindow;
    }
}
