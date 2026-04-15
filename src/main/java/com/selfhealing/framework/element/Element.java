package com.selfhealing.framework.element;

import org.openqa.selenium.By;

/**
 * Objeto de valor inmutable que describe cómo localizar un elemento web.
 *
 * <p>Encapsula la estrategia de localización y el valor del selector, más
 * una etiqueta opcional legible para humanos usada en logs y reportes.
 * NO contiene una referencia a un {@code WebElement} vivo — solo almacena
 * la receta para encontrarlo.</p>
 *
 * <h3>Ejemplos de uso:</h3>
 * <pre>{@code
 * Element loginBtn = Element.id("btn-login").label("Botón Login");
 * Element username = Element.xpath("//input[@name='user']").label("Campo Usuario");
 * Element header   = Element.css("h1.page-title");
 * Element submit   = Element.text("Submit");
 * }</pre>
 */
public class Element {

    /** Estrategias de localización soportadas. */
    public enum LocatorType { XPATH, ID, CSS, NAME, TEXT }

    private final LocatorType type;
    private final String      value;
    private       String      displayLabel; // nombre legible para logs y reportes

    private Element(LocatorType type, String value) {
        this.type         = type;
        this.value        = value;
        this.displayLabel = value; // por defecto el selector mismo sirve como etiqueta
    }

    // -------------------------------------------------------------------------
    // Métodos de fábrica estáticos — uno por estrategia de localización
    // -------------------------------------------------------------------------

    /** Localiza el elemento por expresión XPath. */
    public static Element xpath(String xpath) { return new Element(LocatorType.XPATH, xpath); }

    /** Localiza el elemento por su atributo {@code id}. */
    public static Element id(String id)       { return new Element(LocatorType.ID,    id);    }

    /** Localiza el elemento por selector CSS. */
    public static Element css(String css)     { return new Element(LocatorType.CSS,   css);   }

    /** Localiza el elemento por su atributo {@code name}. */
    public static Element name(String name)   { return new Element(LocatorType.NAME,  name);  }

    /**
     * Localiza el elemento por su texto visible exacto.
     * Genera: {@code //*[normalize-space()='texto']}
     *
     * @param text texto visible exacto del elemento a encontrar
     */
    public static Element text(String text) {
        return new Element(LocatorType.XPATH,
            String.format("//*[normalize-space()='%s']", text));
    }

    // -------------------------------------------------------------------------
    // Builder fluido — agrega metadatos sin cambiar el localizador
    // -------------------------------------------------------------------------

    /**
     * Asigna una etiqueta legible para humanos a este elemento.
     * Se usa en mensajes de log y reportes de error para que sean descriptivos.
     * Devuelve {@code this} para permitir encadenamiento.
     *
     * <pre>{@code
     * Element btn = Element.id("submit").label("Botón Enviar");
     * }</pre>
     *
     * @param label nombre descriptivo del elemento
     */
    public Element label(String label) {
        this.displayLabel = label;
        return this;
    }

    // -------------------------------------------------------------------------
    // Conversión — uso interno del framework
    // -------------------------------------------------------------------------

    /**
     * Convierte este elemento a un localizador {@link By} de Selenium.
     * Es llamado por las clases de acciones y esperas cuando necesitan
     * buscar el elemento en el DOM.
     */
    public By toBy() {
        switch (type) {
            case XPATH: return By.xpath(value);
            case ID:    return By.id(value);
            case CSS:   return By.cssSelector(value);
            case NAME:  return By.name(value);
            default:    throw new IllegalStateException("Tipo de localizador no soportado: " + type);
        }
    }

    /**
     * Devuelve una representación XPath de este localizador.
     * Usado por el servicio de healing, que solo acepta expresiones XPath.
     * Los localizadores no-XPath se convierten a XPath equivalente.
     */
    public String toXpath() {
        switch (type) {
            case XPATH: return value;
            case ID:    return String.format("//*[@id='%s']", value);
            case NAME:  return String.format("//*[@name='%s']", value);
            case CSS:   return value; // el servicio también acepta selectores CSS
            default:    return value;
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public LocatorType getType()         { return type;         }
    public String      getValue()        { return value;        }
    public String      getDisplayLabel() { return displayLabel; }

    @Override
    public String toString() {
        return String.format("Element[%s → %s]", displayLabel, value);
    }
}
