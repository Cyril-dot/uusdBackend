package com.bigdreams.ussd.controller;

import com.bigdreams.ussd.config.PaystackProperties;
import com.bigdreams.ussd.model.OrderResult;
import com.bigdreams.ussd.model.PendingOrder;
import com.bigdreams.ussd.service.ArkeselSmsService;
import com.bigdreams.ussd.service.BigDreamsDataService;
import com.bigdreams.ussd.service.PendingOrderStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Register this URL as your webhook in the Paystack dashboard
 * (Settings -> API Keys & Webhooks -> Webhook URL), e.g.
 * https://your-domain.com/paystack/webhook
 *
 * Paystack signs every webhook body with HMAC-SHA512 using your secret key,
 * sent in the X-Paystack-Signature header - we verify that before trusting
 * anything in the payload.
 */
@RestController
public class PaystackWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaystackWebhookController.class);

    private final PaystackProperties paystackProperties;
    private final PendingOrderStore pendingOrderStore;
    private final BigDreamsDataService dataService;
    private final ArkeselSmsService smsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaystackWebhookController(PaystackProperties paystackProperties,
                                      PendingOrderStore pendingOrderStore,
                                      BigDreamsDataService dataService,
                                      ArkeselSmsService smsService) {
        this.paystackProperties = paystackProperties;
        this.pendingOrderStore = pendingOrderStore;
        this.dataService = dataService;
        this.smsService = smsService;
    }

    @PostMapping(value = "/paystack/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handle(@RequestBody String rawBody,
                                        @RequestHeader(value = "X-Paystack-Signature", required = false) String signature) {

        if (!isValidSignature(rawBody, signature)) {
            log.warn("Rejected Paystack webhook: missing or invalid signature");
            return ResponseEntity.status(401).build();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            log.warn("Could not parse Paystack webhook body", ex);
            return ResponseEntity.badRequest().build();
        }

        String event = root.path("event").asText();
        String reference = root.path("data").path("reference").asText(null);

        if (reference == null) {
            // Not a charge event we care about (e.g. transfer events) - acknowledge and ignore.
            return ResponseEntity.ok().build();
        }

        PendingOrder pending = pendingOrderStore.get(reference);
        if (pending == null) {
            log.info("Received {} for unknown/already-handled reference {}", event, reference);
            return ResponseEntity.ok().build();
        }

        switch (event) {
            case "charge.success" -> onPaymentSucceeded(reference, pending);
            case "charge.failed" -> onPaymentFailed(reference, pending);
            default -> log.debug("Ignoring Paystack event {} for reference {}", event, reference);
        }

        // Always 200 quickly so Paystack doesn't retry-storm us; failures are handled internally above.
        return ResponseEntity.ok().build();
    }

    private void onPaymentSucceeded(String reference, PendingOrder pending) {
        // Money has landed in the merchant's Paystack account. Now spend from the
        // OWNER's own (self-funded) Big Dreams Data wallet to actually deliver the bundle.
        OrderResult result = dataService.placeOrder(
                pending.network(),
                pending.recipient(),
                pending.bundleSizeGb(),
                reference
        );
        pendingOrderStore.remove(reference);

        String message = result.success()
                ? "Payment received! Your " + pending.bundleLabel() + " bundle is being sent to "
                    + pending.recipient() + ". Thank you!"
                : "We received your payment but could not complete the bundle order ("
                    + (result.message() != null ? result.message() : "please contact support")
                    + "). Our team has been notified.";

        if (!result.success()) {
            log.error("Payment succeeded (ref={}) but bundle order FAILED - needs manual follow-up / refund. error={} message={}",
                    reference, result.error(), result.message());
        }

        smsService.send(pending.payerMsisdn(), message);
    }

    private void onPaymentFailed(String reference, PendingOrder pending) {
        pendingOrderStore.remove(reference);
        smsService.send(pending.payerMsisdn(),
                "Your payment for a " + pending.bundleLabel() + " data bundle could not be completed. Please try again.");
    }

    private boolean isValidSignature(String rawBody, String signature) {
        if (signature == null || paystackProperties.getSecretKey() == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(paystackProperties.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return computed.equalsIgnoreCase(signature);
        } catch (Exception ex) {
            log.error("Failed to compute Paystack webhook signature", ex);
            return false;
        }
    }
}
