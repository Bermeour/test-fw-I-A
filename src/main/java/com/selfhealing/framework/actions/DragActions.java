package com.selfhealing.framework.actions;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;

/**
 * Acciones de arrastre y suelte (drag and drop).
 *
 * <p>Se proveen dos implementaciones porque muchos frameworks modernos
 * (React DnD, Angular CDK Drag Drop, SortableJS) no responden
 * a los eventos nativos de Selenium y requieren el enfoque JavaScript:</p>
 * <ul>
 *   <li><strong>Nativo</strong> — usa {@link Actions} de Selenium. Funciona con
 *       HTML5 nativo y widgets jQuery UI.</li>
 *   <li><strong>JavaScript</strong> — dispara eventos {@code MouseEvent} y
 *       {@code DragEvent} directamente en el DOM. Necesario para React/Angular.</li>
 * </ul>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * web.actions.drag.dragAndDrop(taskCard, doneColumn);
 * web.actions.drag.dragAndDropJS(reactCard, reactColumn); // para React DnD
 * web.actions.drag.dragByOffset(slider, 100, 0);          // para sliders
 * }</pre>
 */
public class DragActions {

    private final WebDriver driver;
    private final Waits     waits;

    /**
     * @param driver sesión activa de WebDriver
     * @param waits  operaciones de espera compartidas
     */
    public DragActions(WebDriver driver, Waits waits) {
        this.driver = driver;
        this.waits  = waits;
    }

    // -------------------------------------------------------------------------
    // Arrastre nativo (Selenium Actions API)
    // -------------------------------------------------------------------------

    /**
     * Arrastra el elemento origen y lo suelta sobre el destino.
     * Usa la API nativa de Selenium; funciona con HTML5 drag and drop
     * estándar y la mayoría de widgets jQuery UI.
     *
     * @param source elemento a arrastrar
     * @param target elemento destino donde se suelta
     */
    public void dragAndDrop(Element source, Element target) {
        waits.untilPageReady();
        WebElement elSource = waits.untilVisible(source);
        WebElement elTarget = waits.untilVisible(target);
        new Actions(driver).dragAndDrop(elSource, elTarget).perform();
        waits.untilPageReady();
    }

    /**
     * Arrastra el elemento una cantidad relativa de píxeles desde su posición actual.
     * Valores positivos mueven hacia la derecha/abajo; negativos hacia la izquierda/arriba.
     *
     * <p>Uso típico: ajustar sliders de rango o reposicionar elementos redimensionables.</p>
     *
     * @param element elemento a arrastrar
     * @param offsetX desplazamiento horizontal en píxeles
     * @param offsetY desplazamiento vertical en píxeles
     */
    public void dragByOffset(Element element, int offsetX, int offsetY) {
        waits.untilPageReady();
        WebElement el = waits.untilVisible(element);
        new Actions(driver).dragAndDropBy(el, offsetX, offsetY).perform();
        waits.untilPageReady();
    }

    /**
     * Arrastre lento y deliberado: presiona, pausa, mueve, pausa, suelta.
     * Más confiable para sliders o elementos que requieren que el puntero
     * se detenga brevemente antes de detectar el drop.
     *
     * @param source elemento a arrastrar
     * @param target elemento destino
     */
    public void dragAndDropSlow(Element source, Element target) {
        waits.untilPageReady();
        WebElement elSource = waits.untilVisible(source);
        WebElement elTarget = waits.untilVisible(target);

        new Actions(driver)
            .moveToElement(elSource)
            .pause(200)           // pausa antes de presionar para que el DOM esté listo
            .clickAndHold(elSource)
            .pause(300)           // simula que el usuario "sostiene" el elemento
            .moveToElement(elTarget)
            .pause(300)           // pausa encima del destino para disparar dragover
            .release()
            .perform();

        waits.untilPageReady();
    }

    // -------------------------------------------------------------------------
    // Arrastre via JavaScript
    // -------------------------------------------------------------------------

    /**
     * Simula drag and drop disparando eventos {@code MouseEvent} y {@code DragEvent}
     * directamente en el DOM mediante JavaScript.
     *
     * <p>Necesario para frameworks como React DnD, Angular CDK Drag Drop,
     * o cualquier librería que reemplaza los eventos nativos del navegador
     * con su propio sistema de eventos y por lo tanto no responde a
     * {@code Actions.dragAndDrop()}.</p>
     *
     * @param source elemento a arrastrar
     * @param target elemento destino
     */
    public void dragAndDropJS(Element source, Element target) {
        waits.untilPageReady();
        WebElement elSource = waits.untilVisible(source);
        WebElement elTarget = waits.untilVisible(target);

        // Script que simula la secuencia completa de eventos drag-and-drop
        String script =
            "function simulateDrag(src, dst) {" +
            "  function fire(el, type, x, y) {" +
            "    el.dispatchEvent(new MouseEvent(type, {bubbles:true, cancelable:true, clientX:x, clientY:y}));" +
            "  }" +
            "  var r1 = src.getBoundingClientRect();" +
            "  var r2 = dst.getBoundingClientRect();" +
            "  var x1 = r1.left + r1.width / 2,  y1 = r1.top + r1.height / 2;" +
            "  var x2 = r2.left + r2.width / 2,  y2 = r2.top + r2.height / 2;" +
            "  fire(src, 'mousedown', x1, y1);" +
            "  fire(src, 'dragstart', x1, y1);" +
            "  fire(src, 'drag',      x1, y1);" +
            "  fire(dst, 'dragenter', x2, y2);" +
            "  fire(dst, 'dragover',  x2, y2);" +
            "  fire(dst, 'drop',      x2, y2);" +
            "  fire(src, 'dragend',   x2, y2);" +
            "}" +
            "simulateDrag(arguments[0], arguments[1]);";

        ((JavascriptExecutor) driver).executeScript(script, elSource, elTarget);
        waits.untilPageReady();
    }

    /**
     * Mueve un elemento una cantidad relativa de píxeles usando eventos
     * {@code mousedown} / {@code mousemove} / {@code mouseup} via JavaScript.
     *
     * <p>Útil para sliders de tipo {@code <input type="range">} o elementos
     * arrastables que escuchan eventos de ratón en lugar de eventos drag.</p>
     *
     * @param element elemento a mover
     * @param offsetX desplazamiento horizontal en píxeles
     * @param offsetY desplazamiento vertical en píxeles
     */
    public void dragByOffsetJS(Element element, int offsetX, int offsetY) {
        WebElement el = waits.untilVisible(element);

        // Capturar arguments[] en variables locales antes de entrar a la función
        // interna — dentro de fire(), "arguments" referencia los parámetros de fire,
        // no los del script externo, por lo que acceder a arguments[0] desde dentro
        // de fire() causaría un error de scope.
        String script =
            "var el = arguments[0], offsetX = arguments[1], offsetY = arguments[2];" +
            "var r  = el.getBoundingClientRect();" +
            "var x  = r.left + r.width / 2, y = r.top + r.height / 2;" +
            "function fire(type, tx, ty) {" +
            "  el.dispatchEvent(new MouseEvent(type, {bubbles:true, cancelable:true, clientX:tx, clientY:ty}));" +
            "}" +
            "fire('mousedown', x,           y);" +
            "fire('mousemove', x + offsetX, y + offsetY);" +
            "fire('mouseup',   x + offsetX, y + offsetY);";

        ((JavascriptExecutor) driver).executeScript(script, el, offsetX, offsetY);
        waits.untilPageReady();
    }
}
