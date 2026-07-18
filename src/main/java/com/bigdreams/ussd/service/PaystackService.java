package com.bigdreams.ussd.service;

import com.bigdreams.ussd.config.PaystackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Initiates Paystack Mobile Money charges (Ghana). This is the customer-facing
 * payment leg only - it collects money into the merchant's Paystack account.
 * It has nothing to do with, and never touches, the Big Dreams Data wallet.
 */
@Service
public class PaystackService {

    private static final Logger log = LoggerFactory.getLogger(PaystackService.class);

    // Paystack mobile_money.provider codes for Ghana, per their Charge API docs.
    private static final Map<String, String> PROVIDER_BY_NETWORK = Map.of(
            "mtn", "mtn",
            "telecel", "vod",   // Telecel Cash = former Vodafone Cash, Paystack still uses the "vod" code
            "ishare", "atl"     // AirtelTigo Money
    );

    private final RestTemplate restTemplate;
    private final PaystackProperties properties;

    public PaystackService(RestTemplate restTemplate, PaystackProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public record ChargeResult(boolean initiated, String status, String message) {
    }

    /**
     * Kicks off a mobile money charge. Paystack sends an approval prompt (PIN
     * entry) to the payer's phone; the outcome arrives later via the
     * charge.success / charge.failed webhook - this call only tells you the
     * charge was *accepted for processing*, not that money has moved yet.
     */
    @SuppressWarnings("unchecked")
    public ChargeResult chargeMobileMoney(String network, String payerLocalMsisdn, double amountGhs, String reference) {
        String provider = PROVIDER_BY_NETWORK.getOrDefault(network, "mtn");

        Map<String, Object> mobileMoney = new HashMap<>();
        mobileMoney.put("phone", payerLocalMsisdn);
        mobileMoney.put("provider", provider);

        Map<String, Object> body = new HashMap<>();
        // Paystack requires an email on every charge; customers dialing USSD rarely have one on file,
        // so we use a fixed merchant-owned mailbox as the placeholder for all USSD charges.
        body.put("email", "cheapestdata600@gmail.com");
        body.put("amount", Math.round(amountGhs * 100)); // GHS charged in pesewas (subunit)
        body.put("currency", "GHS");
        body.put("reference", reference);
        body.put("mobile_money", mobileMoney);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getSecretKey());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    properties.getBaseUrl() + "/charge",
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<String, Object> respBody = response.getBody();
            boolean ok = respBody != null && Boolean.TRUE.equals(respBody.get("status"));
            String message = respBody != null && respBody.get("message") != null ? respBody.get("message").toString() : null;
            String dataStatus = null;
            if (respBody != null && respBody.get("data") instanceof Map<?, ?> data) {
                Object s = data.get("status");
                dataStatus = s == null ? null : s.toString();
            }
            return new ChargeResult(ok, dataStatus, message);
        } catch (RestClientException ex) {
            log.error("Paystack charge call failed for reference={}", reference, ex);
            return new ChargeResult(false, null, "Could not reach the payment provider. Please try again.");
        }
    }
}