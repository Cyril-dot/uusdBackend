package com.bigdreams.ussd.service;

import com.bigdreams.ussd.model.Bundle;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ============================================================================
 *  ADMIN PRICING CATALOG - edit CATALOG below to control what customers pay.
 * ============================================================================
 *
 * This is intentionally hardcoded, NOT fetched from Big Dreams Data. Your
 * profit on every order is:
 *
 *      profit = (price you set here, paid via Paystack)
 *             - (Big Dreams Data's actual cost for that same bundle,
 *                deducted from your wallet when the order is placed)
 *
 * IMPORTANT - two things you must get right when editing this list:
 *
 *   1. `sizeGb` MUST exactly match a bundle size Big Dreams Data actually
 *      sells for that network. If it doesn't, the order will fail at
 *      purchase time (after the customer has already paid!) with a
 *      "Bundle not found" error. Check what's really available first:
 *
 *          curl -X POST https://qrzjkrkawcdoaggblvjc.supabase.co/functions/v1/developer-api \
 *            -H "Content-Type: application/json" \
 *            -H "x-api-key: bh_live_your_api_key_here" \
 *            -d '{"action":"get_bundles","network":"mtn"}'
 *
 *      That response's "price" field is what Big Dreams Data charges YOUR
 *      wallet - use it to figure out your markup, it is NOT what the
 *      customer pays (that's the `price` field you set below).
 *
 *   2. Keep `id` unique per entry - it's only used internally for logging,
 *      it isn't sent to Big Dreams Data (they're looked up by network+size).
 *
 * Changing prices here takes effect on the next USSD menu render - no
 * Big Dreams Data account changes needed, since this catalog is entirely
 * separate from whatever pricing (custom or default) is set on your Big
 * Dreams Data account.
 */
@Service
public class BundleCatalogService {

    private static final List<Bundle> CATALOG = List.of(
            // ---- MTN ----
            new Bundle("mtn-1gb", "mtn", "1GB", 1, 6.00, "30 Days"),
            new Bundle("mtn-5gb", "mtn", "5GB", 5, 27.00, "30 Days"),
            new Bundle("mtn-10gb", "mtn", "10GB", 10, 48.00, "30 Days"),

            // ---- Telecel ----
            new Bundle("telecel-2gb", "telecel", "2GB", 2, 11.00, "30 Days"),
            new Bundle("telecel-6gb", "telecel", "6GB", 6, 29.00, "30 Days"),

            // ---- AirtelTigo (iShare) ----
            new Bundle("ishare-1gb", "ishare", "1GB", 1, 6.50, "30 Days")
    );

    /**
     * Returns the sell-priced catalog for a network, in the order defined
     * above (define them cheapest-first if you want that menu ordering).
     */
    public List<Bundle> getBundles(String network) {
        return CATALOG.stream()
                .filter(b -> b.network().equals(network))
                .toList();
    }
}
