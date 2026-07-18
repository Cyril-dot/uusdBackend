package com.bigdreams.ussd.service;

import com.bigdreams.ussd.config.BigDreamsProperties;
import com.bigdreams.ussd.model.Bundle;
import com.bigdreams.ussd.model.OrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the Big Dreams Data "developer-api" endpoint.
 * Every action (place_order, get_bundles, check_balance, ...) is a POST
 * to the same base URL with an "action" field in the JSON body and the
 * x-api-key header for auth.
 */
@Service
public class BigDreamsDataService {

    private static final Logger log = LoggerFactory.getLogger(BigDreamsDataService.class);

    private final RestTemplate restTemplate;
    private final BigDreamsProperties properties;

    public BigDreamsDataService(RestTemplate restTemplate, BigDreamsProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", properties.getApiKey());
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(Map<String, Object> body) {
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers());
            ResponseEntity<Map> response = restTemplate.postForEntity(properties.getBaseUrl(), entity, Map.class);
            return response.getBody();
        } catch (RestClientException ex) {
            log.error("Big Dreams Data API call failed for action={}", body.get("action"), ex);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "network_error");
            error.put("message", "Could not reach the data bundle provider. Please try again shortly.");
            return error;
        }
    }

    /**
     * Fetches available bundles for a network, sorted ascending by price.
     */
    @SuppressWarnings("unchecked")
    public List<Bundle> getBundles(String network) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", "get_bundles");
        body.put("network", network);

        Map<String, Object> resp = call(body);
        List<Bundle> bundles = new ArrayList<>();

        if (resp == null || !Boolean.TRUE.equals(resp.get("success"))) {
            return bundles;
        }

        Object dataObj = resp.get("data");
        if (!(dataObj instanceof Map)) {
            return bundles;
        }
        Map<String, Object> data = (Map<String, Object>) dataObj;
        Object listObj = data.get("bundles");
        if (!(listObj instanceof List)) {
            return bundles;
        }

        for (Object item : (List<Object>) listObj) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> b = (Map<String, Object>) item;
            try {
                bundles.add(new Bundle(
                        String.valueOf(b.get("id")),
                        String.valueOf(b.get("network")),
                        String.valueOf(b.get("size")),
                        toDouble(b.get("size_gb")),
                        toDouble(b.get("price")),
                        String.valueOf(b.get("validity"))
                ));
            } catch (Exception ex) {
                log.warn("Skipping malformed bundle entry: {}", b, ex);
            }
        }

        bundles.sort((a, c) -> Double.compare(a.price(), c.price()));
        return bundles;
    }

    /**
     * Places a data bundle order for a recipient. orderId is used for idempotency
     * (Big Dreams Data returns the original order instead of double-charging on retry).
     */
    @SuppressWarnings("unchecked")
    public OrderResult placeOrder(String network, String recipient, double packageSizeGb, String orderId) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", "place_order");
        body.put("network", network);
        body.put("recipient", recipient);
        body.put("package_size", packageSizeGb);
        if (orderId != null && !orderId.isBlank()) {
            body.put("order_id", orderId);
        }

        Map<String, Object> resp = call(body);
        boolean success = resp != null && Boolean.TRUE.equals(resp.get("success"));
        String message = resp != null && resp.get("message") != null ? resp.get("message").toString() : null;
        String error = resp != null && resp.get("error") != null ? resp.get("error").toString() : null;
        Map<String, Object> data = null;
        if (resp != null && resp.get("data") instanceof Map) {
            data = (Map<String, Object>) resp.get("data");
        }

        return new OrderResult(success, message, error, data);
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }
}
