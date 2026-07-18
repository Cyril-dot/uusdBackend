package com.bigdreams.ussd.dto;

/**
 * Response shape expected by Arkesel's USSD gateway.
 * continueSession = true keeps the session open and waits for the next digit(s);
 * false terminates the session and displays the final message on the handset.
 */
public record UssdResponse(
        String sessionID,
        String userID,
        String msisdn,
        String message,
        boolean continueSession
) {
    public static UssdResponse continueWith(UssdRequest req, String message) {
        return new UssdResponse(req.sessionID(), req.userID(), req.msisdn(), message, true);
    }

    public static UssdResponse end(UssdRequest req, String message) {
        return new UssdResponse(req.sessionID(), req.userID(), req.msisdn(), message, false);
    }
}
