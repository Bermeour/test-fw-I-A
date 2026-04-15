package com.selfhealing.framework.actions;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;

/**
 * Acciones de scroll sobre la página y contenedores individuales.
 *
 * <p>Todos los métodos usan JavaScript porque funciona de forma consistente
 * en distintos navegadores, iframes y contenedores con {@code overflow: scroll},
 * a diferencia de la API nativa de Actions de Selenium que solo hace scroll
 * en la ventana principal.</p>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * web.actions.scroll.toElement(dataTable);
 * web.actions.scroll.toElementCentered(submitButton);   // ideal antes de screenshot
 * web.actions.scroll.toTop();
 * web.actions.scroll.insideContainer(scrollablePanel, 0, 300);
 * }</pre>
 */
public class ScrollActions {

    private final WebDriver driver;
    private final Waits     waits;

    /**
     * @param driver sesión activa de WebDriver
     * @param waits  operaciones de espera compartidas
     */
    public ScrollActions(WebDriver driver, Waits waits) {
        this.driver = driver;
        this.waits  = waits;
    }

    // -------------------------------------------------------------------------
    // Scroll hacia elementos específicos
    // -------------------------------------------------------------------------

    /**
     * Desplaza la página hasta que el elemento sea visible, alineado al borde
     * inferior del viewport. Rápido y suficiente para la mayoría de interacciones.
     *
     * @param element elemento al que hacer scroll
     */
    public void toElement(Element element) {
        WebElement el = waits.untilVisible(element);
        js().executeScript("arguments[0].scrollIntoView(true)", el);
    }

    /**
     * Desplaza la página hasta que el elemento quede centrado vertical y
     * horizontalmente en el viewport. Preferible antes de tomar screenshots
     * o cuando headers/footers fijos cubrirían el elemento alineado al borde.
     *
     * @param element elemento a centrar en el viewport
     */
    public void toElementCentered(Element element) {
        WebElement el = waits.untilVisible(element);
        js().executeScript(
            "arguments[0].scrollIntoView({behavior: 'auto', block: 'center', inline: 'center'})", el);
    }

    /**
     * Desplaza con animación suave hacia el elemento y espera a que termine.
     *
     * <p>Más natural visualmente para demos y grabaciones de pantalla.
     * Evitar en ejecuciones automatizadas rápidas ya que agrega ~1s de pausa.</p>
     *
     * @param element elemento al que hacer scroll suave
     */
    public void toElementSmooth(Element element) {
        WebElement el = waits.untilVisible(element);
        js().executeScript(
            "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'})", el);
        waits.sleep(1); // tiempo para que termine la animación de scroll suave
    }

    // -------------------------------------------------------------------------
    // Scroll en la página completa
    // -------------------------------------------------------------------------

    /**
     * Desplaza al inicio absoluto de la página (coordenadas 0, 0).
     */
    public void toTop() {
        js().executeScript("window.scrollTo({top: 0, left: 0, behavior: 'auto'})");
    }

    /**
     * Desplaza al final absoluto de la página.
     * Útil para disparar lazy-load o scroll infinito.
     */
    public void toBottom() {
        js().executeScript(
            "window.scrollTo({top: document.body.scrollHeight, behavior: 'auto'})");
    }

    /**
     * Desplaza la ventana una cantidad relativa de píxeles desde la posición actual.
     * Valores positivos desplazan hacia abajo/derecha; negativos hacia arriba/izquierda.
     *
     * @param x píxeles horizontales a desplazar
     * @param y píxeles verticales a desplazar
     */
    public void byPixels(int x, int y) {
        js().executeScript("window.scrollBy(arguments[0], arguments[1])", x, y);
    }

    /**
     * Desplaza exactamente una altura de viewport hacia abajo (equivale a pulsar PageDown).
     */
    public void onePageDown() {
        js().executeScript("window.scrollBy(0, window.innerHeight)");
    }

    /**
     * Desplaza exactamente una altura de viewport hacia arriba (equivale a pulsar PageUp).
     */
    public void onePageUp() {
        js().executeScript("window.scrollBy(0, -window.innerHeight)");
    }

    // -------------------------------------------------------------------------
    // Scroll dentro de un contenedor específico
    // -------------------------------------------------------------------------

    /**
     * Desplaza dentro de un contenedor con scroll propio (ej: un {@code <div>} con
     * {@code overflow: auto}, una grilla de datos, o un panel lateral) en lugar
     * de desplazar la ventana del navegador.
     *
     * @param container elemento contenedor con scroll interno
     * @param x         píxeles horizontales a desplazar dentro del contenedor
     * @param y         píxeles verticales a desplazar dentro del contenedor
     */
    public void insideContainer(Element container, int x, int y) {
        WebElement el = waits.untilVisible(container);
        js().executeScript("arguments[0].scrollBy(arguments[1], arguments[2])", el, x, y);
    }

    /**
     * Desplaza al inicio de un contenedor específico (establece {@code scrollTop = 0}).
     *
     * @param container elemento contenedor con scroll interno
     */
    public void containerToTop(Element container) {
        WebElement el = waits.untilVisible(container);
        js().executeScript("arguments[0].scrollTop = 0", el);
    }

    /**
     * Desplaza al final de un contenedor específico.
     * Activa lazy-load en listas virtualizadas que detectan el scroll al fondo.
     *
     * @param container elemento contenedor con scroll interno
     */
    public void containerToBottom(Element container) {
        WebElement el = waits.untilVisible(container);
        js().executeScript("arguments[0].scrollTop = arguments[0].scrollHeight", el);
    }

    // -------------------------------------------------------------------------
    // Helper interno
    // -------------------------------------------------------------------------

    private JavascriptExecutor js() {
        return (JavascriptExecutor) driver;
    }
}
