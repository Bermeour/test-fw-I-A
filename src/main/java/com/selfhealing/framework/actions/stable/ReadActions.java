package com.selfhealing.framework.actions.stable;

import com.selfhealing.framework.waits.StabilityConfig;
import com.selfhealing.framework.waits.StabilityWait;
import com.selfhealing.framework.watchdog.UiWatchdog;
import com.selfhealing.framework.watchdog.WatchdogResult;
import org.openqa.selenium.*;

public class ReadActions extends AbstractActionGroup {

    public ReadActions(WebDriver driver, StabilityConfig config,
                       StabilityWait stabilityWait, UiWatchdog uiWatchdog, ActionLogger logger) {
        super(driver, config, stabilityWait, uiWatchdog, logger);
    }

    public ReadActions(WebDriver driver) { super(driver); }

    public String text(By locator) {
        return executeWithRetry(() -> safeReadText(waitElementVisible(locator)),
                "text(By): " + locator);
    }

    public String text(WebElement element) {
        return executeWithRetry(() -> safeReadText(waitElementVisible(element)),
                "text(WebElement)");
    }

    public String attribute(By locator, String attributeName) {
        return executeWithRetry(() -> safeReadAttribute(waitElementVisible(locator), attributeName),
                "attribute(By): " + locator + " | attr=" + attributeName);
    }

    public String attribute(WebElement element, String attributeName) {
        return executeWithRetry(() -> safeReadAttribute(waitElementVisible(element), attributeName),
                "attribute(WebElement) | attr=" + attributeName);
    }

    public boolean isVisible(By locator) {
        try { return waitElementVisible(locator).isDisplayed(); }
        catch (Exception e) { return false; }
    }

    public boolean isVisible(WebElement element) {
        try { return waitElementVisible(element).isDisplayed(); }
        catch (Exception e) { return false; }
    }

    public boolean exists(By locator) {
        try { return !driver.findElements(locator).isEmpty(); }
        catch (Exception e) { return false; }
    }

    private String safeReadText(WebElement element) {
        try {
            return safeTrim(element.getText());
        } catch (StaleElementReferenceException | NoSuchElementException e) {
            WatchdogResult w = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(w)) {
                log("[WARN] Read text falló y watchdog detectó bloqueo: " + w.toShortLog()); throw e;
            }
            log("[WARN] Read text falló; fallback JS.");
            Object value = js("return arguments[0].textContent || arguments[0].innerText || arguments[0].value || '';", element);
            return safeTrim(value == null ? null : String.valueOf(value));
        }
    }

    private String safeReadAttribute(WebElement element, String attributeName) {
        try {
            return element.getAttribute(attributeName);
        } catch (StaleElementReferenceException | NoSuchElementException e) {
            WatchdogResult w = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(w)) {
                log("[WARN] Read attribute falló y watchdog detectó bloqueo: " + w.toShortLog()); throw e;
            }
            log("[WARN] Read attribute falló; fallback JS.");
            Object value = js("return arguments[0].getAttribute(arguments[1]);", element, attributeName);
            return value == null ? null : String.valueOf(value);
        }
    }
}
