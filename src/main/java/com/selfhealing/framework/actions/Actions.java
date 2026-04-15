package com.selfhealing.framework.actions;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.log.StepLogger;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;

import java.util.Map;

/**
 * Fachada principal de acciones del framework.
 *
 * <p>Expone dos niveles de acceso:</p>
 * <ol>
 *   <li><strong>Métodos planos</strong> — las operaciones más frecuentes del día a día
 *       ({@code type}, {@code click}, {@code read}) están disponibles directamente
 *       sobre este objeto para máxima comodidad.</li>
 *   <li><strong>Sub-namespaces</strong> — operaciones especializadas agrupadas por
 *       categoría. Se acceden como campos públicos:
 *       {@code scroll}, {@code visual}, {@code navigate}, {@code alert},
 *       {@code drag}, {@code select}.</li>
 * </ol>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * // Operaciones planas — las más comunes
 * web.actions.type(usernameField, "admin");
 * web.actions.click(loginButton);
 * String msg = web.actions.read(errorMessage);
 *
 * // Sub-namespaces — operaciones especializadas
 * web.actions.scroll.toElement(dataTable);
 * web.actions.visual.highlight(submitButton);
 * web.actions.select.byText(countryDropdown, "Colombia");
 * web.actions.navigate.switchToFrame(contentFrame);
 * web.actions.alert.readAndAccept();
 * web.actions.drag.dragAndDrop(card, column);
 * }</pre>
 */
public class Actions {

    // -------------------------------------------------------------------------
    // Sub-namespaces — acceso público para permitir encadenamiento fluido
    // -------------------------------------------------------------------------

    /** Operaciones de scroll sobre la página y contenedores internos. */
    public final ScrollActions     scroll;

    /** Resaltado visual, parpadeo y capturas de pantalla. */
    public final VisualActions     visual;

    /** Navegación, historial, pestañas e iframes. */
    public final NavigationActions navigate;

    /** Alertas nativas del navegador (alert, confirm, prompt). */
    public final AlertActions      alert;

    /** Drag and drop nativo y via JavaScript. */
    public final DragActions       drag;

    /** Selección de opciones en elementos {@code <select>}. */
    public final SelectActions     select;

    // -------------------------------------------------------------------------
    // Instancias internas usadas por los métodos planos
    // -------------------------------------------------------------------------

    private final TypingActions typing;
    private final ClickActions  clicks;
    private final WebDriver     driver;
    private final Waits         waits;

    /**
     * Constructor invocado por {@link com.selfhealing.framework.Web}.
     * Los usuarios del framework nunca instancian esta clase directamente.
     *
     * @param driver instancia activa de WebDriver
     * @param waits  operaciones de espera compartidas
     */
    public Actions(WebDriver driver, Waits waits) {
        this.driver = driver;
        this.waits  = waits;

        // Instanciar todas las clases de acciones compartiendo driver y waits
        this.typing   = new TypingActions(driver, waits);
        this.clicks   = new ClickActions(driver, waits);
        this.scroll   = new ScrollActions(driver, waits);
        this.visual   = new VisualActions(driver, waits);
        this.navigate = new NavigationActions(driver, waits);
        this.alert    = new AlertActions(driver, waits);
        this.drag     = new DragActions(driver, waits);
        this.select   = new SelectActions(driver, waits);
    }

    // =========================================================================
    // Escritura — métodos planos más usados en el día a día
    // =========================================================================

    /**
     * Limpia el campo, escribe el texto y verifica que el valor persistió.
     * Reintenta si la aplicación borra el valor tras la escritura (común en Siebel).
     *
     * @param element campo de texto destino
     * @param text    texto a escribir
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step type(Element element, String text) {
        StepLogger.step("type", element.getDisplayLabel(), "\"" + text + "\"");
        typing.type(element, text);
        return new Step(this, element);
    }

    /**
     * Escribe sin limpiar el campo primero.
     * Útil en campos de autocompletado donde el valor existente no debe borrarse.
     *
     * @param element campo de texto destino
     * @param text    texto a añadir al valor actual
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step append(Element element, String text) {
        StepLogger.step("append", element.getDisplayLabel(), "\"" + text + "\"");
        typing.append(element, text);
        return new Step(this, element);
    }

    /**
     * Escribe carácter a carácter con una pausa configurable entre cada tecla.
     * Necesario en aplicaciones que realizan validaciones o búsquedas en tiempo real.
     *
     * @param element        campo de texto destino
     * @param text           texto a escribir
     * @param msPerCharacter milisegundos de pausa entre cada carácter (ej: 50)
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step typeSlow(Element element, String text, int msPerCharacter) {
        StepLogger.step("typeSlow", element.getDisplayLabel(), "\"" + text + "\" (" + msPerCharacter + "ms/char)");
        typing.typeSlow(element, text, msPerCharacter);
        return new Step(this, element);
    }

    /**
     * Establece el valor del campo directamente via JavaScript y dispara eventos
     * {@code input} y {@code change}. Útil para campos protegidos o componentes
     * custom que no responden a {@code sendKeys}.
     *
     * @param element campo destino
     * @param value   valor a establecer
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step typeJS(Element element, String value) {
        StepLogger.step("typeJS", element.getDisplayLabel(), "\"" + value + "\"");
        typing.typeJS(element, value);
        return new Step(this, element);
    }

    /**
     * Presiona una tecla especial (ENTER, TAB, ESCAPE, etc.) sobre el elemento.
     *
     * @param element elemento que recibirá la tecla
     * @param key     constante de {@link Keys} o resultado de {@link Keys#chord}
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step pressKey(Element element, CharSequence key) {
        StepLogger.step("pressKey", element.getDisplayLabel(), key.toString());
        typing.pressKey(element, key);
        return new Step(this, element);
    }

    /**
     * Limpia el campo usando el método nativo de Selenium.
     *
     * @param element campo a limpiar
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step clear(Element element) {
        StepLogger.step("clear", element.getDisplayLabel());
        typing.clear(element);
        return new Step(this, element);
    }

    /**
     * Limpia el campo via JavaScript. Útil cuando {@code clear()} nativo
     * no dispara los eventos de cambio que la aplicación espera.
     *
     * @param element campo a limpiar
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step clearJS(Element element) {
        StepLogger.step("clearJS", element.getDisplayLabel());
        typing.clearJS(element);
        return new Step(this, element);
    }

    // =========================================================================
    // Click — métodos planos más usados en el día a día
    // =========================================================================

    /**
     * Click estándar con reintento automático ante DOM inestable u overlays temporales.
     *
     * @param element elemento a hacer click
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step click(Element element) {
        StepLogger.step("click", element.getDisplayLabel());
        clicks.click(element);
        return new Step(this, element);
    }

    /**
     * Click via JavaScript — evita bloqueos por overlays y elementos fuera del viewport.
     *
     * @param element elemento a hacer click via JS
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step clickJS(Element element) {
        StepLogger.step("clickJS", element.getDisplayLabel());
        clicks.clickJS(element);
        return new Step(this, element);
    }

    /**
     * Doble click sobre el elemento.
     *
     * @param element elemento a hacer doble click
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step doubleClick(Element element) {
        StepLogger.step("dblClick", element.getDisplayLabel());
        clicks.doubleClick(element);
        return new Step(this, element);
    }

    /**
     * Click derecho (menú contextual) sobre el elemento.
     *
     * @param element elemento a hacer click derecho
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step rightClick(Element element) {
        StepLogger.step("rightClick", element.getDisplayLabel());
        clicks.rightClick(element);
        return new Step(this, element);
    }

    /**
     * Click en coordenadas relativas al elemento (para canvas, mapas, sliders).
     *
     * @param element elemento de referencia
     * @param x       píxeles desde el borde izquierdo del elemento
     * @param y       píxeles desde el borde superior del elemento
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step clickAt(Element element, int x, int y) {
        clicks.clickAt(element, x, y);
        return new Step(this, element);
    }

    /**
     * Enfoca el elemento y presiona ENTER como alternativa accesible al click.
     *
     * @param element elemento a activar por teclado
     * @return {@link Step} encadenable sobre el mismo elemento
     */
    public Step clickByKeyboard(Element element) {
        clicks.clickByKeyboard(element);
        return new Step(this, element);
    }

    // =========================================================================
    // Formularios — llenado masivo
    // =========================================================================

    /**
     * Llena múltiples campos de un formulario de una sola vez a partir de un mapa.
     *
     * <p>El mapa se procesa en el orden de inserción ({@link java.util.LinkedHashMap}
     * recomendado si el orden importa). Cada entrada llama a {@link #type} internamente,
     * con todas sus garantías de reintento y verificación.</p>
     *
     * <pre>{@code
     * web.actions.fillForm(Map.of(
     *     usuario,  "admin",
     *     password, "secret",
     *     empresa,  "ACME"
     * ));
     * }</pre>
     *
     * @param fields mapa de Element → valor a escribir
     */
    public void fillForm(Map<Element, String> fields) {
        StepLogger.step("fillForm", fields.size() + " campos", null);
        fields.forEach((element, value) -> type(element, value));
    }

    // =========================================================================
    // Lectura de valores y atributos
    // =========================================================================

    /**
     * Lee el texto visible del elemento (contenido del tag, equivalente a {@code innerText}).
     *
     * @param element elemento a leer
     * @return texto visible del elemento
     */
    public String read(Element element) {
        waits.untilVisible(element);
        String text = driver.findElement(element.toBy()).getText();
        StepLogger.step("read", element.getDisplayLabel(), "\"" + text + "\"");
        return text;
    }

    /**
     * Lee el atributo {@code value} de un input o textarea.
     *
     * @param element campo de formulario
     * @return valor actual del campo
     */
    public String readValue(Element element) {
        waits.untilVisible(element);
        return driver.findElement(element.toBy()).getAttribute("value");
    }

    /**
     * Lee el valor de cualquier atributo HTML del elemento.
     *
     * @param element   elemento a inspeccionar
     * @param attribute nombre del atributo HTML (ej: "href", "class", "data-id")
     * @return valor del atributo, o {@code null} si no existe
     */
    public String readAttribute(Element element, String attribute) {
        waits.untilVisible(element);
        return driver.findElement(element.toBy()).getAttribute(attribute);
    }

    // =========================================================================
    // Estado del elemento
    // =========================================================================

    /**
     * Indica si el elemento existe en el DOM y es visible en pantalla.
     * No lanza excepción si el elemento no existe.
     *
     * @param element elemento a verificar
     * @return {@code true} si está visible
     */
    public boolean isVisible(Element element) {
        try {
            return driver.findElement(element.toBy()).isDisplayed();
        } catch (NoSuchElementException | StaleElementReferenceException e) {
            return false;
        }
    }

    /**
     * Indica si el elemento está visible y habilitado (no deshabilitado con {@code disabled}).
     *
     * @param element elemento a verificar
     * @return {@code true} si está habilitado
     */
    public boolean isEnabled(Element element) {
        try {
            WebElement el = driver.findElement(element.toBy());
            return el.isDisplayed() && el.isEnabled();
        } catch (NoSuchElementException | StaleElementReferenceException e) {
            return false;
        }
    }

    /**
     * Indica si el elemento está seleccionado (checkboxes, radio buttons, options).
     *
     * @param element elemento a verificar
     * @return {@code true} si está seleccionado
     */
    public boolean isSelected(Element element) {
        try {
            return driver.findElement(element.toBy()).isSelected();
        } catch (NoSuchElementException | StaleElementReferenceException e) {
            return false;
        }
    }
}
