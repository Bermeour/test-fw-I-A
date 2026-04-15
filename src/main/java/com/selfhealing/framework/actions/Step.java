package com.selfhealing.framework.actions;

import com.selfhealing.framework.element.Element;

/**
 * Resultado encadenable de cualquier acción del framework.
 *
 * <p>Cada método de {@link Actions} que no devuelve un valor (type, click, clear…)
 * retorna un {@code Step} en lugar de {@code void}. Esto permite encadenar acciones
 * adicionales sobre <strong>el mismo elemento</strong> sin repetir la referencia:</p>
 *
 * <pre>{@code
 * // Sin encadenamiento — repetitivo
 * web.actions.type(username, "admin");
 * web.actions.visual.highlight(username);
 *
 * // Con encadenamiento — fluido
 * web.actions.type(username, "admin").border();
 *
 * // Cadenas más largas
 * web.actions.type(username, "admin")
 *            .highlightSuccess()
 *            .scroll();
 *
 * web.actions.click(loginBtn)
 *            .blink();
 *
 * // Leer el texto del elemento al final de la cadena
 * String msg = web.actions.click(errorLink).read();
 * }</pre>
 *
 * <p>La cadena recuerda el elemento sobre el que se actuó. Todos los métodos
 * devuelven {@code this} para permitir más encadenamiento, excepto los que
 * devuelven un valor ({@link #read()}, {@link #readValue()}).</p>
 */
public class Step {

    private final Actions actions;
    private final Element element;

    /** Construido internamente por {@link Actions}. No usar directamente. */
    Step(Actions actions, Element element) {
        this.actions = actions;
        this.element = element;
    }

    // =========================================================================
    // Acciones de interacción — sobre el mismo elemento
    // =========================================================================

    /** Click estándar sobre el mismo elemento. */
    public Step click() {
        actions.click(element);
        return this;
    }

    /** Click via JavaScript sobre el mismo elemento. */
    public Step clickJS() {
        actions.clickJS(element);
        return this;
    }

    /** Limpia el campo y escribe el texto indicado. */
    public Step type(String text) {
        actions.type(element, text);
        return this;
    }

    /** Escribe sin limpiar el campo primero. */
    public Step append(String text) {
        actions.append(element, text);
        return this;
    }

    /** Limpia el campo. */
    public Step clear() {
        actions.clear(element);
        return this;
    }

    // =========================================================================
    // Efectos visuales — borde y parpadeo
    // =========================================================================

    /**
     * Dibuja un borde naranja-rojo alrededor del elemento.
     * Alias directo de {@link #highlight()} — nombre intuitivo para demos.
     */
    public Step border() {
        actions.visual.highlight(element);
        return this;
    }

    /** Dibuja un borde naranja-rojo (resaltado por defecto). */
    public Step highlight() {
        actions.visual.highlight(element);
        return this;
    }

    /** Dibuja un borde verde — indica estado correcto o confirmado. */
    public Step highlightSuccess() {
        actions.visual.highlightSuccess(element);
        return this;
    }

    /** Dibuja un borde rojo — indica error o problema. */
    public Step highlightError() {
        actions.visual.highlightError(element);
        return this;
    }

    /** Dibuja un borde azul — marcado informativo. */
    public Step highlightInfo() {
        actions.visual.highlightInfo(element);
        return this;
    }

    /** Hace parpadear el elemento 3 veces. */
    public Step blink() {
        actions.visual.blink(element);
        return this;
    }

    /** Hace parpadear el elemento el número de veces indicado. */
    public Step blink(int times) {
        actions.visual.blink(element, times);
        return this;
    }

    /** Quita cualquier borde aplicado al elemento. */
    public Step removeBorder() {
        actions.visual.removeHighlight(element);
        return this;
    }

    // =========================================================================
    // Scroll
    // =========================================================================

    /** Hace scroll hasta que el elemento sea visible (alineado al borde inferior). */
    public Step scroll() {
        actions.scroll.toElement(element);
        return this;
    }

    /** Hace scroll hasta que el elemento quede centrado en el viewport. */
    public Step scrollCentered() {
        actions.scroll.toElementCentered(element);
        return this;
    }

    // =========================================================================
    // Screenshot — terminan la cadena devolviendo la ruta
    // =========================================================================

    /**
     * Captura el viewport completo, lo guarda con nombre automático y devuelve la ruta.
     * Termina la cadena devolviendo {@code String}.
     *
     * <pre>{@code
     * String ruta = web.actions.click(errorBtn).highlightError().screenshotToFile();
     * }</pre>
     */
    public String screenshotToFile() {
        return actions.visual.screenshotToFile();
    }

    /**
     * Captura el viewport completo, lo guarda en la ruta indicada y la devuelve.
     * Termina la cadena devolviendo {@code String}.
     */
    public String screenshotToFile(String filePath) {
        return actions.visual.screenshotToFile(filePath);
    }

    /**
     * Captura solo el elemento actual y lo guarda con nombre automático.
     * Termina la cadena devolviendo la ruta del archivo.
     *
     * <pre>{@code
     * String ruta = web.actions.click(errorField).highlightError().screenshotElement();
     * }</pre>
     */
    public String screenshotElement() {
        return actions.visual.screenshotElementToFile(element, null);
    }

    // =========================================================================
    // Lectura — terminan la cadena devolviendo un valor
    // =========================================================================

    /**
     * Lee el texto visible del elemento.
     * Termina la cadena ya que devuelve {@code String}, no {@code Step}.
     *
     * <pre>{@code
     * String msg = web.actions.click(errorLink).read();
     * }</pre>
     */
    public String read() {
        return actions.read(element);
    }

    /**
     * Lee el atributo {@code value} del elemento (inputs, textareas).
     * Termina la cadena devolviendo {@code String}.
     */
    public String readValue() {
        return actions.readValue(element);
    }

    // =========================================================================
    // Acceso al elemento
    // =========================================================================

    /** Devuelve el {@link Element} sobre el que opera esta cadena. */
    public Element getElement() {
        return element;
    }
}