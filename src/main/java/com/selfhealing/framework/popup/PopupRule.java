package com.selfhealing.framework.popup;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Regla que describe cómo detectar un popup específico y qué hacer con él.
 *
 * <p>El usuario registra reglas en {@link PopupGuard} para popups conocidos de
 * su aplicación. Cuando el guard detecta el popup, ejecuta el handler asociado.</p>
 *
 * <p>Se construye via los métodos estáticos de fábrica según la estrategia
 * de detección más conveniente para cada caso.</p>
 *
 * <h3>Ejemplos de uso:</h3>
 * <pre>{@code
 * // Detectar por presencia de un elemento específico
 * PopupRule cookieBanner = PopupRule.byElement(
 *     "Cookie Banner",
 *     By.id("cookie-consent"),
 *     driver -> driver.findElement(By.id("btn-accept-cookies")).click()
 * );
 *
 * // Detectar por texto visible en la página
 * PopupRule sessionWarning = PopupRule.byText(
 *     "Sesión por expirar",
 *     "Su sesión expirará en",
 *     driver -> driver.findElement(By.id("btn-extend-session")).click()
 * );
 *
 * // Detectar por URL (redirección inesperada)
 * PopupRule loginRedirect = PopupRule.byUrl(
 *     "Redirección a login",
 *     "/login",
 *     driver -> { /* lógica de relogin *\/ }
 * );
 * }</pre>
 */
public class PopupRule {

    /** Interfaz funcional para el detector — decide si este popup está presente. */
    @FunctionalInterface
    public interface Detector {
        boolean isPresent(WebDriver driver);
    }

    /** Interfaz funcional para el handler — define qué hacer con el popup. */
    @FunctionalInterface
    public interface Handler {
        void handle(WebDriver driver);
    }

    private final String   name;     // nombre descriptivo para logs y reportes
    private final Detector detector; // lógica de detección
    private final Handler  handler;  // lógica de manejo

    private PopupRule(String name, Detector detector, Handler handler) {
        this.name     = name;
        this.detector = detector;
        this.handler  = handler;
    }

    // -------------------------------------------------------------------------
    // Métodos de fábrica — una por estrategia de detección
    // -------------------------------------------------------------------------

    /**
     * Crea una regla que se activa cuando un elemento específico es visible en pantalla.
     * Es la estrategia más común para modales HTML y banners de cookies.
     *
     * @param name     nombre descriptivo del popup (para logs)
     * @param locator  localizador del elemento que identifica el popup
     * @param handler  acción a ejecutar cuando el popup es detectado
     */
    public static PopupRule byElement(String name, By locator, Handler handler) {
        return new PopupRule(name,
            driver -> {
                try {
                    WebElement el = driver.findElement(locator);
                    return el.isDisplayed();
                } catch (Exception e) {
                    return false; // el elemento no existe — popup no presente
                }
            },
            handler);
    }

    /**
     * Crea una regla que se activa cuando el texto de la página contiene la cadena indicada.
     * Útil para mensajes de error de sistema, advertencias de sesión, o cualquier
     * popup cuyo texto es más estable que sus atributos HTML.
     *
     * @param name    nombre descriptivo del popup
     * @param text    subcadena a buscar en el texto visible de la página
     * @param handler acción a ejecutar cuando el texto es detectado
     */
    public static PopupRule byText(String name, String text, Handler handler) {
        return new PopupRule(name,
            driver -> {
                try {
                    // getText() sobre body extrae todo el texto visible — null-safe
                    String pageText = driver.findElement(By.tagName("body")).getText();
                    return pageText != null && pageText.contains(text);
                } catch (Exception e) {
                    return false;
                }
            },
            handler);
    }

    /**
     * Crea una regla que se activa cuando la URL actual contiene la cadena indicada.
     * Útil para detectar redirecciones inesperadas (ej: redirigido al login,
     * página de error 500, pantalla de mantenimiento).
     *
     * @param name      nombre descriptivo del evento
     * @param urlPart   subcadena que debe aparecer en la URL para activar la regla
     * @param handler   acción a ejecutar cuando la URL coincide
     */
    public static PopupRule byUrl(String name, String urlPart, Handler handler) {
        return new PopupRule(name,
            driver -> driver.getCurrentUrl().contains(urlPart),
            handler);
    }

    /**
     * Crea una regla completamente personalizada con detector y handler propios.
     * Usar cuando las estrategias predefinidas no son suficientes.
     *
     * @param name     nombre descriptivo del popup
     * @param detector lógica custom de detección
     * @param handler  lógica custom de manejo
     */
    public static PopupRule custom(String name, Detector detector, Handler handler) {
        return new PopupRule(name, detector, handler);
    }

    // -------------------------------------------------------------------------
    // Métodos usados por PopupGuard
    // -------------------------------------------------------------------------

    /** @return {@code true} si este popup está actualmente visible/activo */
    public boolean isPresent(WebDriver driver) {
        try {
            return detector.isPresent(driver);
        } catch (Exception e) {
            return false; // si el detector lanza error, asumir que el popup no está
        }
    }

    /** Ejecuta el handler de este popup contra el driver proporcionado. */
    public void handle(WebDriver driver) {
        handler.handle(driver);
    }

    public String getName() { return name; }
}
