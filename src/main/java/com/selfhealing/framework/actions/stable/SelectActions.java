package com.selfhealing.framework.actions.stable;

import com.selfhealing.framework.waits.StabilityConfig;
import com.selfhealing.framework.waits.StabilityWait;
import com.selfhealing.framework.watchdog.UiWatchdog;
import com.selfhealing.framework.watchdog.WatchdogResult;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.Select;

public class SelectActions extends AbstractActionGroup {

    public SelectActions(WebDriver driver, StabilityConfig config,
                         StabilityWait stabilityWait, UiWatchdog uiWatchdog, ActionLogger logger) {
        super(driver, config, stabilityWait, uiWatchdog, logger);
    }

    public SelectActions(WebDriver driver) { super(driver); }

    public void selectByText(By locator, String visibleText) {
        executeWithRetry(() -> { safeSelectByText(waitElementVisible(locator), visibleText); return null; },
                "selectByText(By): " + locator + " | text=" + safeValue(visibleText));
    }

    public void selectByText(WebElement element, String visibleText) {
        executeWithRetry(() -> { safeSelectByText(waitElementVisible(element), visibleText); return null; },
                "selectByText(WebElement) | text=" + safeValue(visibleText));
    }

    public void selectByValue(By locator, String value) {
        executeWithRetry(() -> { safeSelectByValue(waitElementVisible(locator), value); return null; },
                "selectByValue(By): " + locator + " | value=" + safeValue(value));
    }

    public void selectByValue(WebElement element, String value) {
        executeWithRetry(() -> { safeSelectByValue(waitElementVisible(element), value); return null; },
                "selectByValue(WebElement) | value=" + safeValue(value));
    }

    public void selectByIndex(By locator, int index) {
        executeWithRetry(() -> { safeSelectByIndex(waitElementVisible(locator), index); return null; },
                "selectByIndex(By): " + locator + " | index=" + index);
    }

    public void selectByIndex(WebElement element, int index) {
        executeWithRetry(() -> { safeSelectByIndex(waitElementVisible(element), index); return null; },
                "selectByIndex(WebElement) | index=" + index);
    }

    public String getSelectedText(By locator) {
        return executeWithRetry(() -> {
            Select select = new Select(waitElementVisible(locator));
            return safeTrim(select.getFirstSelectedOption().getText());
        }, "getSelectedText(By): " + locator);
    }

    public String getSelectedText(WebElement element) {
        return executeWithRetry(() -> {
            Select select = new Select(waitElementVisible(element));
            return safeTrim(select.getFirstSelectedOption().getText());
        }, "getSelectedText(WebElement)");
    }

    public String getSelectedValue(By locator) {
        return executeWithRetry(() -> {
            Select select = new Select(waitElementVisible(locator));
            return select.getFirstSelectedOption().getAttribute("value");
        }, "getSelectedValue(By): " + locator);
    }

    private void safeSelectByText(WebElement element, String visibleText) {
        try {
            new Select(element).selectByVisibleText(visibleText);
        } catch (ElementNotInteractableException | NoSuchElementException e) {
            WatchdogResult w = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(w)) {
                log("[WARN] selectByText falló y watchdog detectó bloqueo: " + w.toShortLog()); throw e;
            }
            log("[WARN] selectByText falló; fallback por value.");
            new Select(element).selectByValue(visibleText);
        }
    }

    private void safeSelectByValue(WebElement element, String value) {
        try {
            new Select(element).selectByValue(value);
        } catch (ElementNotInteractableException | NoSuchElementException e) {
            WatchdogResult w = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(w)) {
                log("[WARN] selectByValue falló y watchdog detectó bloqueo: " + w.toShortLog()); throw e;
            }
            log("[WARN] selectByValue falló; fallback por texto visible.");
            new Select(element).selectByVisibleText(value);
        }
    }

    private void safeSelectByIndex(WebElement element, int index) {
        try {
            new Select(element).selectByIndex(index);
        } catch (ElementNotInteractableException | NoSuchElementException e) {
            WatchdogResult w = inspectBlockingState();
            if (shouldDelegateRecoveryToEngine(w)) {
                log("[WARN] selectByIndex falló y watchdog detectó bloqueo: " + w.toShortLog()); throw e;
            }
            throw e;
        }
    }
}
