package com.selfhealing.framework.actions.stable;

import com.selfhealing.framework.waits.StabilityConfig;
import com.selfhealing.framework.waits.StabilityWait;
import com.selfhealing.framework.watchdog.UiWatchdog;
import com.selfhealing.framework.watchdog.WatchdogResult;
import com.selfhealing.framework.watchdog.WatchdogStatus;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Clase base para las familias de acciones estables.
 *
 * Provee:
 * - Retry automático con clasificación de excepciones
 * - Integración con StabilityWait (document.ready, jQuery, loaders, overlays)
 * - Integración con UiWatchdog (modales, overlays, alerts)
 * - Helpers de JavaScript, scroll, logging
 */
public abstract class AbstractActionGroup {

    protected final WebDriver driver;
    protected final StabilityConfig config;
    protected final StabilityWait stabilityWait;
    protected final UiWatchdog uiWatchdog;
    protected final ActionLogger logger;

    protected AbstractActionGroup(WebDriver driver,
                                  StabilityConfig config,
                                  StabilityWait stabilityWait,
                                  UiWatchdog uiWatchdog,
                                  ActionLogger logger) {
        this.driver       = driver;
        this.config       = config;
        this.stabilityWait = stabilityWait;
        this.uiWatchdog   = uiWatchdog;
        this.logger       = logger;
    }

    /** Constructor conveniente con configuración por defecto. */
    protected AbstractActionGroup(WebDriver driver) {
        this(driver, StabilityConfig.defaultConfig(),
             new StabilityWait(driver, StabilityConfig.defaultConfig()),
             new UiWatchdog(driver),
             new ConsoleActionLogger());
    }

    /** Ejecuta una acción con retry finito + wait de estabilidad en cada intento. */
    protected <T> T executeWithRetry(Action<T> action, String actionName) {
        RuntimeException lastException = null;

        for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
            try {
                log("--------------------------------------------------");
                log("Ejecutando acción: " + actionName + " | intento " + (attempt + 1));

                if (attempt == 0) {
                    stabilityWait.waitUntilReady();
                } else {
                    stabilityWait.waitUntilReadyFast();
                }

                inspectUi("PRE");
                T result = action.execute();
                log("Acción completada: " + actionName);
                return result;

            } catch (Exception e) {
                RetryDecision decision = ExceptionClassifier.classify(e);

                log("[ERROR] Falló acción: " + actionName);
                log("[ERROR] Clasificación: " + decision + " | " + e.getClass().getSimpleName() + " | " + safeMessage(e));

                if (decision == RetryDecision.FAIL_FAST) {
                    throw new RuntimeException("Excepción fatal en acción [" + actionName + "]", e);
                }

                lastException = new RuntimeException(
                        "Falló acción [" + actionName + "] en intento " + (attempt + 1), e);

                inspectUi("POST-FAIL");

                if (attempt < config.getMaxRetries()) {
                    log("Reintentando acción...");
                    safeWaitBeforeRetry();
                }
            }
        }

        throw lastException != null
                ? lastException
                : new RuntimeException("Falló acción [" + actionName + "] sin excepción concreta");
    }

    protected void inspectUi(String phase) {
        if (!config.isEnableWatchdog() || uiWatchdog == null) return;
        try {
            WatchdogResult result = uiWatchdog.inspect();
            log("[WATCHDOG][" + phase + "] " + result.toShortLog());
            if (result.isBlocking() && result.getStatus() != WatchdogStatus.CLEAN) {
                log("[WATCHDOG][" + phase + "] Condición potencialmente bloqueante detectada.");
            }
        } catch (Exception e) {
            log("[WATCHDOG][" + phase + "] No fue posible completar la inspección.");
        }
    }

    protected void safeWaitBeforeRetry() {
        try {
            stabilityWait.waitUntilReadyFast();
        } catch (Exception e) {
            log("[WARN] No fue posible estabilizar la UI antes del reintento.");
        }
    }

    protected WebElement waitElementClickable(By locator) {
        return new WebDriverWait(driver, config.getDefaultTimeout())
                .until(ExpectedConditions.elementToBeClickable(locator));
    }

    protected WebElement waitElementClickable(WebElement element) {
        if (element == null) throw new IllegalArgumentException("El WebElement no puede ser null.");
        try {
            if (!element.isDisplayed()) throw new ElementNotInteractableException("El elemento no está visible.");
            if (!element.isEnabled())   throw new ElementNotInteractableException("El elemento no está habilitado.");
            return element;
        } catch (WebDriverException e) {
            throw e;
        }
    }

    protected WebElement waitElementVisible(By locator) {
        return new WebDriverWait(driver, config.getDefaultTimeout())
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected WebElement waitElementVisible(WebElement element) {
        return new WebDriverWait(driver, config.getFastRecoveryTimeout())
                .until(ExpectedConditions.visibilityOf(element));
    }

    protected void scrollIntoViewCenter(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
        } catch (Exception ignored) {}
    }

    protected WatchdogResult inspectBlockingState() {
        try {
            if (uiWatchdog == null || !config.isEnableWatchdog()) return null;
            return uiWatchdog.inspect();
        } catch (Exception e) {
            return null;
        }
    }

    protected boolean isBlockingUi(WatchdogResult result) {
        if (result == null || !result.isBlocking()) return false;
        WatchdogStatus status = result.getStatus();
        return status == WatchdogStatus.MODAL_DETECTED
                || status == WatchdogStatus.OVERLAY_DETECTED
                || status == WatchdogStatus.LOADER_DETECTED
                || status == WatchdogStatus.ALERT_DETECTED;
    }

    protected boolean shouldDelegateRecoveryToEngine(WatchdogResult result) {
        return isBlockingUi(result);
    }

    protected Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    protected String safeTrim(String value)         { return value == null ? null : value.trim(); }
    protected String safeValue(String value)        { return value == null ? "null" : value; }
    protected String safeMessage(Throwable ex)      { return ex == null || ex.getMessage() == null ? "(sin detalle)" : ex.getMessage(); }

    protected void log(String message) {
        if (logger != null) logger.info("[" + getClass().getSimpleName() + "] " + message);
    }

    @FunctionalInterface
    protected interface Action<T> {
        T execute();
    }
}
