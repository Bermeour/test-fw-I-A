package com.selfhealing.framework.actions.stable;

import com.selfhealing.framework.waits.StabilityConfig;
import com.selfhealing.framework.waits.StabilityWait;
import com.selfhealing.framework.watchdog.UiWatchdog;
import com.selfhealing.framework.watchdog.WatchdogResult;
import org.openqa.selenium.*;

public class ScrollActions extends AbstractActionGroup {

    public ScrollActions(WebDriver driver, StabilityConfig config,
                         StabilityWait stabilityWait, UiWatchdog uiWatchdog, ActionLogger logger) {
        super(driver, config, stabilityWait, uiWatchdog, logger);
    }

    public ScrollActions(WebDriver driver) { super(driver); }

    public void scrollIntoView(By locator) {
        executeWithRetry(() -> {
            safeScrollIntoView(waitElementVisible(locator));
            return null;
        }, "scrollIntoView(By): " + locator);
    }

    public void scrollIntoView(WebElement element) {
        executeWithRetry(() -> {
            safeScrollIntoView(waitElementVisible(element));
            return null;
        }, "scrollIntoView(WebElement)");
    }

    public void scrollToTop() {
        executeWithRetry(() -> { js("window.scrollTo(0, 0);"); return null; }, "scrollToTop()");
    }

    public void scrollToBottom() {
        executeWithRetry(() -> { js("window.scrollTo(0, document.body.scrollHeight);"); return null; }, "scrollToBottom()");
    }

    private void safeScrollIntoView(WebElement element) {
        try {
            js("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
        } catch (JavascriptException | StaleElementReferenceException e) {
            WatchdogResult w = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(w)) {
                log("[WARN] Scroll falló y watchdog detectó bloqueo: " + w.toShortLog()); throw e;
            }
            log("[WARN] Scroll falló; fallback JS simple.");
            js("arguments[0].scrollIntoView(true);", element);
        }
    }
}
