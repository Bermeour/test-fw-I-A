package com.selfhealing.framework.repair;

import org.openqa.selenium.By;

import java.util.Collections;
import java.util.Map;

public class SuggestedLocator {

    private String type;
    private String value;
    private int score;
    private String reason;
    private Map<String, Object> meta;
    private RepairStatus status;

    public SuggestedLocator() {}

    public String getType()                     { return type; }
    public void setType(String type)            { this.type = type; }

    public String getValue()                    { return value; }
    public void setValue(String value)          { this.value = value; }

    public int getScore()                       { return score; }
    public void setScore(int score)             { this.score = score; }

    public String getReason()                   { return reason; }
    public void setReason(String reason)        { this.reason = reason; }

    public Map<String, Object> getMeta() {
        return meta == null ? Collections.emptyMap() : meta;
    }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }

    public RepairStatus getStatus()             { return status; }
    public void setStatus(RepairStatus status)  { this.status = status; }

    public By toBy() {
        if (type == null) throw new IllegalArgumentException("SuggestedLocator.type is null");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("SuggestedLocator.value is null/empty");
        switch (type.toLowerCase()) {
            case "css":   return By.cssSelector(value);
            case "xpath": return By.xpath(value);
            default:      throw new IllegalArgumentException("Unsupported locator type: " + type);
        }
    }

    @Override
    public String toString() {
        return "SuggestedLocator{type='" + type + "', value='" + value + "', score=" + score + ", reason='" + reason + "'}";
    }
}
