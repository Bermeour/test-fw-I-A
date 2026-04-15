package com.selfhealing.framework.assertions;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;

import java.util.List;

/**
 * Assertions fluidas sobre elementos web.
 *
 * <p>Dos modos de uso:</p>
 * <ul>
 *   <li><strong>Estricto</strong> (via {@code web.assertThat(element)}) — lanza
 *       {@link AssertionError} inmediatamente al primer fallo.</li>
 *   <li><strong>Soft</strong> (via {@code web.softAssert(sa -> sa.check(element))}) — acumula
 *       los fallos y los lanza todos juntos al final con {@code assertAll()}.</li>
 * </ul>
 *
 * <h3>Ejemplo estricto:</h3>
 * <pre>{@code
 * web.assertThat(resultado)
 *    .isVisible()
 *    .hasText("Login exitoso")
 *    .hasAttribute("class", "success");
 * }</pre>
 *
 * <h3>Ejemplo soft:</h3>
 * <pre>{@code
 * web.softAssert(sa -> {
 *     sa.check(titulo).hasText("Dashboard");
 *     sa.check(menu).isVisible();
 *     sa.check(campo).hasValue("admin");
 * });
 * }</pre>
 */
public class WebAssert {

    private final WebDriver    driver;
    private final Waits        waits;
    private final Element      element;

    /**
     * Si es {@code null} → modo estricto (lanza AssertionError).
     * Si no es null → modo soft (añade el mensaje a la lista).
     */
    private final List<String> failures;

    /**
     * Constructor para uso externo (estricto).
     * Normalmente llamado via {@code web.assertThat(element)}.
     */
    public WebAssert(WebDriver driver, Waits waits, Element element) {
        this(driver, waits, element, null);
    }

    /**
     * Constructor para uso interno por {@link SoftAssertions} (modo soft).
     *
     * @param failures lista compartida donde acumular fallos; {@code null} = modo estricto
     */
    WebAssert(WebDriver driver, Waits waits, Element element, List<String> failures) {
        this.driver   = driver;
        this.waits    = waits;
        this.element  = element;
        this.failures = failures;
    }

    // =========================================================================
    // Texto y valor
    // =========================================================================

    /**
     * El texto visible del elemento es exactamente el esperado.
     *
     * @param expected texto esperado (se normaliza whitespace)
     */
    public WebAssert hasText(String expected) {
        try {
            String actual = driver.findElement(element.toBy()).getText().trim();
            if (!actual.equals(expected)) {
                fail(String.format("hasText(%s): esperado \"%s\" pero fue \"%s\"",
                    element.getDisplayLabel(), expected, actual));
            }
        } catch (NoSuchElementException e) {
            fail(String.format("hasText(%s): el elemento no existe en el DOM",
                element.getDisplayLabel()));
        }
        return this;
    }

    /**
     * El texto visible del elemento contiene la subcadena indicada.
     *
     * @param text subcadena que debe aparecer
     */
    public WebAssert containsText(String text) {
        try {
            String actual = driver.findElement(element.toBy()).getText();
            if (!actual.contains(text)) {
                fail(String.format("containsText(%s): \"%s\" no contiene \"%s\"",
                    element.getDisplayLabel(), actual, text));
            }
        } catch (NoSuchElementException e) {
            fail(String.format("containsText(%s): el elemento no existe en el DOM",
                element.getDisplayLabel()));
        }
        return this;
    }

    /**
     * El atributo {@code value} del elemento (inputs, textareas) es el esperado.
     *
     * @param expected valor esperado del campo
     */
    public WebAssert hasValue(String expected) {
        try {
            String actual = driver.findElement(element.toBy()).getAttribute("value");
            if (!expected.equals(actual)) {
                fail(String.format("hasValue(%s): esperado \"%s\" pero fue \"%s\"",
                    element.getDisplayLabel(), expected, actual));
            }
        } catch (NoSuchElementException e) {
            fail(String.format("hasValue(%s): el elemento no existe en el DOM",
                element.getDisplayLabel()));
        }
        return this;
    }

    /**
     * El elemento tiene el atributo con el valor exacto indicado.
     *
     * @param attribute nombre del atributo HTML (ej: "class", "href", "data-id")
     * @param expected  valor esperado del atributo
     */
    public WebAssert hasAttribute(String attribute, String expected) {
        try {
            String actual = driver.findElement(element.toBy()).getAttribute(attribute);
            if (!expected.equals(actual)) {
                fail(String.format("hasAttribute(%s)[%s]: esperado \"%s\" pero fue \"%s\"",
                    element.getDisplayLabel(), attribute, expected, actual));
            }
        } catch (NoSuchElementException e) {
            fail(String.format("hasAttribute(%s): el elemento no existe en el DOM",
                element.getDisplayLabel()));
        }
        return this;
    }

    /**
     * El atributo {@code class} del elemento contiene la clase CSS indicada.
     *
     * @param cssClass nombre de la clase CSS (sin punto)
     */
    public WebAssert hasClass(String cssClass) {
        try {
            String classAttr = driver.findElement(element.toBy()).getAttribute("class");
            if (classAttr == null || !classAttr.contains(cssClass)) {
                fail(String.format("hasClass(%s): class=\"%s\" no contiene \"%s\"",
                    element.getDisplayLabel(), classAttr, cssClass));
            }
        } catch (NoSuchElementException e) {
            fail(String.format("hasClass(%s): el elemento no existe en el DOM",
                element.getDisplayLabel()));
        }
        return this;
    }

    // =========================================================================
    // Visibilidad y estado
    // =========================================================================

    /**
     * El elemento existe en el DOM y está visible en pantalla.
     */
    public WebAssert isVisible() {
        try {
            boolean visible = driver.findElement(element.toBy()).isDisplayed();
            if (!visible) {
                fail(String.format("isVisible(%s): el elemento existe pero no está visible",
                    element.getDisplayLabel()));
            }
        } catch (NoSuchElementException e) {
            fail(String.format("isVisible(%s): el elemento no existe en el DOM",
                element.getDisplayLabel()));
        }
        return this;
    }

    /**
     * El elemento no está visible (no existe en el DOM o está oculto).
     */
    public WebAssert isNotVisible() {
        try {
            boolean visible = driver.findElement(element.toBy()).isDisplayed();
            if (visible) {
                fail(String.format("isNotVisible(%s): el elemento está visible pero no debería",
                    element.getDisplayLabel()));
            }
        } catch (NoSuchElementException ignored) {
            // No existe en el DOM → cumple la condición de "no visible"
        }
        return this;
    }

    /**
     * El elemento está visible y habilitado (no tiene atributo {@code disabled}).
     */
    public WebAssert isEnabled() {
        try {
            WebElement el = driver.findElement(element.toBy());
            if (!el.isDisplayed() || !el.isEnabled()) {
                fail(String.format("isEnabled(%s): el elemento no está habilitado",
                    element.getDisplayLabel()));
            }
        } catch (NoSuchElementException e) {
            fail(String.format("isEnabled(%s): el elemento no existe en el DOM",
                element.getDisplayLabel()));
        }
        return this;
    }

    /**
     * El elemento existe pero está deshabilitado ({@code disabled} attribute).
     */
    public WebAssert isDisabled() {
        try {
            WebElement el = driver.findElement(element.toBy());
            if (el.isEnabled()) {
                fail(String.format("isDisabled(%s): el elemento está habilitado pero no debería",
                    element.getDisplayLabel()));
            }
        } catch (NoSuchElementException e) {
            fail(String.format("isDisabled(%s): el elemento no existe en el DOM",
                element.getDisplayLabel()));
        }
        return this;
    }

    /**
     * El elemento está seleccionado (checkbox, radio button o {@code <option>}).
     */
    public WebAssert isSelected() {
        try {
            boolean selected = driver.findElement(element.toBy()).isSelected();
            if (!selected) {
                fail(String.format("isSelected(%s): el elemento no está seleccionado",
                    element.getDisplayLabel()));
            }
        } catch (NoSuchElementException e) {
            fail(String.format("isSelected(%s): el elemento no existe en el DOM",
                element.getDisplayLabel()));
        }
        return this;
    }

    /**
     * El elemento no está seleccionado.
     */
    public WebAssert isNotSelected() {
        try {
            boolean selected = driver.findElement(element.toBy()).isSelected();
            if (selected) {
                fail(String.format("isNotSelected(%s): el elemento está seleccionado pero no debería",
                    element.getDisplayLabel()));
            }
        } catch (NoSuchElementException e) {
            fail(String.format("isNotSelected(%s): el elemento no existe en el DOM",
                element.getDisplayLabel()));
        }
        return this;
    }

    // =========================================================================
    // Cardinalidad
    // =========================================================================

    /**
     * El número de elementos que coinciden con el localizador es exactamente el esperado.
     * Útil para verificar filas de tablas, items de lista, resultados de búsqueda.
     *
     * @param expected número exacto de elementos esperados
     */
    public WebAssert matchesCount(int expected) {
        List<WebElement> elements = driver.findElements(element.toBy());
        if (elements.size() != expected) {
            fail(String.format("matchesCount(%s): esperados %d elementos pero hay %d",
                element.getDisplayLabel(), expected, elements.size()));
        }
        return this;
    }

    /**
     * Existe al menos un elemento que coincide con el localizador.
     */
    public WebAssert exists() {
        List<WebElement> elements = driver.findElements(element.toBy());
        if (elements.isEmpty()) {
            fail(String.format("exists(%s): no se encontró ningún elemento",
                element.getDisplayLabel()));
        }
        return this;
    }

    /**
     * No existe ningún elemento que coincida con el localizador.
     */
    public WebAssert doesNotExist() {
        List<WebElement> elements = driver.findElements(element.toBy());
        if (!elements.isEmpty()) {
            fail(String.format("doesNotExist(%s): se encontraron %d elementos, se esperaba 0",
                element.getDisplayLabel(), elements.size()));
        }
        return this;
    }

    // =========================================================================
    // Helper interno — bifurcación estricto / soft
    // =========================================================================

    private void fail(String message) {
        if (failures == null) {
            throw new AssertionError(message);
        } else {
            failures.add(message);
        }
    }
}
