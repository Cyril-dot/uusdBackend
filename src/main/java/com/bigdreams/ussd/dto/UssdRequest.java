package com.bigdreams.ussd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload sent by Arkesel to the USSD endpoint on every step of a session.
 * See: Arkesel USSD API Reference -> "Receiving Requests".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UssdRequest(
        String sessionID,
        String userID,
        Boolean newSession,
        String msisdn,
        String userData,
        String network
) {
    public boolean isNewSession() {
        return Boolean.TRUE.equals(newSession);
    }

    public String userDataTrimmed() {
        return userData == null ? "" : userData.trim();
    }
}
