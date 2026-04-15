package com.selfhealing.framework.waits;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;

import java.time.Duration;

/**
 * Utilidades de espera e interacción para aplicaciones lentas e inestables
 * (Siebel, SAP, Oracle Forms).
 *
 * <p>No depende de ningún runner de tests (JUnit, TestNG, Cucumber).
 * Se crea con un {@link WebDriver} y se usa directamente desde cualquier contexto:</p>
 *
 * <pre>{@code
 * // Con Web.init()
 * Web web = Web.init(config);
 * SiebelWaits siebel = new SiebelWaits(web.driver);
 *
 * siebel.waitForPageReady();
 * siebel.safeClick("//button[@id='aceptar']");
 * siebel.safeSendKeys("//input[@name='monto']", "1000");
 * }</pre>
 */
public class SiebelWaits {

    // Timeout generoso para apps Siebel (lentas por naturaleza)
    private static final int PAGE_READY_TIMEOUT_SEC = 60;
    private static final int ELEMENT_TIMEOUT_SEC    = 30;
    private static final int POLL_MS                = 500;
    private static final int MAX_RETRIES            = 3;

    /** XPath genérico de spinner/overlay de carga — ajustar si el spinner es diferente. */
    public static final String SPINNER_XPATH =
        "//*[contains(@class,'spinner') or contains(@class,'loading') or contains(@class,'loader')]";

    private final WebDriver driver;

    public SiebelWaits(WebDriver driver) {
        this.driver = driver;
    }

    // -------------------------------------------------------------------------
    // Esperar que la página esté realmente lista
    //
    // Siebel miente: document.readyState dice "complete" pero internamente
    // sigue ejecutando JavaScript y reconstruyendo el DOM.
    // Esperamos 3 cosas: readyState, AJAX inactivo, y spinner desaparecido.
    // -------------------------------------------------------------------------

    public void waitForPageReady() {
        waitForDocumentReady();
        waitForAjaxIdle();
        waitForSpinnerGone();
    }

    private void waitForDocumentReady() {
        fluent(PAGE_READY_TIMEOUT_SEC)
            .ignoring(JavascriptException.class)
            .until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
    }

    private void waitForAjaxIdle() {
        // Funciona si la app usa jQuery; si no, lo ignora sin fallar
        fluent(30)
            .ignoring(Exception.class)
            .until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return typeof jQuery === 'undefined' || jQuery.active === 0"));
    }

    private void waitForSpinnerGone() {
        try {
            fluent(30)
                .ignoring(NoSuchElementException.class)
                .until(ExpectedConditions.invisibilityOfElementLocated(
                    By.xpath(SPINNER_XPATH)));
        } catch (TimeoutException e) {
            // Si el spinner no existía no pasa nada
        }
    }

    // -------------------------------------------------------------------------
    // Esperar que un elemento esté visible Y habilitado
    //
    // isDisplayed() = está en pantalla
    // isEnabled()   = se puede interactuar (no está en gris/disabled)
    //
    // Ignora StaleElementReferenceException porque Siebel puede destruir
    // y recrear el elemento mientras esperamos.
    // -------------------------------------------------------------------------

    public WebElement waitReady(String xpath) {
        return fluent(ELEMENT_TIMEOUT_SEC)
            .ignoring(NoSuchElementException.class)
            .ignoring(StaleElementReferenceException.class)
            .until(d -> {
                WebElement el = d.findElement(By.xpath(xpath));
                return (el.isDisplayed() && el.isEnabled()) ? el : null;
            });
    }

    // -------------------------------------------------------------------------
    // Click seguro con reintento
    //
    // El problema: Siebel reconstruye el DOM tras muchas acciones.
    // Si el DOM se reconstruyó entre el findElement y el click,
    // obtenemos StaleElementReferenceException.
    // Solución: reintentar hasta MAX_RETRIES veces.
    // -------------------------------------------------------------------------

    public void safeClick(String xpath) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                waitForPageReady();
                waitReady(xpath).click();
                waitForPageReady();
                return;
            } catch (StaleElementReferenceException e) {
                System.out.printf("[SiebelWaits] DOM reconstruido, reintento %d/%d — %s%n",
                    attempt, MAX_RETRIES, xpath);
                if (attempt == MAX_RETRIES) throw e;
            } catch (ElementClickInterceptedException e) {
                System.out.printf("[SiebelWaits] Click interceptado, reintento %d/%d — %s%n",
                    attempt, MAX_RETRIES, xpath);
                waitForSpinnerGone();
                if (attempt == MAX_RETRIES) throw e;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Escritura segura con verificación
    //
    // Siebel a veces "borra" lo que escribiste porque un evento JS
    // limpia el campo después de que escribes.
    // Verificamos que el valor quedó escrito; si no, reintentamos.
    // -------------------------------------------------------------------------

    public void safeSendKeys(String xpath, String value) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                waitForPageReady();
                WebElement el = waitReady(xpath);
                el.clear();
                el.sendKeys(value);

                String actual = el.getAttribute("value");
                if (value.equals(actual)) return;

                System.out.printf("[SiebelWaits] Valor no persistió ('%s' esperado, '%s' actual), reintento %d/%d%n",
                    value, actual, attempt, MAX_RETRIES);

            } catch (StaleElementReferenceException e) {
                System.out.printf("[SiebelWaits] DOM reconstruido, reintento %d/%d — %s%n",
                    attempt, MAX_RETRIES, xpath);
                if (attempt == MAX_RETRIES) throw e;
            }
        }
        throw new RuntimeException(String.format(
            "[SiebelWaits] No se pudo escribir '%s' en '%s' tras %d intentos",
            value, xpath, MAX_RETRIES));
    }

    // -------------------------------------------------------------------------
    // Esperar que un elemento desaparezca (útil tras cerrar diálogos en Siebel)
    // -------------------------------------------------------------------------

    public void waitGone(String xpath) {
        fluent(ELEMENT_TIMEOUT_SEC)
            .ignoring(StaleElementReferenceException.class)
            .until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(xpath)));
    }

    // -------------------------------------------------------------------------
    // Helper interno: FluentWait preconfigurado
    // -------------------------------------------------------------------------

    private FluentWait<WebDriver> fluent(int timeoutSeconds) {
        return new FluentWait<>(driver)
            .withTimeout(Duration.ofSeconds(timeoutSeconds))
            .pollingEvery(Duration.ofMillis(POLL_MS));
    }
}
