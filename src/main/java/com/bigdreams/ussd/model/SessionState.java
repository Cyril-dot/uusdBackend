package com.bigdreams.ussd.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionState {

    private final String sessionId;
    private final String msisdn;

    private UssdStep step = UssdStep.MAIN_MENU;
    private String network;          // mtn | telecel | ishare
    private List<Bundle> bundles = new ArrayList<>();
    private int bundlePage = 0;
    private Bundle selectedBundle;
    private String recipient;
    private String pendingReference; // Paystack charge reference while awaiting OTP

    private volatile long lastActivity = System.currentTimeMillis();

    public SessionState(String sessionId, String msisdn) {
        this.sessionId = sessionId;
        this.msisdn = msisdn;
    }

    public void touch() {
        this.lastActivity = System.currentTimeMillis();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public UssdStep getStep() {
        return step;
    }

    public void setStep(UssdStep step) {
        this.step = step;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public List<Bundle> getBundles() {
        return Collections.unmodifiableList(bundles);
    }

    public void setBundles(List<Bundle> bundles) {
        this.bundles = new ArrayList<>(bundles);
    }

    public int getBundlePage() {
        return bundlePage;
    }

    public void setBundlePage(int bundlePage) {
        this.bundlePage = bundlePage;
    }

    public Bundle getSelectedBundle() {
        return selectedBundle;
    }

    public void setSelectedBundle(Bundle selectedBundle) {
        this.selectedBundle = selectedBundle;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getPendingReference() {
        return pendingReference;
    }

    public void setPendingReference(String pendingReference) {
        this.pendingReference = pendingReference;
    }

    public long getLastActivity() {
        return lastActivity;
    }
}