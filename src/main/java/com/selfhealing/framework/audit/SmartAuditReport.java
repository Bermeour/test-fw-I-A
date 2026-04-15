package com.selfhealing.framework.audit;

import java.util.ArrayList;
import java.util.List;

public class SmartAuditReport {

    private String appName;
    private String pageUrl;
    private String mode;
    private String originalLocator;

    private long startTime;
    private long endTime;

    private String finalLocator;
    private String resultSource;
    private boolean success;

    private final List<SmartAuditEvent> events = new ArrayList<>();

    public void addEvent(SmartAuditEvent e)        { events.add(e); }

    public void finishSuccess(String finalLocator, String source) {
        this.finalLocator = finalLocator;
        this.resultSource = source;
        this.success      = true;
        this.endTime      = System.currentTimeMillis();
    }

    public void finishFailure() {
        this.success = false;
        this.endTime = System.currentTimeMillis();
    }

    public long getDurationMs() {
        if (endTime <= startTime) return -1;
        return endTime - startTime;
    }

    public void setAppName(String appName)               { this.appName = appName; }
    public void setPageUrl(String pageUrl)               { this.pageUrl = pageUrl; }
    public void setMode(String mode)                     { this.mode = mode; }
    public void setOriginalLocator(String originalLocator) { this.originalLocator = originalLocator; }
    public void setStartTime(long startTime)             { this.startTime = startTime; }

    public String getAppName()          { return appName; }
    public String getPageUrl()          { return pageUrl; }
    public String getMode()             { return mode; }
    public String getOriginalLocator()  { return originalLocator; }
    public long getStartTime()          { return startTime; }
    public long getEndTime()            { return endTime; }
    public String getFinalLocator()     { return finalLocator; }
    public String getResultSource()     { return resultSource; }
    public boolean isSuccess()          { return success; }
    public List<SmartAuditEvent> getEvents() { return events; }
}
