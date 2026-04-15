package com.selfhealing.framework.actions;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;

/**
 * Acciones de click e interacciones con el ratón.
 *
 * <p>Se documenta claramente si cada método usa eventos nativos de Selenium
 * o JavaScript, porque se comportan diferente:</p>
 * <ul>
 *   <li><strong>Nativo</strong> — mueve el cursor virtual al elemento y dispara
 *       un evento real del navegador. Puede ser bloqueado por overlays.</li>
 *   <li><strong>JavaScript</strong> — invoca {@code element.click()} o dispara
 *       un {@code MouseEvent} directamente en el nodo del DOM, ignorando
 *       restricciones de visibilidad y overlays.</li>
 * </ul>
 *
 * <p>La lógica de reintento maneja {@link StaleElementReferenceException} y
 * {@link ElementClickInterceptedException}, ambas comunes en SPAs y aplicaciones
 * empresariales legacy que reconstruyen partes del DOM tras cada acción.</p>
 */
public class ClickActions {

    /** Número máximo de reintentos antes de relanzar la excepción. */
    private static final int MAX_RETRIES = 3;

    private final WebDriver driver;
    private final Waits     waits;

    /**
     * @param driver sesión activa de WebDriver
     * @param waits  operaciones de espera para verificar disponibilidad antes y después del click
     */
    public ClickActions(WebDriver driver, Waits waits) {
        this.driver = driver;
        this.waits  = waits;
    }

    // -------------------------------------------------------------------------
    // Clicks nativos de Selenium
    // -------------------------------------------------------------------------

    /**
     * Click estándar con reintento automático ante inestabilidad del DOM.
     *
     * <p>Espera que la página esté lista antes y después del click
     * (la espera post-click captura re-renders asíncronos disparados por la acción).
     * Reintenta cuando ocurre:
     * <ul>
     *   <li>{@link StaleElementReferenceException} — el DOM fue reconstruido entre
     *       la búsqueda del elemento y el click</li>
     *   <li>{@link ElementClickInterceptedException} — un overlay (spinner, modal)
     *       está cubriendo temporalmente el elemento</li>
     * </ul>
     * </p>
     *
     * @param element elemento a hacer click
     */
    public void click(Element element) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                waits.untilPageReady();
                waits.untilClickable(element).click();
                waits.untilPageReady(); // esperar cualquier recarga/re-render disparado por el click
                return;
            } catch (StaleElementReferenceException e) {
                log("StaleElement en click, reintento %d/%d — %s", attempt, MAX_RETRIES, element);
                if (attempt == MAX_RETRIES) throw e;
            } catch (ElementClickInterceptedException e) {
                // Otro elemento (spinner, overlay) está encima — esperar y reintentar
                log("Click interceptado (overlay?), reintento %d/%d — %s", attempt, MAX_RETRIES, element);
                waits.sleep(1);
                if (attempt == MAX_RETRIES) throw e;
            }
        }
    }

    /**
     * Doble click sobre el elemento.
     *
     * <p>Usos comunes: abrir editores inline en grillas de datos, seleccionar
     * una palabra en un campo de texto, activar handlers de doble click custom.</p>
     *
     * @param element elemento a hacer doble click
     */
    public void doubleClick(Element element) {
        waits.untilPageReady();
        WebElement el = waits.untilClickable(element);
        new Actions(driver).doubleClick(el).perform();
        waits.untilPageReady();
    }

    /**
     * Click derecho (menú contextual) sobre el elemento.
     *
     * @param element elemento a hacer click derecho
     */
    public void rightClick(Element element) {
        waits.untilPageReady();
        WebElement el = waits.untilVisible(element);
        new Actions(driver).contextClick(el).perform();
    }

    /**
     * Click en una posición relativa a la esquina superior izquierda del elemento.
     *
     * <p>Usar para: canvas, mapas de imagen, sliders, o cualquier elemento
     * donde las coordenadas del click importan (ej: una celda específica de un gráfico).</p>
     *
     * @param element  elemento de referencia
     * @param offsetX  píxeles desde el borde izquierdo del elemento (puede ser negativo)
     * @param offsetY  píxeles desde el borde superior del elemento (puede ser negativo)
     */
    public void clickAt(Element element, int offsetX, int offsetY) {
        waits.untilPageReady();
        WebElement el = waits.untilVisible(element);
        new Actions(driver).moveToElement(el, offsetX, offsetY).click().perform();
    }

    /**
     * Enfoca el elemento y presiona ENTER para activarlo.
     *
     * <p>Alternativa accesible cuando el click nativo no funciona.
     * También útil para botones que escuchan {@code keydown} en lugar de {@code click}.</p>
     *
     * @param element elemento a activar por teclado
     */
    public void clickByKeyboard(Element element) {
        waits.untilClickable(element).sendKeys(Keys.ENTER);
    }

    /**
     * Mantiene presionado CTRL mientras hace click en el elemento.
     *
     * <p>Usos comunes: selección múltiple en listas, abrir un enlace en nueva pestaña,
     * seleccionar elementos no contiguos en una tabla.</p>
     *
     * @param element elemento a hacer click con Ctrl presionado
     */
    public void ctrlClick(Element element) {
        WebElement el = waits.untilVisible(element);
        new Actions(driver)
            .keyDown(Keys.CONTROL)
            .click(el)
            .keyUp(Keys.CONTROL)
            .perform();
    }

    // -------------------------------------------------------------------------
    // Clicks via JavaScript
    // -------------------------------------------------------------------------

    /**
     * Click via el método {@code element.click()} de JavaScript.
     *
     * <p>Omite el hit-testing del navegador — el elemento no necesita estar
     * visible ni libre de obstrucciones. Usar cuando:
     * <ul>
     *   <li>Un overlay persistente cubre el elemento</li>
     *   <li>El elemento está parcialmente fuera del viewport</li>
     *   <li>El click nativo activa un tooltip o estado hover no deseado</li>
     * </ul>
     * </p>
     *
     * @param element elemento a hacer click via JavaScript
     */
    public void clickJS(Element element) {
        waits.untilPageReady();
        WebElement el = waits.untilVisible(element);
        js().executeScript("arguments[0].click()", el);
        waits.untilPageReady();
    }

    /**
     * Hace scroll hasta el elemento y luego click via JavaScript.
     *
     * <p>Usar cuando el elemento está fuera del viewport y el flujo normal de
     * scroll + click es poco confiable (ej: headers fijos que cubren el elemento
     * después del scroll).</p>
     *
     * @param element elemento al que hacer scroll y click
     */
    public void scrollAndClickJS(Element element) {
        WebElement el = waits.untilVisible(element);
        js().executeScript("arguments[0].scrollIntoView({block: 'center'})", el);
        js().executeScript("arguments[0].click()", el);
        waits.untilPageReady();
    }

    /**
     * Dispara un {@code MouseEvent('click')} sintético sobre el elemento via JavaScript.
     *
     * <p>Diferencia con {@link #clickJS}: este despacha un evento DOM en lugar de
     * llamar al método {@code .click()} del elemento. Usar para elementos que escuchan
     * {@code addEventListener('click', ...)} y necesitan un {@code MouseEvent} con
     * bubbling (algunos componentes React/Vue requieren esto para actualizar su estado).</p>
     *
     * @param element elemento sobre el que disparar el evento de click
     */
    public void fireClickEvent(Element element) {
        WebElement el = waits.untilVisible(element);
        js().executeScript(
            "arguments[0].dispatchEvent(" +
            "  new MouseEvent('click', {bubbles: true, cancelable: true})" +
            ")", el);
    }

    // -------------------------------------------------------------------------
    // Helpers internos
    // -------------------------------------------------------------------------

    private JavascriptExecutor js() {
        return (JavascriptExecutor) driver;
    }

    private void log(String msg, Object... args) {
        System.out.printf("[ClickActions] " + msg + "%n", args);
    }
}
