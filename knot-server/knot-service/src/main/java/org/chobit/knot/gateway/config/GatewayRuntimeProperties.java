package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knot.gateway")
public class GatewayRuntimeProperties {

    /**
     * 缃戝叧杩愯鏃?Base URL锛岀敤浜庣鐞嗙璺敱瑙勫垯娴嬭瘯杞彂
     */
    private String baseUrl = "http://127.0.0.1:9090";

    /**
     * Returns the requested value. Executes the public operation.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
