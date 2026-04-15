package com.selfhealing.framework.actions;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;

/**
 * Acciones de escritura sobre campos de formulario.
 *
 * <p>Diferencia claramente entre escritura nativa de Selenium y escritura via
 * JavaScript porque algunas aplicaciones (Siebel, SAP, formularios reactivos de
 * Angular) borran el campo después de {@code sendKeys}, ignoran {@code clear()},
 * o requieren que los eventos DOM se disparen manualmente tras establecer el valor.</p>
 *
 * <p>Todos los métodos reintentan hasta {@value #MAX_RETRIES} veces ante
 * {@link StaleElementReferenceException} — habitual en apps que reconstruyen
 * el DOM entre la búsqueda del elemento y la escritura real.</p>
 */
public class TypingActions {

    /** Número máximo de reintentos antes de relanzar la excepción. */
    private static final int MAX_RETRIES = 3;

    private final WebDriver driver;
    private final Waits     waits;

    /**
     * @param driver sesión activa de WebDriver
     * @param waits  operaciones de espera compartidas para verificar disponibilidad
     */
    public TypingActions(WebDriver driver, Waits waits) {
        this.driver = driver;
        this.waits  = waits;
    }

    // -------------------------------------------------------------------------
    // Escritura nativa de Selenium
    // -------------------------------------------------------------------------

    /**
     * Limpia el campo, escribe el texto y verifica que el valor persistió.
     *
     * <p>Algunas aplicaciones (especialmente Siebel) disparan eventos JavaScript
     * que borran el campo poco después de la entrada. Este método reintenta hasta
     * {@value #MAX_RETRIES} veces cuando eso ocurre, siendo la opción más segura
     * para escritura en general.</p>
     *
     * @param element campo de texto destino (input o textarea)
     * @param text    texto a escribir
     * @throws RuntimeException si el valor no persiste tras todos los reintentos
     */
    public void type(Element element, String text) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                waits.untilPageReady();
                WebElement el = waits.untilClickable(element);
                el.clear();
                el.sendKeys(text);

                // Verificar que el valor realmente quedó guardado en el campo
                if (text.equals(el.getAttribute("value"))) return;

                log("El valor no persistió (intento %d/%d) — %s", attempt, MAX_RETRIES, element);
            } catch (StaleElementReferenceException e) {
                log("StaleElement, reintentando %d/%d — %s", attempt, MAX_RETRIES, element);
                if (attempt == MAX_RETRIES) throw e;
            }
        }
        throw new RuntimeException(
            String.format("No se pudo escribir '%s' en %s tras %d intentos", text, element, MAX_RETRIES));
    }

    /**
     * Escribe texto sin limpiar el campo primero.
     *
     * <p>Usar para campos de autocompletado o búsqueda donde el valor existente
     * no debe borrarse antes de añadir nuevos caracteres.</p>
     *
     * @param element campo de texto destino
     * @param text    texto a añadir al valor actual
     */
    public void append(Element element, String text) {
        waits.untilClickable(element).sendKeys(text);
    }

    /**
     * Escribe carácter a carácter con una pausa configurable entre cada tecla.
     *
     * <p>Necesario en aplicaciones que realizan validaciones o búsquedas de
     * autocompletado con cada pulsación. También útil para evitar condiciones
     * de carrera en apps que procesan teclas más rápido de lo que pueden
     * renderizar sugerencias.</p>
     *
     * @param element          campo de texto destino
     * @param text             texto a escribir
     * @param msPerCharacter   milisegundos de pausa entre cada carácter (ej: 50)
     */
    public void typeSlow(Element element, String text, int msPerCharacter) {
        waits.untilClickable(element).clear();
        WebElement el = driver.findElement(element.toBy());
        for (char c : text.toCharArray()) {
            el.sendKeys(String.valueOf(c));
            try {
                Thread.sleep(msPerCharacter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Envía una tecla especial o combinación de teclas al elemento.
     *
     * <p>Ejemplos:
     * <pre>{@code
     * actions.pressKey(searchField, Keys.ENTER);
     * actions.pressKey(dateField,   Keys.TAB);
     * actions.pressKey(modal,       Keys.ESCAPE);
     * }</pre>
     * </p>
     *
     * @param element elemento que recibirá la tecla
     * @param key     constante de {@link Keys} o combinación con {@link Keys#chord}
     */
    public void pressKey(Element element, CharSequence key) {
        waits.untilClickable(element).sendKeys(key);
    }

    // -------------------------------------------------------------------------
    // Escritura via JavaScript
    // -------------------------------------------------------------------------

    /**
     * Establece el valor del campo directamente via JavaScript y dispara los
     * eventos {@code input} y {@code change} para que la aplicación detecte el cambio.
     *
     * <p>Usar cuando:
     * <ul>
     *   <li>El campo es {@code readonly} para Selenium pero acepta cambios por script</li>
     *   <li>Campos de Siebel o SAP que ignoran {@code sendKeys} nativo</li>
     *   <li>Date pickers o calendarios custom que no son inputs HTML estándar</li>
     * </ul>
     * </p>
     *
     * @param element elemento destino
     * @param value   valor a establecer en el campo
     */
    public void typeJS(Element element, String value) {
        WebElement el = waits.untilVisible(element);
        js().executeScript("arguments[0].value = arguments[1]", el, value);

        // Disparar eventos DOM para que los listeners de la app detecten el nuevo valor
        js().executeScript(
            "arguments[0].dispatchEvent(new Event('input',  {bubbles: true}));" +
            "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
            el);
    }

    // -------------------------------------------------------------------------
    // Limpieza de campos
    // -------------------------------------------------------------------------

    /**
     * Limpia el campo usando el método nativo {@code clear()} de Selenium.
     * Funciona para la mayoría de inputs HTML estándar.
     *
     * @param element campo a limpiar
     */
    public void clear(Element element) {
        waits.untilClickable(element).clear();
    }

    /**
     * Limpia el campo estableciendo su valor a cadena vacía via JavaScript
     * y disparando los eventos {@code input} / {@code change}.
     *
     * <p>Usar cuando {@code clear()} nativo no dispara la detección de cambios
     * de la aplicación (común en formularios Angular y Vue con validación reactiva).</p>
     *
     * @param element campo a limpiar
     */
    public void clearJS(Element element) {
        WebElement el = waits.untilVisible(element);
        js().executeScript("arguments[0].value = ''", el);
        js().executeScript(
            "arguments[0].dispatchEvent(new Event('input',  {bubbles: true}));" +
            "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
            el);
    }

    /**
     * Selecciona todo el texto del campo ({@code Ctrl+A}) y lo reemplaza de una vez.
     *
     * <p>Alternativa a {@code clear()} + {@code sendKeys()} que funciona mejor
     * en editores de texto enriquecido y algunos inputs custom.</p>
     *
     * @param element campo destino
     * @param text    texto de reemplazo
     */
    public void selectAllAndType(Element element, String text) {
        WebElement el = waits.untilClickable(element);
        el.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        el.sendKeys(text);
    }

    // -------------------------------------------------------------------------
    // Helpers internos
    // -------------------------------------------------------------------------

    private JavascriptExecutor js() {
        return (JavascriptExecutor) driver;
    }

    private void log(String msg, Object... args) {
        System.out.printf("[TypingActions] " + msg + "%n", args);
    }
}
