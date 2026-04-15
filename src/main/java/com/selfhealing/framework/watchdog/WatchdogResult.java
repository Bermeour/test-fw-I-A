package com.selfhealing.framework.watchdog;

import java.time.Instant;
import java.util.Objects;

public class WatchdogResult {

    private final WatchdogStatus status;
    private final String detail;
    private final boolean blocking;
    private final String matchedSelector;
    private final String visibleText;
    private final Instant timestamp;

    public WatchdogResult(WatchdogStatus status,
                          String detail,
                          boolean blocking,
                          String matchedSelector,
                          String visibleText,
                          Instant timestamp) {
        this.status = Objects.requireNonNull(status, "status no puede ser null");
        this.detail = detail;
        this.blocking = blocking;
        this.matchedSelector = matchedSelector;
        this.visibleText = visibleText;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static WatchdogResult clean() {
        return new WatchdogResult(WatchdogStatus.CLEAN, "UI limpia", false, null, null, Instant.now());
    }

    public WatchdogStatus getStatus()       { return status; }
    public String getDetail()               { return detail; }
    public boolean isBlocking()             { return blocking; }
    public String getMatchedSelector()      { return matchedSelector; }
    public String getVisibleText()          { return visibleText; }
    public Instant getTimestamp()           { return timestamp; }

    public String toShortLog() {
        return "[" + status + "] " + detail +
                (matchedSelector != null ? " | selector=" + matchedSelector : "") +
                (visibleText     != null ? " | text="    + visibleText      : "");
    }

    @Override
    public String toString() {
        return "WatchdogResult{status=" + status + ", detail='" + detail + '\'' +
                ", blocking=" + blocking + ", matchedSelector='" + matchedSelector + '\'' +
                ", visibleText='" + visibleText + "', timestamp=" + timestamp + '}';
    }
}
