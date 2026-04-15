package com.selfhealing.base;

import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;

import java.time.Duration;

/**
 * Capa de interacción para páginas lentas e inestables (Siebel, SAP, Oracle).
 *
 * Responsabilidad única: manejar tiempos de carga, recargas inesperadas del DOM
 * y reintentos ante elementos que desaparecen durante la interacción.
 *
 * No sabe nada de tests, JUnit, ni del servicio de healing.
 * BaseTest hereda de aquí y obtiene todos estos métodos.
 */
public abstract class SiebelWaits {

    // Timeout generoso para apps Siebel (lentas por naturaleza)
    private static final int PAGE_READY_TIMEOUT_SEC = 60;
    private static final int ELEMENT_TIMEOUT_SEC    = 30;
    private static final int POLL_MS                = 500;
    private static final int MAX_RETRIES            = 3;

    // El driver lo inicializa BaseTest en @BeforeEach
    protected WebDriver driver;

    // -------------------------------------------------------------------------
    // Esperar que la página esté realmente lista
    //
    // Siebel miente: document.readyState dice "complete" pero internamente
    // sigue ejecutando JavaScript y reconstruyendo el DOM.
    // Esperamos 3 cosas: readyState, AJAX inactivo, y spinner desaparecido.
    // -------------------------------------------------------------------------

    protected void waitForPageReady() {
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
        // XPath genérico para spinners/overlays de carga comunes en Siebel/SAP
        // Ajusta el selector al spinner específico de tu app si es necesario
        try {
            fluent(30)
                .ignoring(NoSuchElementException.class)
                .until(ExpectedConditions.invisibilityOfElementLocated(
                    By.xpath(Waits.SPINNER_XPATH)));
        } catch (TimeoutException e) {
            // Si el spinner no existía, no pasa nada
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

    protected WebElement waitReady(String xpath) {
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

    protected void safeClick(String xpath) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                waitForPageReady();
                waitReady(xpath).click();
                waitForPageReady(); // esperar que termine la recarga provocada por el click
                return;
            } catch (StaleElementReferenceException e) {
                System.out.printf("[safeClick] DOM reconstruido, reintento %d/%d — %s%n",
                    attempt, MAX_RETRIES, xpath);
                if (attempt == MAX_RETRIES) throw e;
            } catch (ElementClickInterceptedException e) {
                // Otro elemento está tapando el botón (típico en Siebel con overlays)
                System.out.printf("[safeClick] Click interceptado, reintento %d/%d — %s%n",
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

    protected void safeSendKeys(String xpath, String value) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                waitForPageReady();
                WebElement el = waitReady(xpath);
                el.clear();
                el.sendKeys(value);

                // Verificar que el valor realmente quedó en el campo
                String actual = el.getAttribute("value");
                if (value.equals(actual)) return;

                System.out.printf("[safeSendKeys] Valor no persistió ('%s' esperado, '%s' actual), reintento %d/%d%n",
                    value, actual, attempt, MAX_RETRIES);

            } catch (StaleElementReferenceException e) {
                System.out.printf("[safeSendKeys] DOM reconstruido, reintento %d/%d — %s%n",
                    attempt, MAX_RETRIES, xpath);
                if (attempt == MAX_RETRIES) throw e;
            }
        }
        throw new RuntimeException(
            String.format("[safeSendKeys] No se pudo escribir '%s' en '%s' tras %d intentos",
                value, xpath, MAX_RETRIES));
    }

    // -------------------------------------------------------------------------
    // Esperar que un elemento desaparezca (útil tras cerrar diálogos en Siebel)
    // -------------------------------------------------------------------------

    protected void waitGone(String xpath) {
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