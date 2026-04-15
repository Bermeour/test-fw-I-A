package com.selfhealing.framework.waits;

import com.selfhealing.framework.watchdog.WatchdogConfig;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.FluentWait;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wait inteligente que verifica estabilidad completa de la UI antes de ejecutar acciones.
 *
 * Verifica en orden:
 *   1. document.readyState === 'complete'
 *   2. jQuery.active === 0 (si jQuery está presente y está configurado)
 *   3. Loaders/spinners CSS no visibles
 *   4. Overlays CSS no visibles
 *
 * Incluye cache temporal para evitar re-evaluar estabilidad en acciones consecutivas.
 *
 * Uso:
 *   StabilityWait sw = new StabilityWait(driver, StabilityConfig.defaultConfig());
 *   sw.waitUntilReady();   // wait completo con cache
 *   sw.waitUntilReadyFast(); // wait corto para reintentos
 */
public class StabilityWait {

    private final WebDriver driver;
    private final StabilityConfig config;
    private final WatchdogConfig watchdogConfig;
    private final AtomicLong lastStableAtMillis = new AtomicLong(0);

    public StabilityWait(WebDriver driver, StabilityConfig config) {
        this.driver = driver;
        this.config = config;
        this.watchdogConfig = config != null && config.getWatchdogConfig() != null
                ? config.getWatchdogConfig()
                : WatchdogConfig.defaultConfig();
    }

    /** Wait principal con cache — evita re-estabilización innecesaria entre acciones. */
    public void waitUntilReady() {
        long now        = System.currentTimeMillis();
        long lastStable = lastStableAtMillis.get();
        long cacheWindow = config.getStabilityCacheWindow().toMillis();

        if (lastStable > 0 && (now - lastStable) <= cacheWindow) {
            return;
        }

        waitUntilReady(config.getDefaultTimeout());
        lastStableAtMillis.set(System.currentTimeMillis());
    }

    /** Wait completo con timeout configurable. */
    public void waitUntilReady(Duration timeout) {
        FluentWait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(timeout)
                .pollingEvery(config.getPollingInterval())
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class)
                .ignoring(JavascriptException.class);

        wait.until(d -> isUiStableFull());
        lastStableAtMillis.set(System.currentTimeMillis());
    }

    /** Wait corto para reintentos y recovery. */
    public void waitUntilReadyFast() {
        FluentWait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(config.getFastRecoveryTimeout())
                .pollingEvery(config.getPollingInterval())
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class)
                .ignoring(JavascriptException.class);

        wait.until(d -> isUiStableFast());
    }

    /** Validación completa: document + jQuery + loaders + overlays. */
    public boolean isUiStableFull() {
        return isDocumentReady()
                && isJQueryInactive()
                && areBlockingLoadersGone()
                && areBlockingOverlaysGone();
    }

    /** Validación rápida: document + loaders + overlays (sin jQuery). */
    public boolean isUiStableFast() {
        return isDocumentReady()
                && areBlockingLoadersGone()
                && areBlockingOverlaysGone();
    }

    /** Invalida la cache manualmente cuando el flujo sabe que la UI cambió. */
    public void invalidateCache() {
        lastStableAtMillis.set(0);
    }

    private boolean isDocumentReady() {
        if (!config.isWaitForDocumentReady()) return true;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript("return document.readyState");
            return "complete".equals(String.valueOf(result));
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isJQueryInactive() {
        if (!config.isWaitForJQueryInactive()) return true;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript("return window.jQuery ? jQuery.active === 0 : true;");
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean areBlockingLoadersGone() {
        if (!watchdogConfig.isLoadersBlockExecution()) return true;
        return areSelectorsInvisible(watchdogConfig.getLoaderSelectors());
    }

    private boolean areBlockingOverlaysGone() {
        if (!watchdogConfig.isOverlaysBlockExecution()) return true;
        return areSelectorsInvisible(watchdogConfig.getOverlaySelectors());
    }

    private boolean areSelectorsInvisible(List<String> selectors) {
        if (selectors == null || selectors.isEmpty()) return true;
        for (String selector : selectors) {
            if (selector == null || selector.trim().isEmpty()) continue;
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                for (WebElement element : elements) {
                    if (isEffectivelyDisplayed(element)) return false;
                }
            } catch (Exception ignored) {}
        }
        return true;
    }

    private boolean isEffectivelyDisplayed(WebElement element) {
        try {
            return element != null && element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }
}
