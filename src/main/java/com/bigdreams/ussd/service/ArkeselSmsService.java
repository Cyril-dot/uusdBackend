package com.bigdreams.ussd.service;

import com.bigdreams.ussd.config.ArkeselSmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends the "your bundle purchase went through" (or failed) SMS once the
 * Paystack webhook resolves an order - the USSD session itself is long gone
 * by the time payment confirmation arrives, so this is the only way the
 * customer finds out what happened.
 *
 * If arkesel.sms.api-key is left blank, sending is skipped silently so the
 * app still works without SMS configured.
 */
@Service
public class ArkeselSmsService {

    private static final Logger log = LoggerFactory.getLogger(ArkeselSmsService.class);

    private final RestTemplate restTemplate;
    private final ArkeselSmsProperties properties;

    public ArkeselSmsService(RestTemplate restTemplate, ArkeselSmsProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public void send(String localMsisdn, String message) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.debug("Arkesel SMS API key not configured; skipping confirmation SMS to {}", localMsisdn);
            return;
        }

        String international = toInternational(localMsisdn);

        Map<String, Object> body = new HashMap<>();
        body.put("sender", properties.getSenderId());
        body.put("message", message);
        body.put("recipients", List.of(international));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", properties.getApiKey());

        try {
            restTemplate.postForEntity(
                    properties.getBaseUrl() + "/sms/send",
                    new HttpEntity<>(body, headers),
                    Map.class
            );
        } catch (RestClientException ex) {
            log.warn("Failed to send confirmation SMS to {}", international, ex);
        }
    }

    private static String toInternational(String localMsisdn) {
        if (localMsisdn != null && localMsisdn.startsWith("0") && localMsisdn.length() == 10) {
            return "233" + localMsisdn.substring(1);
        }
        return localMsisdn;
    }
}
