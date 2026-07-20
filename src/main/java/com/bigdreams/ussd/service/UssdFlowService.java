package com.bigdreams.ussd.service;

import com.bigdreams.ussd.dto.UssdRequest;
import com.bigdreams.ussd.dto.UssdResponse;
import com.bigdreams.ussd.model.Bundle;
import com.bigdreams.ussd.model.PendingOrder;
import com.bigdreams.ussd.model.SessionState;
import com.bigdreams.ussd.model.UssdStep;
import com.bigdreams.ussd.service.PaystackService.ChargeResult;
import com.bigdreams.ussd.util.PhoneUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UssdFlowService {

    private static final Logger log = LoggerFactory.getLogger(UssdFlowService.class);

    private static final Map<String, String> NETWORK_BY_KEY = new LinkedHashMap<>();
    private static final Map<String, String> NETWORK_DISPLAY = new LinkedHashMap<>();

    static {
        NETWORK_BY_KEY.put("1", "mtn");
        NETWORK_BY_KEY.put("2", "telecel");
        NETWORK_BY_KEY.put("3", "ishare");

        NETWORK_DISPLAY.put("mtn", "MTN");
        NETWORK_DISPLAY.put("telecel", "Telecel");
        NETWORK_DISPLAY.put("ishare", "AirtelTigo (iShare)");
    }

    private final UssdSessionService sessionService;
    private final BundleCatalogService bundleCatalogService;
    private final PaystackService paystackService;
    private final PendingOrderStore pendingOrderStore;

    public UssdFlowService(UssdSessionService sessionService,
                           BundleCatalogService bundleCatalogService,
                           PaystackService paystackService,
                           PendingOrderStore pendingOrderStore) {
        this.sessionService = sessionService;
        this.bundleCatalogService = bundleCatalogService;
        this.paystackService = paystackService;
        this.pendingOrderStore = pendingOrderStore;
    }

    public UssdResponse process(UssdRequest req) {
        SessionState session = req.isNewSession() ? null : sessionService.get(req.sessionID());

        if (session == null) {
            session = sessionService.create(req.sessionID(), req.msisdn());
            return renderMainMenu(req, null);
        }

        String input = req.userDataTrimmed();

        return switch (session.getStep()) {
            case MAIN_MENU -> handleMainMenu(req, session, input);
            case SELECT_BUNDLE -> handleSelectBundle(req, session, input);
            case SELECT_RECIPIENT_TYPE -> handleSelectRecipientType(req, session, input);
            case ENTER_RECIPIENT -> handleEnterRecipient(req, session, input);
            case CONFIRM -> handleConfirm(req, session, input);
            case ENTER_OTP -> handleEnterOtp(req, session, input);
        };
    }

    // ---------- Step 1: network selection ----------

    private UssdResponse renderMainMenu(UssdRequest req, String error) {
        StringBuilder sb = new StringBuilder();
        if (error != null) {
            sb.append(error).append("\n");
        }
        sb.append("Buy Data Bundle\n");
        sb.append("1. MTN\n");
        sb.append("2. Telecel\n");
        sb.append("3. AirtelTigo (iShare)");
        return UssdResponse.continueWith(req, sb.toString());
    }

    private UssdResponse handleMainMenu(UssdRequest req, SessionState session, String input) {
        String network = NETWORK_BY_KEY.get(input);
        if (network == null) {
            return renderMainMenu(req, "Invalid option.");
        }

        List<Bundle> bundles = bundleCatalogService.getBundles(network);
        if (bundles.isEmpty()) {
            sessionService.remove(session.getSessionId());
            return UssdResponse.end(req, "No bundles are available for "
                    + NETWORK_DISPLAY.get(network) + " right now. Please try again later.");
        }

        session.setNetwork(network);
        session.setBundles(bundles);
        session.setBundlePage(0);
        session.setStep(UssdStep.SELECT_BUNDLE);

        return renderBundleMenu(req, session, null);
    }

    // ---------- Step 2: bundle selection ----------

    // Reserves digit 9 for "More" (when there's a next page) and digit 0 for "Back",
    // so each page shows at most 8 bundles - keeps every option reachable with a single digit
    // no matter how large the catalog grows.
    private static final int BUNDLES_PER_PAGE = 8;

    private UssdResponse renderBundleMenu(UssdRequest req, SessionState session, String error) {
        List<Bundle> all = session.getBundles();
        int page = session.getBundlePage();
        int start = page * BUNDLES_PER_PAGE;
        int end = Math.min(start + BUNDLES_PER_PAGE, all.size());
        List<Bundle> pageItems = all.subList(start, end);
        boolean hasMore = end < all.size();

        StringBuilder sb = new StringBuilder();
        if (error != null) {
            sb.append(error).append("\n");
        }
        sb.append("Select Bundle (").append(NETWORK_DISPLAY.get(session.getNetwork())).append(")");
        if (page > 0) {
            sb.append(" - page ").append(page + 1);
        }
        sb.append("\n");

        for (int i = 0; i < pageItems.size(); i++) {
            sb.append(i + 1).append(". ").append(pageItems.get(i).menuLabel()).append("\n");
        }
        if (hasMore) {
            sb.append(pageItems.size() + 1).append(". More\n");
        }
        sb.append("0. Back");
        return UssdResponse.continueWith(req, sb.toString());
    }

    private UssdResponse handleSelectBundle(UssdRequest req, SessionState session, String input) {
        List<Bundle> all = session.getBundles();
        int page = session.getBundlePage();
        int start = page * BUNDLES_PER_PAGE;
        int end = Math.min(start + BUNDLES_PER_PAGE, all.size());
        int pageCount = end - start;
        boolean hasMore = end < all.size();

        if ("0".equals(input)) {
            if (page > 0) {
                session.setBundlePage(page - 1);
                return renderBundleMenu(req, session, null);
            }
            session.setStep(UssdStep.MAIN_MENU);
            session.setBundlePage(0);
            return renderMainMenu(req, null);
        }

        int choice = parsePositiveInt(input);

        if (hasMore && choice == pageCount + 1) {
            session.setBundlePage(page + 1);
            return renderBundleMenu(req, session, null);
        }

        if (choice < 1 || choice > pageCount) {
            return renderBundleMenu(req, session, "Invalid option.");
        }

        session.setSelectedBundle(all.get(start + choice - 1));
        session.setStep(UssdStep.SELECT_RECIPIENT_TYPE);
        return renderRecipientTypeMenu(req, session, null);
    }

    // ---------- Step 3: recipient type ----------

    private UssdResponse renderRecipientTypeMenu(UssdRequest req, SessionState session, String error) {
        StringBuilder sb = new StringBuilder();
        if (error != null) {
            sb.append(error).append("\n");
        }
        sb.append("Send bundle to:\n");
        sb.append("1. My number (").append(PhoneUtil.toLocal(session.getMsisdn())).append(")\n");
        sb.append("2. Another number\n");
        sb.append("0. Back");
        return UssdResponse.continueWith(req, sb.toString());
    }

    private UssdResponse handleSelectRecipientType(UssdRequest req, SessionState session, String input) {
        switch (input) {
            case "0":
                session.setStep(UssdStep.SELECT_BUNDLE);
                return renderBundleMenu(req, session, null);
            case "1":
                session.setRecipient(PhoneUtil.toLocal(session.getMsisdn()));
                session.setStep(UssdStep.CONFIRM);
                return renderConfirm(req, session, null);
            case "2":
                session.setStep(UssdStep.ENTER_RECIPIENT);
                return UssdResponse.continueWith(req, "Enter recipient number\n(e.g. 0244123456)");
            default:
                return renderRecipientTypeMenu(req, session, "Invalid option.");
        }
    }

    // ---------- Step 4: manual recipient entry ----------

    private UssdResponse handleEnterRecipient(UssdRequest req, SessionState session, String input) {
        String candidate = input.replaceAll("[^0-9]", "");
        if (!PhoneUtil.isValidLocal(candidate)) {
            return UssdResponse.continueWith(req,
                    "Invalid number. Enter a 10-digit number starting with 0\n(e.g. 0244123456)");
        }
        session.setRecipient(candidate);
        session.setStep(UssdStep.CONFIRM);
        return renderConfirm(req, session, null);
    }

    // ---------- Step 5: confirmation ----------

    private UssdResponse renderConfirm(UssdRequest req, SessionState session, String error) {
        Bundle bundle = session.getSelectedBundle();
        StringBuilder sb = new StringBuilder();
        if (error != null) {
            sb.append(error).append("\n");
        }
        sb.append("Confirm Purchase\n");
        sb.append("Network: ").append(NETWORK_DISPLAY.get(session.getNetwork())).append("\n");
        sb.append("Bundle: ").append(bundle.size()).append("\n");
        sb.append("Recipient: ").append(session.getRecipient()).append("\n");
        sb.append("Amount: GHS").append(String.format("%.2f", bundle.price())).append(" (Mobile Money)\n");
        sb.append("1. Pay & Confirm\n");
        sb.append("2. Cancel");
        return UssdResponse.continueWith(req, sb.toString());
    }

    private UssdResponse handleConfirm(UssdRequest req, SessionState session, String input) {
        switch (input) {
            case "1":
                return initiatePaymentAndEnd(req, session);
            case "2":
                sessionService.remove(session.getSessionId());
                return UssdResponse.end(req, "Order cancelled. Thank you for using our service.");
            default:
                return renderConfirm(req, session, "Invalid option.");
        }
    }

    // ---------- Step 6: charge the customer via Paystack Mobile Money ----------
    //
    // The actual Big Dreams Data purchase does NOT happen here. It happens later,
    // in PaystackWebhookController, once Paystack confirms the charge succeeded.
    // This is because the customer has to approve a MoMo PIN prompt on their
    // phone, which almost always outlives the USSD session window.
    //
    // Some MoMo numbers (commonly first-time payers) require an extra step:
    // Paystack pauses the charge, texts the customer an OTP, and returns
    // status "send_otp" instead of going straight to the PIN prompt. In that
    // case we route to ENTER_OTP and only send the PIN prompt after the OTP
    // is submitted successfully.

    private UssdResponse initiatePaymentAndEnd(UssdRequest req, SessionState session) {
        Bundle bundle = session.getSelectedBundle();
        String payer = PhoneUtil.toLocal(session.getMsisdn());

        // Unique per attempt (not just per session) so a "cancel and retry" within
        // the same USSD session doesn't collide with a still-pending prior charge.
        String reference = "USSD_" + session.getSessionId() + "_" + System.currentTimeMillis();

        pendingOrderStore.put(new PendingOrder(
                reference,
                payer,
                session.getNetwork(),
                session.getRecipient(),
                bundle.sizeGb(),
                bundle.size(),
                bundle.price()
        ));

        ChargeResult charge = paystackService.chargeMobileMoney(session.getNetwork(), payer, bundle.price(), reference);

        if (!charge.initiated()) {
            pendingOrderStore.remove(reference);
            sessionService.remove(session.getSessionId());
            log.warn("Failed to initiate Paystack charge for reference {}: {}", reference, charge.message());
            return UssdResponse.end(req, "We could not start your payment. Please try again shortly.");
        }

        // First-time (or otherwise flagged) MoMo numbers: Paystack pauses the
        // charge and asks for an SMS OTP before it will send the PIN prompt.
        if ("send_otp".equalsIgnoreCase(charge.status())) {
            session.setPendingReference(reference);
            session.setStep(UssdStep.ENTER_OTP);
            return UssdResponse.continueWith(req,
                    "Enter the code sent to your phone via SMS to continue.");
        }

        // Already at pay_offline / success - PIN prompt is on its way, nothing more to collect here.
        sessionService.remove(session.getSessionId());
        return UssdResponse.end(req,
                "Approve the GHS" + String.format("%.2f", bundle.price())
                        + " payment prompt sent to your phone to complete your " + bundle.size() + " purchase.\n"
                        + "You'll get an SMS once it's confirmed.");
    }

    // ---------- Step 7: OTP entry (first-time MoMo numbers only) ----------

    private UssdResponse handleEnterOtp(UssdRequest req, SessionState session, String input) {
        String otp = input.replaceAll("[^0-9]", "");
        if (otp.isEmpty()) {
            return UssdResponse.continueWith(req, "Invalid code. Enter the code sent to your phone via SMS.");
        }

        Bundle bundle = session.getSelectedBundle();
        String reference = session.getPendingReference();

        ChargeResult result = paystackService.submitOtp(otp, reference);
        sessionService.remove(session.getSessionId());

        if (!result.initiated()) {
            pendingOrderStore.remove(reference);
            log.warn("OTP submission failed for reference {}: {}", reference, result.message());
            return UssdResponse.end(req, "That code didn't work. Please dial in again to retry your purchase.");
        }

        return UssdResponse.end(req,
                "Approve the GHS" + String.format("%.2f", bundle.price())
                        + " payment prompt sent to your phone to complete your " + bundle.size() + " purchase.\n"
                        + "You'll get an SMS once it's confirmed.");
    }

    // ---------- helpers ----------

    private static int parsePositiveInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}