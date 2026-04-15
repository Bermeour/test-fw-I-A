package com.selfhealing.framework.actions.stable;

import com.selfhealing.framework.waits.StabilityConfig;
import com.selfhealing.framework.waits.StabilityWait;
import com.selfhealing.framework.watchdog.UiWatchdog;
import org.openqa.selenium.*;

public class VisualActions extends AbstractActionGroup {

    public VisualActions(WebDriver driver, StabilityConfig config,
                         StabilityWait stabilityWait, UiWatchdog uiWatchdog, ActionLogger logger) {
        super(driver, config, stabilityWait, uiWatchdog, logger);
    }

    public VisualActions(WebDriver driver) { super(driver); }

    public void border(By locator) {
        executeWithRetry(() -> {
            WebElement element = waitElementVisible(locator);
            ((JavascriptExecutor) driver).executeScript("arguments[0].style.border='3px solid red';", element);
            return null;
        }, "border(By): " + locator);
    }

    public void border(WebElement element) {
        executeWithRetry(() -> {
            WebElement resolved = waitElementVisible(element);
            ((JavascriptExecutor) driver).executeScript("arguments[0].style.border='3px solid red';", resolved);
            return null;
        }, "border(WebElement)");
    }

    public void highlight(By locator) {
        executeWithRetry(() -> {
            WebElement element = waitElementVisible(locator);
            ((JavascriptExecutor) driver).executeScript("arguments[0].style.backgroundColor='yellow';", element);
            return null;
        }, "highlight(By): " + locator);
    }

    public void highlight(WebElement element) {
        executeWithRetry(() -> {
            WebElement resolved = waitElementVisible(element);
            ((JavascriptExecutor) driver).executeScript("arguments[0].style.backgroundColor='yellow';", resolved);
            return null;
        }, "highlight(WebElement)");
    }

    public boolean isDisplayed(By locator) {
        try { return waitElementVisible(locator).isDisplayed(); }
        catch (Exception e) { return false; }
    }

    public boolean isEnabled(By locator) {
        try { return waitElementVisible(locator).isEnabled(); }
        catch (Exception e) { return false; }
    }
}
