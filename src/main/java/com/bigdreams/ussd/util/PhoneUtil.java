package com.bigdreams.ussd.util;

public final class PhoneUtil {

    private PhoneUtil() {
    }

    /**
     * Converts a Ghana MSISDN as delivered by Arkesel (e.g. 233544919953)
     * into the local 10-digit format required by the Big Dreams Data API
     * (e.g. 0544919953).
     */
    public static String toLocal(String msisdn) {
        if (msisdn == null) {
            return null;
        }
        String digits = msisdn.replaceAll("[^0-9]", "");
        if (digits.startsWith("233") && digits.length() == 12) {
            return "0" + digits.substring(3);
        }
        if (digits.startsWith("0") && digits.length() == 10) {
            return digits;
        }
        // Fall back to whatever digits we have; downstream validation will reject it if malformed.
        return digits;
    }

    /**
     * Validates the 10-digit local format expected by the Big Dreams Data API: 0XXXXXXXXX.
     */
    public static boolean isValidLocal(String number) {
        return number != null && number.matches("0\\d{9}");
    }
}
