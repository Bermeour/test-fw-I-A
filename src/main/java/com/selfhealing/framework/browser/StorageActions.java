package com.selfhealing.framework.browser;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestión de {@code localStorage} y {@code sessionStorage} del navegador via JavaScript.
 *
 * <p>Expone dos instancias: {@code local} para {@code localStorage}
 * y {@code session} para {@code sessionStorage}.</p>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * // localStorage
 * web.storage.local.set("theme", "dark");
 * String theme = web.storage.local.get("theme");
 * web.storage.local.remove("theme");
 * web.storage.local.clear();
 *
 * // sessionStorage
 * web.storage.session.set("step", "checkout");
 * boolean exists = web.storage.session.hasKey("step");
 * int count = web.storage.session.size();
 *
 * // Volcar todo el storage para debugging
 * Map<String, String> all = web.storage.local.getAll();
 * }</pre>
 */
public class StorageActions {

    /** Acceso a {@code window.localStorage}. */
    public final Storage local;

    /** Acceso a {@code window.sessionStorage}. */
    public final Storage session;

    /**
     * Constructor invocado por {@link com.selfhealing.framework.Web}.
     *
     * @param driver sesión activa de WebDriver
     */
    public StorageActions(WebDriver driver) {
        this.local   = new Storage(driver, "localStorage");
        this.session = new Storage(driver, "sessionStorage");
    }

    // =========================================================================
    // Clase interna — un Storage (local o session)
    // =========================================================================

    /**
     * Operaciones sobre un tipo específico de Web Storage ({@code localStorage}
     * o {@code sessionStorage}).
     */
    public static class Storage {

        private final JavascriptExecutor js;
        private final String             storageType; // "localStorage" o "sessionStorage"

        Storage(WebDriver driver, String storageType) {
            this.js          = (JavascriptExecutor) driver;
            this.storageType = storageType;
        }

        // -------------------------------------------------------------------------
        // Escritura
        // -------------------------------------------------------------------------

        /**
         * Establece la clave con el valor dado, creándola si no existe.
         *
         * @param key   clave del item
         * @param value valor a guardar (siempre String en Web Storage)
         */
        public void set(String key, String value) {
            js.executeScript(
                String.format("window.%s.setItem(arguments[0], arguments[1])", storageType),
                key, value);
        }

        /**
         * Establece múltiples claves a la vez desde un mapa.
         *
         * <pre>{@code
         * web.storage.local.setAll(Map.of("a", "1", "b", "2"));
         * }</pre>
         *
         * @param items mapa de clave → valor
         */
        public void setAll(Map<String, String> items) {
            items.forEach(this::set);
        }

        // -------------------------------------------------------------------------
        // Lectura
        // -------------------------------------------------------------------------

        /**
         * Devuelve el valor asociado a la clave.
         *
         * @param key clave del item
         * @return el valor como String, o {@code null} si la clave no existe
         */
        public String get(String key) {
            Object result = js.executeScript(
                String.format("return window.%s.getItem(arguments[0])", storageType),
                key);
            return result != null ? result.toString() : null;
        }

        /**
         * Devuelve todos los pares clave-valor del storage como mapa.
         * Útil para inspección durante debugging.
         *
         * @return mapa con todos los items (puede estar vacío)
         */
        @SuppressWarnings("unchecked")
        public Map<String, String> getAll() {
            // Serializar el storage completo a JSON desde JS y reconstruir en Java
            Object raw = js.executeScript(
                String.format(
                    "var s = window.%s; var m = {};" +
                    "for (var i = 0; i < s.length; i++) {" +
                    "  var k = s.key(i); m[k] = s.getItem(k);" +
                    "} return m;",
                    storageType));

            Map<String, String> result = new LinkedHashMap<>();
            if (raw instanceof Map) {
                ((Map<?, ?>) raw).forEach((k, v) ->
                    result.put(String.valueOf(k), v != null ? String.valueOf(v) : null));
            }
            return result;
        }

        // -------------------------------------------------------------------------
        // Verificación
        // -------------------------------------------------------------------------

        /**
         * Indica si la clave existe en el storage.
         *
         * @param key clave a verificar
         * @return {@code true} si la clave existe
         */
        public boolean hasKey(String key) {
            Object result = js.executeScript(
                String.format("return window.%s.getItem(arguments[0]) !== null", storageType),
                key);
            return Boolean.TRUE.equals(result);
        }

        /**
         * Número de items almacenados.
         *
         * @return cantidad de claves en el storage
         */
        public int size() {
            Object result = js.executeScript(
                String.format("return window.%s.length", storageType));
            return result instanceof Long ? ((Long) result).intValue() : 0;
        }

        // -------------------------------------------------------------------------
        // Eliminación
        // -------------------------------------------------------------------------

        /**
         * Elimina la clave indicada. No hace nada si no existe.
         *
         * @param key clave a eliminar
         */
        public void remove(String key) {
            js.executeScript(
                String.format("window.%s.removeItem(arguments[0])", storageType),
                key);
        }

        /**
         * Elimina todos los items del storage.
         * Equivalente a {@code localStorage.clear()} en JavaScript.
         */
        public void clear() {
            js.executeScript(String.format("window.%s.clear()", storageType));
        }

        @Override
        public String toString() {
            return "Storage[" + storageType + "] size=" + size();
        }
    }
}
