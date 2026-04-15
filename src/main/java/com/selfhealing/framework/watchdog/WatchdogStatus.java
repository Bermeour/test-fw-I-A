package com.selfhealing.framework.watchdog;

public enum WatchdogStatus {
    CLEAN,
    LOADER_DETECTED,
    OVERLAY_DETECTED,
    MODAL_DETECTED,
    ERROR_DETECTED,
    ALERT_DETECTED,
    DOM_UNSTABLE
}
