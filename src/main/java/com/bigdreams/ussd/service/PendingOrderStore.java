package com.bigdreams.ussd.service;

import com.bigdreams.ussd.model.PendingOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds orders that have a Paystack charge in flight, keyed by the Paystack
 * transaction reference, until the webhook confirms or fails them.
 *
 * Same single-instance caveat as UssdSessionService: for multi-instance
 * deployments, back this with a shared store (Redis/DB) instead.
 */
@Service
public class PendingOrderStore {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderStore.class);
    private static final long STALE_AFTER_MS = 30 * 60_000L; // 30 minutes - MoMo prompts don't stay valid much longer

    private final Map<String, PendingOrder> orders = new ConcurrentHashMap<>();

    public void put(PendingOrder order) {
        orders.put(order.reference(), order);
    }

    public PendingOrder get(String reference) {
        return orders.get(reference);
    }

    public void remove(String reference) {
        orders.remove(reference);
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupStale() {
        long cutoff = System.currentTimeMillis() - STALE_AFTER_MS;
        int before = orders.size();
        orders.entrySet().removeIf(e -> e.getValue().createdAt() < cutoff);
        int removed = before - orders.size();
        if (removed > 0) {
            log.debug("Dropped {} stale pending order(s) that never got a payment webhook", removed);
        }
    }
}
