package com.selfhealing.framework.waits;

import com.selfhealing.framework.element.Element;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;

import java.time.Duration;

/**
 * Operaciones de espera centralizadas para aplicaciones web lentas o inestables.
 *
 * <p>Usa {@link FluentWait} en lugar de esperas implícitas porque permite
 * ignorar excepciones transitorias como {@link StaleElementReferenceException},
 * comunes en Siebel, SAP, Oracle y aplicaciones SPA que reconstruyen el DOM
 * agresivamente tras cada acción del usuario.</p>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * web.waits.untilPageReady();
 * web.waits.untilVisible(loginButton);
 * web.waits.untilGone(loadingSpinner);
 * }</pre>
 */
public class Waits {

    /**
     * XPath compartido para detectar spinners y overlays de carga comunes.
     * Usado también por {@code SiebelWaits} para mantener un único origen de verdad.
     */
    public static final String SPINNER_XPATH =
        "//*[contains(@class,'loading')]"   +
        "|//*[contains(@class,'busy')]"     +
        "|//*[contains(@id,'spinner')]"     +
        "|//*[contains(@id,'loadingPanel')]";

    private final WebDriver driver;
    private final int       timeoutSeconds;

    /**
     * @param driver         sesión activa de WebDriver
     * @param timeoutSeconds tiempo máximo de espera aplicado a todas las operaciones
     */
    public Waits(WebDriver driver, int timeoutSeconds) {
        this.driver         = driver;
        this.timeoutSeconds = timeoutSeconds;
    }

    // -------------------------------------------------------------------------
    // Estado de carga de la página
    // -------------------------------------------------------------------------

    /**
     * Espera hasta que la página esté completamente lista para interactuar.
     *
     * <p>Verifica tres condiciones en secuencia:
     * <ol>
     *   <li>{@code document.readyState === 'complete'} — HTML y recursos estáticos cargados</li>
     *   <li>La cola AJAX de jQuery está inactiva (se omite si jQuery no está presente)</li>
     *   <li>Los spinners y overlays de carga han desaparecido del DOM</li>
     * </ol>
     * Es más confiable que verificar solo {@code readyState} porque frameworks
     * como Siebel reportan "complete" mientras aún ejecutan ciclos internos de JS.</p>
     */
    public void untilPageReady() {
        waitForDocumentReady();
        waitForAjaxIdle();
        waitForSpinnerGone();
    }

    /**
     * Espera a que {@code document.readyState} sea {@code "complete"}.
     * Garantiza que el HTML fue parseado y los scripts síncronos terminaron.
     */
    private void waitForDocumentReady() {
        fluent(timeoutSeconds)
            .ignoring(JavascriptException.class)
            .until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
    }

    /**
     * Espera a que la cola AJAX de jQuery reporte cero peticiones activas.
     * Se ignora silenciosamente si jQuery no está cargado en la página.
     */
    private void waitForAjaxIdle() {
        fluent(timeoutSeconds)
            .ignoring(Exception.class) // JavascriptException si jQuery no está presente
            .until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return typeof jQuery === 'undefined' || jQuery.active === 0"));
    }

    /**
     * Espera a que los indicadores de carga comunes desaparezcan.
     * El XPath cubre patrones genéricos de nombre; ajusta el selector
     * al spinner específico de tu aplicación si es necesario.
     */
    private void waitForSpinnerGone() {
        try {
            // Timeout más corto: si no hay spinner, fallar rápido
            fluent(15)
                .ignoring(NoSuchElementException.class)
                .until(ExpectedConditions.invisibilityOfElementLocated(
                    By.xpath(SPINNER_XPATH)));
        } catch (TimeoutException ignored) {
            // No había spinner — la página ya estaba limpia
        }
    }

    // -------------------------------------------------------------------------
    // Estado de elementos individuales
    // -------------------------------------------------------------------------

    /**
     * Espera hasta que el elemento esté presente en el DOM y visible en pantalla.
     * Ignora {@link StaleElementReferenceException} para manejar páginas que
     * reconstruyen el DOM mientras esperamos.
     *
     * @param element descriptor del elemento a esperar
     * @return el {@link WebElement} visible una vez encontrado
     */
    public WebElement untilVisible(Element element) {
        return fluent(timeoutSeconds)
            .ignoring(NoSuchElementException.class)
            .ignoring(StaleElementReferenceException.class)
            .until(ExpectedConditions.visibilityOfElementLocated(element.toBy()));
    }

    /**
     * Espera hasta que el elemento esté visible ({@code isDisplayed()}) y
     * habilitado ({@code isEnabled()}) — es decir, listo para recibir interacción.
     *
     * @param element descriptor del elemento a esperar
     * @return el {@link WebElement} clickable una vez disponible
     */
    public WebElement untilClickable(Element element) {
        return fluent(timeoutSeconds)
            .ignoring(NoSuchElementException.class)
            .ignoring(StaleElementReferenceException.class)
            .until(d -> {
                WebElement el = d.findElement(element.toBy());
                // Ambas condiciones deben cumplirse simultáneamente
                return (el.isDisplayed() && el.isEnabled()) ? el : null;
            });
    }

    /**
     * Espera hasta que el elemento deje de ser visible o sea eliminado del DOM.
     * Útil después de cerrar diálogos, enviar formularios o que desaparezca un spinner.
     *
     * @param element descriptor del elemento que debe desaparecer
     */
    public void untilGone(Element element) {
        fluent(timeoutSeconds)
            .ignoring(StaleElementReferenceException.class)
            .until(ExpectedConditions.invisibilityOfElementLocated(element.toBy()));
    }

    /**
     * Espera hasta que el texto visible del elemento contenga la subcadena esperada.
     *
     * @param element      elemento cuyo texto monitorear
     * @param expectedText subcadena que debe aparecer en el texto del elemento
     */
    public void untilTextPresent(Element element, String expectedText) {
        fluent(timeoutSeconds)
            .ignoring(NoSuchElementException.class)
            .ignoring(StaleElementReferenceException.class)
            .until(ExpectedConditions.textToBePresentInElementLocated(
                element.toBy(), expectedText));
    }

    /**
     * Pausa la ejecución durante el número de segundos indicado.
     *
     * <p><strong>Usar con moderación.</strong> Prefiere siempre esperas basadas en
     * condiciones. Reserva este método para casos donde no existe una condición
     * confiable (ej: animaciones CSS sin evento de fin).</p>
     *
     * @param seconds segundos a pausar
     */
    public void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Helper interno
    // -------------------------------------------------------------------------

    /**
     * Crea un {@link FluentWait} preconfigurado con el timeout dado y
     * un intervalo de polling de 500 ms. Los métodos individuales agregan
     * sus propias excepciones ignoradas sobre esta base.
     */
    private FluentWait<WebDriver> fluent(int seconds) {
        return new FluentWait<>(driver)
            .withTimeout(Duration.ofSeconds(seconds))
            .pollingEvery(Duration.ofMillis(500));
    }
}
