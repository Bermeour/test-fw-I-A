package com.selfhealing.framework.waits;

import com.selfhealing.framework.watchdog.WatchdogConfig;

import java.time.Duration;

/**
 * Configuración de estabilidad para StabilityWait y AbstractActionGroup.
 *
 * Uso típico:
 *   StabilityConfig cfg = StabilityConfig.defaultConfig()
 *       .setDefaultTimeout(Duration.ofSeconds(10))
 *       .setWaitForJQueryInactive(true);
 */
public class StabilityConfig {

    private Duration defaultTimeout      = Duration.ofSeconds(8);
    private Duration pollingInterval     = Duration.ofMillis(250);
    private int      maxRetries          = 0;
    private boolean  waitForDocumentReady   = true;
    private boolean  waitForJQueryInactive  = false;
    private Duration fastRecoveryTimeout = Duration.ofSeconds(2);
    private Duration stabilityCacheWindow= Duration.ofMillis(800);
    private WatchdogConfig watchdogConfig = WatchdogConfig.defaultConfig();

    public static StabilityConfig defaultConfig() {
        return new StabilityConfig();
    }

    public Duration getDefaultTimeout()          { return defaultTimeout; }
    public Duration getPollingInterval()         { return pollingInterval; }
    public int      getMaxRetries()              { return maxRetries; }
    public boolean  isWaitForDocumentReady()     { return waitForDocumentReady; }
    public boolean  isWaitForJQueryInactive()    { return waitForJQueryInactive; }
    public Duration getFastRecoveryTimeout()     { return fastRecoveryTimeout; }
    public Duration getStabilityCacheWindow()    { return stabilityCacheWindow; }
    public WatchdogConfig getWatchdogConfig()    { return watchdogConfig; }

    public boolean isEnableWatchdog() {
        return watchdogConfig != null && watchdogConfig.isEnabled();
    }

    public StabilityConfig setDefaultTimeout(Duration v) {
        this.defaultTimeout = v != null ? v : Duration.ofSeconds(8); return this;
    }
    public StabilityConfig setPollingInterval(Duration v) {
        this.pollingInterval = v != null ? v : Duration.ofMillis(250); return this;
    }
    public StabilityConfig setMaxRetries(int v) {
        this.maxRetries = Math.max(v, 0); return this;
    }
    public StabilityConfig setWaitForDocumentReady(boolean v) {
        this.waitForDocumentReady = v; return this;
    }
    public StabilityConfig setWaitForJQueryInactive(boolean v) {
        this.waitForJQueryInactive = v; return this;
    }
    public StabilityConfig setFastRecoveryTimeout(Duration v) {
        this.fastRecoveryTimeout = v != null ? v : Duration.ofSeconds(2); return this;
    }
    public StabilityConfig setStabilityCacheWindow(Duration v) {
        this.stabilityCacheWindow = v != null ? v : Duration.ofMillis(800); return this;
    }
    public StabilityConfig setWatchdogConfig(WatchdogConfig v) {
        this.watchdogConfig = v != null ? v : WatchdogConfig.defaultConfig(); return this;
    }
}
