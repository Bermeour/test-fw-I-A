package com.selfhealing.framework.actions.stable;

import com.selfhealing.framework.waits.StabilityConfig;
import com.selfhealing.framework.waits.StabilityWait;
import com.selfhealing.framework.watchdog.UiWatchdog;
import com.selfhealing.framework.watchdog.WatchdogResult;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;

public class ClickActions extends AbstractActionGroup {

    public ClickActions(WebDriver driver, StabilityConfig config,
                        StabilityWait stabilityWait, UiWatchdog uiWatchdog, ActionLogger logger) {
        super(driver, config, stabilityWait, uiWatchdog, logger);
    }

    public ClickActions(WebDriver driver) { super(driver); }

    public void click(By locator) {
        executeWithRetry(() -> {
            WebElement element = waitElementClickable(locator);
            scrollIntoViewCenter(element);
            safeClick(element);
            return null;
        }, "click(By): " + locator);
    }

    public void click(WebElement element) {
        executeWithRetry(() -> {
            WebElement clickable = waitElementClickable(element);
            scrollIntoViewCenter(clickable);
            safeClick(clickable);
            return null;
        }, "click(WebElement)");
    }

    public void doubleClick(By locator) {
        executeWithRetry(() -> {
            WebElement element = waitElementClickable(locator);
            scrollIntoViewCenter(element);
            new Actions(driver).doubleClick(element).perform();
            return null;
        }, "doubleClick(By): " + locator);
    }

    public void doubleClick(WebElement element) {
        executeWithRetry(() -> {
            WebElement clickable = waitElementClickable(element);
            scrollIntoViewCenter(clickable);
            new Actions(driver).doubleClick(clickable).perform();
            return null;
        }, "doubleClick(WebElement)");
    }

    public void jsClick(By locator) {
        executeWithRetry(() -> {
            WebElement element = waitElementVisible(locator);
            scrollIntoViewCenter(element);
            js("arguments[0].click();", element);
            return null;
        }, "jsClick(By): " + locator);
    }

    public void jsClick(WebElement element) {
        executeWithRetry(() -> {
            WebElement visible = waitElementVisible(element);
            scrollIntoViewCenter(visible);
            js("arguments[0].click();", visible);
            return null;
        }, "jsClick(WebElement)");
    }

    private void safeClick(WebElement element) {
        try {
            element.click();
        } catch (ElementClickInterceptedException intercepted) {
            WatchdogResult watchdogResult = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(watchdogResult)) {
                log("[WARN] Click interceptado y watchdog detectó condición bloqueante: "
                        + watchdogResult.toShortLog());
                throw intercepted;
            }
            log("[WARN] Click interceptado sin bloqueo claro; aplicando JS click fallback.");
            js("arguments[0].click();", element);
        }
    }
}
