package com.bigdreams.ussd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ussd.session")
public class UssdProperties {

    /**
     * How long an idle USSD session is kept in memory before being dropped.
     */
    private int timeoutMinutes = 5;

    /**
     * Max number of items shown in a single numbered USSD menu (keeps input single-digit).
     */
    private int maxMenuItems = 9;

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public int getMaxMenuItems() {
        return maxMenuItems;
    }

    public void setMaxMenuItems(int maxMenuItems) {
        this.maxMenuItems = maxMenuItems;
    }
}
