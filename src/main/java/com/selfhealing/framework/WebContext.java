package com.selfhealing.framework;

import org.openqa.selenium.WebDriver;

/**
 * Almacén ThreadLocal de la sesión {@link Web} activa en el hilo actual.
 *
 * <p>Permite que extensiones y utilidades (screenshots, listeners, page objects)
 * accedan al driver sin necesitar una referencia directa a la clase de test,
 * lo que es esencial para ejecución paralela donde cada hilo tiene su propio browser.</p>
 *
 * <h3>Ciclo de vida (gestionado por BaseTest):</h3>
 * <pre>
 * @BeforeEach → WebContext.set(web)
 * @AfterEach  → WebContext.remove()
 * </pre>
 *
 * <h3>Uso en extensiones / page objects:</h3>
 * <pre>{@code
 * // En una extensión JUnit 5
 * WebDriver driver = WebContext.driver();
 *
 * // En un page object
 * Web web = WebContext.get();
 * web.actions.click(miElemento);
 * }</pre>
 *
 * <h3>Thread safety:</h3>
 * <p>Cada hilo de JUnit tiene su propia entrada en el ThreadLocal.
 * Con la configuración PER_METHOD (por defecto en JUnit 5), cada método de test
 * corre en un hilo propio → cada hilo tiene su propio browser → sin conflictos.</p>
 */
public final class WebContext {

    private static final ThreadLocal<Web> HOLDER = new ThreadLocal<>();

    private WebContext() {}

    /**
     * Registra la instancia {@link Web} del hilo actual.
     * Llamar en {@code @BeforeEach} inmediatamente tras crear el Web.
     *
     * @param web instancia activa para este hilo
     */
    public static void set(Web web) {
        HOLDER.set(web);
    }

    /**
     * Devuelve la instancia {@link Web} registrada en el hilo actual.
     *
     * @return instancia activa, o {@code null} si no se ha registrado ninguna
     */
    public static Web get() {
        return HOLDER.get();
    }

    /**
     * Elimina la referencia del hilo actual.
     * Llamar siempre en {@code @AfterEach} para evitar memory leaks en pools de hilos.
     */
    public static void remove() {
        HOLDER.remove();
    }

    /**
     * Atajo para obtener el {@link WebDriver} del hilo actual.
     *
     * @return driver activo, o {@code null} si no hay sesión registrada
     */
    public static WebDriver driver() {
        Web web = HOLDER.get();
        return web != null ? web.driver : null;
    }
}