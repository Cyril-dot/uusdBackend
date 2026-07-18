package com.bigdreams.ussd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paystack")
public class PaystackProperties {

    /**
     * Paystack secret key (sk_live_... or sk_test_...). Used both to call the
     * Charge API and to verify webhook signatures.
     */
    private String secretKey;

    private String baseUrl = "https://api.paystack.co";

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
