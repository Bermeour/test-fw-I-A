package com.selfhealing.framework.actions.stable;

import com.selfhealing.framework.waits.StabilityConfig;
import com.selfhealing.framework.waits.StabilityWait;
import com.selfhealing.framework.watchdog.UiWatchdog;
import com.selfhealing.framework.watchdog.WatchdogResult;
import org.openqa.selenium.*;

public class WriteActions extends AbstractActionGroup {

    public WriteActions(WebDriver driver, StabilityConfig config,
                        StabilityWait stabilityWait, UiWatchdog uiWatchdog, ActionLogger logger) {
        super(driver, config, stabilityWait, uiWatchdog, logger);
    }

    public WriteActions(WebDriver driver) { super(driver); }

    public void write(By locator, String text) {
        executeWithRetry(() -> { safeWrite(waitElementVisible(locator), text); return null; },
                "write(By): " + locator + " | text=" + safeValue(text));
    }

    public void write(WebElement element, String text) {
        executeWithRetry(() -> { safeWrite(waitElementVisible(element), text); return null; },
                "write(WebElement) | text=" + safeValue(text));
    }

    public void clear(By locator) {
        executeWithRetry(() -> { safeClear(waitElementVisible(locator)); return null; },
                "clear(By): " + locator);
    }

    public void clear(WebElement element) {
        executeWithRetry(() -> { safeClear(waitElementVisible(element)); return null; },
                "clear(WebElement)");
    }

    public void append(By locator, String text) {
        executeWithRetry(() -> { safeAppend(waitElementVisible(locator), text); return null; },
                "append(By): " + locator + " | text=" + safeValue(text));
    }

    public void append(WebElement element, String text) {
        executeWithRetry(() -> { safeAppend(waitElementVisible(element), text); return null; },
                "append(WebElement) | text=" + safeValue(text));
    }

    public void pressEnter(By locator) {
        executeWithRetry(() -> { safeSendKey(waitElementVisible(locator), Keys.ENTER, "ENTER"); return null; },
                "pressEnter(By): " + locator);
    }

    public void pressEnter(WebElement element) {
        executeWithRetry(() -> { safeSendKey(waitElementVisible(element), Keys.ENTER, "ENTER"); return null; },
                "pressEnter(WebElement)");
    }

    public void pressTab(By locator) {
        executeWithRetry(() -> { safeSendKey(waitElementVisible(locator), Keys.TAB, "TAB"); return null; },
                "pressTab(By): " + locator);
    }

    public void pressTab(WebElement element) {
        executeWithRetry(() -> { safeSendKey(waitElementVisible(element), Keys.TAB, "TAB"); return null; },
                "pressTab(WebElement)");
    }

    private void safeWrite(WebElement element, String text) {
        try {
            element.clear();
            element.sendKeys(text);
        } catch (InvalidElementStateException e) {
            WatchdogResult w = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(w)) {
                log("[WARN] Write falló y watchdog detectó bloqueo: " + w.toShortLog()); throw e;
            }
            log("[WARN] Write falló; fallback: focus + clear + sendKeys.");
            js("arguments[0].focus();", element);
            element.clear();
            element.sendKeys(text);
        }
    }

    private void safeClear(WebElement element) {
        try {
            element.clear();
        } catch (InvalidElementStateException e) {
            WatchdogResult w = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(w)) {
                log("[WARN] Clear falló y watchdog detectó bloqueo: " + w.toShortLog()); throw e;
            }
            log("[WARN] Clear falló; fallback: CTRL+A + DELETE.");
            element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
            element.sendKeys(Keys.DELETE);
        }
    }

    private void safeAppend(WebElement element, String text) {
        try {
            element.sendKeys(text);
        } catch (InvalidElementStateException e) {
            WatchdogResult w = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(w)) {
                log("[WARN] Append falló y watchdog detectó bloqueo: " + w.toShortLog()); throw e;
            }
            log("[WARN] Append falló; fallback: focus + sendKeys.");
            js("arguments[0].focus();", element);
            element.sendKeys(text);
        }
    }

    private void safeSendKey(WebElement element, Keys key, String keyName) {
        try {
            element.sendKeys(key);
        } catch (InvalidElementStateException e) {
            WatchdogResult w = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(w)) {
                log("[WARN] sendKey(" + keyName + ") falló y watchdog detectó bloqueo: " + w.toShortLog()); throw e;
            }
            log("[WARN] sendKey(" + keyName + ") falló; fallback: focus.");
            js("arguments[0].focus();", element);
            element.sendKeys(key);
        }
    }
}
