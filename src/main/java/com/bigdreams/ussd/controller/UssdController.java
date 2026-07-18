package com.bigdreams.ussd.controller;

import com.bigdreams.ussd.dto.UssdRequest;
import com.bigdreams.ussd.dto.UssdResponse;
import com.bigdreams.ussd.service.UssdFlowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Register this URL as your "Endpoint URL" in the Arkesel dashboard when you
 * subscribe to / configure your USSD shortcode, e.g.
 * https://your-domain.com/ussd
 *
 * Deliberately does NOT restrict `consumes` to application/json and parses the
 * body manually. Some USSD gateways send a Content-Type header Spring's
 * strict media-type matcher doesn't like (missing header, odd charset suffix,
 * etc.); with `consumes` set, that gets silently rejected as 415 before your
 * code ever sees it - which just looks like "nothing happens" on the phone.
 * Accepting any Content-Type and parsing manually avoids that failure mode.
 */
@RestController
public class UssdController {

    private static final Logger log = LoggerFactory.getLogger(UssdController.class);

    private final UssdFlowService flowService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UssdController(UssdFlowService flowService) {
        this.flowService = flowService;
    }

    @PostMapping(value = "/ussd", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UssdResponse> handle(@RequestBody String rawBody) {
        log.info("USSD raw hit: {}", rawBody);

        UssdRequest request;
        try {
            request = objectMapper.readValue(rawBody, UssdRequest.class);
        } catch (Exception ex) {
            log.error("Could not parse USSD request body: {}", rawBody, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        UssdResponse response = flowService.process(request);

        log.info("USSD out: sessionID={} continueSession={} message={}",
                response.sessionID(), response.continueSession(), response.message());

        return ResponseEntity.ok(response);
    }

    /**
     * Not used by Arkesel (they only POST), but handy for a quick manual sanity
     * check in a browser: if you see this JSON, the deployment is reachable and
     * routing correctly - so any USSD failure is happening upstream (gateway
     * configuration) or in request parsing, not "the app is down".
     */
    @GetMapping("/ussd")
    public ResponseEntity<String> pingCheck() {
        return ResponseEntity.ok("{\"status\":\"USSD endpoint is up - Arkesel should POST here, not GET\"}");
    }
}
