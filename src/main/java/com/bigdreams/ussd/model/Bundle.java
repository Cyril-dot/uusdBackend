package com.bigdreams.ussd.model;

public record Bundle(
        String id,
        String network,
        String size,
        double sizeGb,
        double price,
        String validity
) {
    public String menuLabel() {
        return size + " - GHS" + String.format("%.2f", price);
    }
}
