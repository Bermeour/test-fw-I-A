package com.selfhealing.framework.browser;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;

import java.util.Map;
import java.util.Set;

/**
 * Gestión de cookies del navegador.
 *
 * <p>Permite leer, escribir y eliminar cookies sin necesidad de acceder
 * directamente a {@code driver.manage().getCookies()}.</p>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * // Establecer una cookie simple
 * web.cookies.set("session_token", "abc123");
 *
 * // Leer el valor
 * String token = web.cookies.get("session_token");
 *
 * // Verificar si existe
 * assertTrue(web.cookies.exists("session_token"));
 *
 * // Establecer varias de una vez
 * web.cookies.setAll(Map.of(
 *     "lang",   "es",
 *     "theme",  "dark"
 * ));
 *
 * // Eliminar una cookie o todas
 * web.cookies.delete("session_token");
 * web.cookies.deleteAll();
 * }</pre>
 */
public class CookieActions {

    private final WebDriver driver;

    /**
     * Constructor invocado por {@link com.selfhealing.framework.Web}.
     *
     * @param driver sesión activa de WebDriver
     */
    public CookieActions(WebDriver driver) {
        this.driver = driver;
    }

    // -------------------------------------------------------------------------
    // Escritura
    // -------------------------------------------------------------------------

    /**
     * Crea o reemplaza una cookie con nombre y valor.
     * El dominio, path y expiración quedan con los valores por defecto del navegador.
     *
     * @param name  nombre de la cookie
     * @param value valor de la cookie
     */
    public void set(String name, String value) {
        driver.manage().addCookie(new Cookie(name, value));
    }

    /**
     * Crea o reemplaza una cookie usando el objeto {@link Cookie} de Selenium.
     * Usar cuando se necesita control total sobre dominio, path y expiración.
     *
     * @param cookie objeto cookie de Selenium
     */
    public void set(Cookie cookie) {
        driver.manage().addCookie(cookie);
    }

    /**
     * Crea o reemplaza múltiples cookies de una vez desde un mapa nombre→valor.
     *
     * <pre>{@code
     * web.cookies.setAll(Map.of(
     *     "lang",  "es",
     *     "theme", "dark"
     * ));
     * }</pre>
     *
     * @param cookies mapa de nombre → valor
     */
    public void setAll(Map<String, String> cookies) {
        cookies.forEach(this::set);
    }

    // -------------------------------------------------------------------------
    // Lectura
    // -------------------------------------------------------------------------

    /**
     * Devuelve el valor de la cookie con el nombre indicado.
     *
     * @param name nombre de la cookie
     * @return valor de la cookie, o {@code null} si no existe
     */
    public String get(String name) {
        Cookie cookie = driver.manage().getCookieNamed(name);
        return cookie != null ? cookie.getValue() : null;
    }

    /**
     * Devuelve el objeto {@link Cookie} completo de Selenium para la cookie indicada.
     * Útil para inspeccionar dominio, path, expiración, httpOnly, secure.
     *
     * @param name nombre de la cookie
     * @return objeto Cookie, o {@code null} si no existe
     */
    public Cookie getCookie(String name) {
        return driver.manage().getCookieNamed(name);
    }

    /**
     * Devuelve todas las cookies de la sesión actual como conjunto.
     *
     * @return conjunto inmutable de cookies
     */
    public Set<Cookie> getAll() {
        return driver.manage().getCookies();
    }

    // -------------------------------------------------------------------------
    // Verificación
    // -------------------------------------------------------------------------

    /**
     * Indica si existe una cookie con el nombre dado en la sesión actual.
     *
     * @param name nombre de la cookie
     * @return {@code true} si la cookie existe
     */
    public boolean exists(String name) {
        return driver.manage().getCookieNamed(name) != null;
    }

    // -------------------------------------------------------------------------
    // Eliminación
    // -------------------------------------------------------------------------

    /**
     * Elimina la cookie con el nombre indicado. No hace nada si no existe.
     *
     * @param name nombre de la cookie a eliminar
     */
    public void delete(String name) {
        driver.manage().deleteCookieNamed(name);
    }

    /**
     * Elimina todas las cookies de la sesión actual.
     * Útil para limpiar el estado entre tests sin reiniciar el browser.
     */
    public void deleteAll() {
        driver.manage().deleteAllCookies();
    }
}
