package com.bigdreams.ussd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arkesel.sms")
public class ArkeselSmsProperties {

    /**
     * Arkesel SMS v2 API key (header "api-key"). If left blank, confirmation
     * SMS sending is silently skipped - useful if you only want the USSD +
     * payment flow without SMS receipts.
     */
    private String apiKey;

    /**
     * Sender ID shown to the customer, max 11 characters.
     */
    private String senderId = "DataBundle";

    private String baseUrl = "https://sms.arkesel.com/api/v2";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
