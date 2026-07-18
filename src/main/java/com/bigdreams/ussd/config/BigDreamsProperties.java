package com.bigdreams.ussd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bigdreams.api")
public class BigDreamsProperties {

    /**
     * Base URL for the Big Dreams Data developer API.
     */
    private String baseUrl;

    /**
     * x-api-key used to authenticate against the Big Dreams Data API.
     */
    private String apiKey;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
