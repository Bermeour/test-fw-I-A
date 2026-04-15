package com.selfhealing.framework.audit;

public class SmartAuditEvent {

    private final SmartAuditEventType type;
    private final String phase;
    private final SmartAuditStatus status;
    private final String message;

    private String locator;
    private String pageUrl;
    private Integer score;
    private String mode;

    private final long timestamp;

    public SmartAuditEvent(SmartAuditEventType type,
                           String phase,
                           SmartAuditStatus status,
                           String message) {
        if (type    == null) throw new IllegalArgumentException("type is required");
        if (status  == null) throw new IllegalArgumentException("status is required");
        if (message == null) throw new IllegalArgumentException("message is required");

        this.type      = type;
        this.phase     = (phase == null || phase.isBlank()) ? "runtime" : phase;
        this.status    = status;
        this.message   = message;
        this.timestamp = System.currentTimeMillis();
    }

    public SmartAuditEvent locator(String locator)   { this.locator = locator; return this; }
    public SmartAuditEvent pageUrl(String pageUrl)   { this.pageUrl = pageUrl; return this; }
    public SmartAuditEvent score(Integer score)      { this.score   = score;   return this; }
    public SmartAuditEvent mode(String mode)         { this.mode    = mode;    return this; }

    public SmartAuditEventType getType()  { return type; }
    public String getPhase()              { return phase; }
    public SmartAuditStatus getStatus()   { return status; }
    public String getMessage()            { return message; }
    public String getLocator()            { return locator; }
    public String getPageUrl()            { return pageUrl; }
    public Integer getScore()             { return score; }
    public String getMode()               { return mode; }
    public long getTimestamp()            { return timestamp; }
}
