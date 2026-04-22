package com.knot.gateway.common.model;

public class QuotaPolicy {
    private long dailyLimit;
    private long monthlyLimit;
    private long tokenLimit;
    private boolean alertEnabled;

    public QuotaPolicy() {
    }

    public QuotaPolicy(long dailyLimit, long monthlyLimit, long tokenLimit, boolean alertEnabled) {
        this.dailyLimit = dailyLimit;
        this.monthlyLimit = monthlyLimit;
        this.tokenLimit = tokenLimit;
        this.alertEnabled = alertEnabled;
    }

    public long getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(long dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public long getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(long monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public long getTokenLimit() {
        return tokenLimit;
    }

    public void setTokenLimit(long tokenLimit) {
        this.tokenLimit = tokenLimit;
    }

    public boolean isAlertEnabled() {
        return alertEnabled;
    }

    public void setAlertEnabled(boolean alertEnabled) {
        this.alertEnabled = alertEnabled;
    }
}
