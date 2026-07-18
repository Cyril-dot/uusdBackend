package com.bigdreams.ussd.model;

import java.util.Map;

public record OrderResult(
        boolean success,
        String message,
        String error,
        Map<String, Object> data
) {
    public String reference() {
        if (data == null) return null;
        Object ref = data.getOrDefault("order_id", data.get("reference"));
        return ref == null ? null : ref.toString();
    }
}
