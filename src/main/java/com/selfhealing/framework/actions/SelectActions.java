package com.selfhealing.framework.actions;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Acciones para elementos nativos {@code <select>} de HTML.
 *
 * <p>Envuelve la clase {@link Select} de Selenium con verificaciones de
 * disponibilidad y mensajes de error más claros. Cubre selección, deselección
 * (para selects múltiples) e inspección de opciones disponibles.</p>
 *
 * <p><strong>Importante:</strong> Esta clase solo funciona con elementos
 * {@code <select>} HTML reales. Para dropdowns custom (construidos con
 * {@code <div>}, {@code <ul>}, o frameworks JS), usar {@link ClickActions}
 * para abrir la lista y hacer click en la opción deseada.</p>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * web.actions.select.byText(countryDropdown, "Colombia");
 * web.actions.select.byIndex(pageSizeSelect, 0);
 * String selected = web.actions.select.getSelectedText(statusSelect);
 * }</pre>
 */
public class SelectActions {

    private final WebDriver driver;
    private final Waits     waits;

    /**
     * @param driver sesión activa de WebDriver
     * @param waits  operaciones de espera compartidas
     */
    public SelectActions(WebDriver driver, Waits waits) {
        this.driver = driver;
        this.waits  = waits;
    }

    // -------------------------------------------------------------------------
    // Selección de opciones
    // -------------------------------------------------------------------------

    /**
     * Selecciona la opción cuyo texto visible coincide exactamente con el valor dado.
     *
     * @param element el elemento {@code <select>}
     * @param text    texto visible exacto de la opción a seleccionar
     */
    public void byText(Element element, String text) {
        toSelect(element).selectByVisibleText(text);
    }

    /**
     * Selecciona la opción cuyo atributo {@code value} coincide con el valor dado.
     * Útil cuando las etiquetas visibles no son estables pero los valores internos sí.
     *
     * @param element el elemento {@code <select>}
     * @param value   atributo {@code value} de la opción destino
     */
    public void byValue(Element element, String value) {
        toSelect(element).selectByValue(value);
    }

    /**
     * Selecciona la opción en la posición indicada (base cero).
     *
     * @param element el elemento {@code <select>}
     * @param index   posición base-cero (0 = primera opción)
     */
    public void byIndex(Element element, int index) {
        toSelect(element).selectByIndex(index);
    }

    // -------------------------------------------------------------------------
    // Deselección (solo para selects múltiples)
    // -------------------------------------------------------------------------

    /**
     * Desmarca la opción cuyo texto visible coincide con el valor dado.
     * Solo válido para elementos {@code <select multiple>}.
     *
     * @param element el elemento multi-select
     * @param text    texto visible de la opción a desmarcar
     */
    public void deselectByText(Element element, String text) {
        toSelect(element).deselectByVisibleText(text);
    }

    /**
     * Desmarca todas las opciones actualmente seleccionadas.
     * Solo válido para elementos multi-select.
     *
     * @param element el elemento multi-select
     */
    public void deselectAll(Element element) {
        toSelect(element).deselectAll();
    }

    // -------------------------------------------------------------------------
    // Lectura del estado actual
    // -------------------------------------------------------------------------

    /**
     * Devuelve el texto visible de la opción actualmente seleccionada.
     * Para multi-selects, devuelve la primera opción seleccionada.
     *
     * @param element el elemento {@code <select>}
     * @return texto visible de la opción seleccionada
     */
    public String getSelectedText(Element element) {
        return toSelect(element).getFirstSelectedOption().getText();
    }

    /**
     * Devuelve el atributo {@code value} de la opción actualmente seleccionada.
     *
     * @param element el elemento {@code <select>}
     * @return atributo {@code value} de la opción seleccionada
     */
    public String getSelectedValue(Element element) {
        return toSelect(element).getFirstSelectedOption().getAttribute("value");
    }

    /**
     * Devuelve el texto visible de todas las opciones disponibles en el select.
     *
     * @param element el elemento {@code <select>}
     * @return lista ordenada de etiquetas de opciones
     */
    public List<String> getOptions(Element element) {
        return toSelect(element).getOptions()
            .stream()
            .map(WebElement::getText)
            .collect(Collectors.toList());
    }

    /**
     * Verifica si existe una opción con el texto visible dado en el select.
     *
     * @param element el elemento {@code <select>}
     * @param text    etiqueta de la opción a buscar
     * @return {@code true} si la opción existe
     */
    public boolean hasOption(Element element, String text) {
        return getOptions(element).contains(text);
    }

    // -------------------------------------------------------------------------
    // Helper interno
    // -------------------------------------------------------------------------

    /**
     * Espera a que el elemento esté interactivo y lo envuelve en un {@link Select} de Selenium.
     */
    private Select toSelect(Element element) {
        waits.untilPageReady();
        WebElement el = waits.untilClickable(element);
        return new Select(el);
    }
}
