package com.selfhealing.framework.watchdog;

import java.util.ArrayList;
import java.util.List;

import static com.selfhealing.framework.watchdog.WatchdogDefaults.*;

public class WatchdogConfig {

    private boolean enabled = true;
    private boolean loadersBlockExecution = true;
    private boolean overlaysBlockExecution = true;
    private List<String> loaderSelectors  = LOADER_SELECTORS;
    private List<String> overlaySelectors = OVERLAY_SELECTORS;
    private List<String> modalSelectors   = MODAL_SELECTORS;
    private List<String> errorSelectors   = ERROR_SELECTORS;

    public static WatchdogConfig defaultConfig() {
        return new WatchdogConfig();
    }

    public boolean isEnabled()                          { return enabled; }
    public WatchdogConfig setEnabled(boolean v)         { this.enabled = v; return this; }

    public boolean isLoadersBlockExecution()            { return loadersBlockExecution; }
    public WatchdogConfig setLoadersBlockExecution(boolean v) { this.loadersBlockExecution = v; return this; }

    public boolean isOverlaysBlockExecution()           { return overlaysBlockExecution; }
    public WatchdogConfig setOverlaysBlockExecution(boolean v) { this.overlaysBlockExecution = v; return this; }

    public List<String> getLoaderSelectors()            { return loaderSelectors; }
    public WatchdogConfig setLoaderSelectors(List<String> v) {
        this.loaderSelectors = v != null ? v : new ArrayList<>(); return this;
    }

    public List<String> getOverlaySelectors()           { return overlaySelectors; }
    public WatchdogConfig setOverlaySelectors(List<String> v) {
        this.overlaySelectors = v != null ? v : new ArrayList<>(); return this;
    }

    public List<String> getModalSelectors()             { return modalSelectors; }
    public WatchdogConfig setModalSelectors(List<String> v) {
        this.modalSelectors = v != null ? v : new ArrayList<>(); return this;
    }

    public List<String> getErrorSelectors()             { return errorSelectors; }
    public WatchdogConfig setErrorSelectors(List<String> v) {
        this.errorSelectors = v != null ? v : new ArrayList<>(); return this;
    }
}
