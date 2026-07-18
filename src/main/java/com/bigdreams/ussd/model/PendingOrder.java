package com.bigdreams.ussd.model;

/**
 * Represents a bundle order that has been quoted and for which a Paystack
 * mobile money charge has been initiated, but not yet confirmed. Kept in
 * memory keyed by the Paystack transaction reference until the webhook
 * resolves it (charge.success / charge.failed) or it goes stale.
 */
public record PendingOrder(
        String reference,
        String payerMsisdn,      // local format, e.g. 0544919953 - who gets charged via MoMo
        String network,          // mtn | telecel | ishare
        String recipient,        // local format - who receives the bundle (may equal payerMsisdn)
        double bundleSizeGb,
        String bundleLabel,      // e.g. "5GB"
        double amount,           // GHS
        long createdAt
) {
    public PendingOrder(String reference, String payerMsisdn, String network, String recipient,
                         double bundleSizeGb, String bundleLabel, double amount) {
        this(reference, payerMsisdn, network, recipient, bundleSizeGb, bundleLabel, amount,
                System.currentTimeMillis());
    }
}
