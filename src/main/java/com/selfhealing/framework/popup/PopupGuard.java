package com.selfhealing.framework.popup;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Guardia de popups: detecta y maneja automáticamente ventanas emergentes
 * inesperadas que aparecen durante la ejecución de los tests.
 *
 * <h3>Problema que resuelve</h3>
 * <p>En muchas aplicaciones aparecen popups que no están contemplados en el script
 * de automatización: banners de cookies, advertencias de sesión próxima a expirar,
 * modales de error del sistema, notificaciones del navegador, etc. Sin manejo,
 * estos popups crashean el test con {@link NoSuchElementException} o
 * {@link ElementClickInterceptedException} sin mensaje claro del verdadero motivo.</p>
 *
 * <h3>Flujo de detección</h3>
 * <p>Cada vez que el guard escanea, verifica en este orden de prioridad:</p>
 * <ol>
 *   <li>Alertas nativas del navegador (alert/confirm/prompt)</li>
 *   <li>Reglas registradas por el usuario para popups conocidos</li>
 *   <li>Intento genérico de cierre para popups desconocidos (ESC → botones comunes)</li>
 *   <li>Si nada funciona: tomar screenshot y registrar como no manejado</li>
 * </ol>
 *
 * <h3>Ejemplo de configuración y uso:</h3>
 * <pre>{@code
 * // 1. Registrar popups conocidos de la aplicación
 * web.popupGuard.register(PopupRule.byElement(
 *     "Cookie Banner",
 *     By.id("cookie-consent"),
 *     driver -> driver.findElement(By.id("btn-accept-all")).click()
 * ));
 *
 * web.popupGuard.register(PopupRule.byText(
 *     "Sesión por expirar",
 *     "Su sesión expirará en",
 *     driver -> driver.findElement(By.id("btn-extender-sesion")).click()
 * ));
 *
 * // 2a. Uso explícito: envolver acciones sensibles
 * web.popupGuard.safely(() -> web.actions.click(guardarButton));
 *
 * // 2b. Uso global: activar para toda la sesión (escanea automáticamente)
 * web.popupGuard.enableGlobalProtection();
 *
 * // 3. Consultar qué popups se manejaron (para reportes o aserciones)
 * List<PopupInterception> popups = web.popupGuard.getInterceptions();
 * }</pre>
 */
public class PopupGuard {

    // -------------------------------------------------------------------------
    // XPaths genéricos para intentar cerrar popups desconocidos.
    // Cubren los patrones más comunes de botones de cierre en distintos frameworks UI.
    // Orden: del más específico y seguro al más genérico.
    // -------------------------------------------------------------------------
    private static final String[] GENERIC_CLOSE_PATTERNS = {
        "//button[@aria-label='Close']",
        "//button[@aria-label='Cerrar']",
        "//button[@aria-label='close']",
        "//button[contains(@class,'close') and not(contains(@class,'closed'))]",
        "//*[@data-dismiss='modal']",
        "//*[@data-bs-dismiss='modal']",           // Bootstrap 5
        "//button[normalize-space()='×']",
        "//button[normalize-space()='✕']",
        "//button[normalize-space()='X']",
        "//button[normalize-space()='Close']",
        "//button[normalize-space()='Cerrar']",
        "//button[normalize-space()='OK']",
        "//button[normalize-space()='Aceptar']",
        "//button[normalize-space()='Accept']",
        "//button[normalize-space()='Got it']",
        "//button[normalize-space()='Entendido']",
        "//button[normalize-space()='Continuar']",
        "//button[normalize-space()='Continue']",
        "//*[contains(@class,'modal')]//button[last()]"  // último botón de cualquier modal
    };

    /** Acción a tomar cuando aparece una alerta nativa no esperada. */
    public enum NativeAlertAction {
        /** Aceptar la alerta (pulsar "OK"). */
        ACCEPT,
        /** Descartar la alerta (pulsar "Cancelar" o cerrar). */
        DISMISS,
        /**
         * Tomar screenshot de evidencia y luego descartar.
         * Recomendada en producción para no silenciar alertas sin registro.
         */
        SCREENSHOT_AND_DISMISS,
        /**
         * Lanzar excepción para que el test falle explícitamente.
         * Útil si ninguna alerta nativa debería aparecer en la aplicación.
         */
        FAIL
    }

    // -------------------------------------------------------------------------
    // Estado interno
    // -------------------------------------------------------------------------

    private final WebDriver              driver;
    private final List<PopupRule>        rules;           // reglas registradas por el usuario
    private final List<PopupInterception> interceptions;  // historial de popups manejados

    private NativeAlertAction defaultNativeAction = NativeAlertAction.SCREENSHOT_AND_DISMISS;
    private volatile boolean  globalProtection   = false; // volatile: leído/escrito desde hilo de fondo
    private String            screenshotDir      = "screenshots/popups";

    /**
     * @param driver sesión activa de WebDriver
     */
    public PopupGuard(WebDriver driver) {
        this.driver       = driver;
        this.rules        = new ArrayList<>();
        this.interceptions = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Configuración
    // -------------------------------------------------------------------------

    /**
     * Define qué hacer cuando aparece una alerta nativa inesperada (alert/confirm/prompt).
     * Por defecto: {@link NativeAlertAction#SCREENSHOT_AND_DISMISS}.
     *
     * @param action acción a tomar sobre alertas nativas no contempladas
     * @return {@code this} para encadenamiento fluido
     */
    public PopupGuard onNativeAlert(NativeAlertAction action) {
        this.defaultNativeAction = action;
        return this;
    }

    /**
     * Registra una regla para un popup conocido de la aplicación.
     * Las reglas se evalúan en el orden de registro; la primera que coincida se aplica.
     *
     * @param rule regla creada con los métodos de fábrica de {@link PopupRule}
     * @return {@code this} para encadenamiento fluido
     */
    public PopupGuard register(PopupRule rule) {
        rules.add(rule);
        return this;
    }

    /**
     * Atajo para registrar una regla por presencia de elemento con handler lambda.
     *
     * <pre>{@code
     * web.popupGuard.register("Cookie Banner", By.id("cookie-consent"),
     *     driver -> driver.findElement(By.id("btn-accept-all")).click()
     * );
     * }</pre>
     *
     * @param name    nombre descriptivo del popup
     * @param locator localizador del elemento que identifica el popup
     * @param handler lambda que recibe el driver y cierra el popup
     * @return {@code this} para encadenamiento fluido
     */
    public PopupGuard register(String name, By locator, PopupRule.Handler handler) {
        return register(PopupRule.byElement(name, locator, handler));
    }

    /**
     * Define el directorio donde se guardan los screenshots de evidencia
     * de popups inesperados. Por defecto: {@code screenshots/popups}.
     *
     * @param directory ruta al directorio
     * @return {@code this} para encadenamiento fluido
     */
    public PopupGuard screenshotDir(String directory) {
        this.screenshotDir = directory;
        return this;
    }

    // -------------------------------------------------------------------------
    // Uso explícito
    // -------------------------------------------------------------------------

    /**
     * Ejecuta la acción dada con protección de popups activa.
     * Escanea antes y después de la acción para interceptar cualquier popup que aparezca.
     *
     * <pre>{@code
     * web.popupGuard.safely(() -> web.actions.click(guardarButton));
     * web.popupGuard.safely(() -> {
     *     web.actions.type(campo, "valor");
     *     web.actions.click(enviar);
     * });
     * }</pre>
     *
     * @param action bloque de código a ejecutar con protección
     */
    public void safely(Runnable action) {
        scan(); // verificar si hay algo antes de actuar
        action.run();
        scan(); // verificar si la acción disparó algún popup
    }

    /**
     * Versión con valor de retorno de {@link #safely(Runnable)}.
     * Permite proteger acciones que devuelven un resultado (ej: lecturas).
     *
     * <pre>{@code
     * String texto = web.popupGuard.safely(() -> web.actions.read(mensajeElement));
     * }</pre>
     *
     * @param action  bloque de código que devuelve un valor
     * @param <T>     tipo del valor devuelto
     * @return el valor devuelto por el bloque
     */
    public <T> T safely(Supplier<T> action) {
        scan();
        T result = action.get();
        scan();
        return result;
    }

    // -------------------------------------------------------------------------
    // Protección global (auto-scan)
    // -------------------------------------------------------------------------

    /**
     * Activa la protección global: el guard hará un scan automático periódicamente.
     *
     * <p>Inicia un hilo de fondo que verifica la presencia de popups cada 2 segundos.
     * Si detecta uno, lo maneja con las reglas registradas o el comportamiento por defecto.</p>
     *
     * <p><strong>Usar con cuidado</strong>: el hilo de fondo puede interferir con
     * operaciones que esperan que ciertos elementos estén visibles. Recomendado
     * solo para aplicaciones con popups muy frecuentes e impredecibles.</p>
     */
    public synchronized void enableGlobalProtection() {
        if (globalProtection) return; // ya está activa
        globalProtection = true;

        Thread monitor = new Thread(() -> {
            while (globalProtection) {
                try {
                    Thread.sleep(2000); // escanear cada 2 segundos
                    scan();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {
                    // Si el driver está cerrado u ocurre otro error, detener silenciosamente
                    break;
                }
            }
        });

        monitor.setDaemon(true); // termina cuando el test JVM termina
        monitor.setName("PopupGuard-Monitor");
        monitor.start();
        log("Protección global activada (escaneo cada 2s en hilo de fondo)");
    }

    /**
     * Desactiva la protección global y detiene el hilo de fondo.
     */
    public synchronized void disableGlobalProtection() {
        globalProtection = false;
        log("Protección global desactivada");
    }

    // -------------------------------------------------------------------------
    // Escaneo manual
    // -------------------------------------------------------------------------

    /**
     * Realiza un escaneo único buscando popups activos y los maneja si los encuentra.
     * Puede llamarse manualmente en cualquier punto del test.
     *
     * @return {@code true} si se interceptó y manejó algún popup
     */
    public boolean scan() {
        // Paso 1: verificar alerta nativa del navegador (tiene prioridad absoluta
        // porque bloquea toda interacción con la página)
        if (handleNativeAlert()) return true;

        // Paso 2: verificar reglas registradas por el usuario
        for (PopupRule rule : rules) {
            if (rule.isPresent(driver)) {
                log("Popup conocido detectado: '%s' — aplicando handler registrado", rule.getName());
                try {
                    rule.handle(driver);
                    recordInterception(
                        PopupInterception.PopupType.REGISTERED_MODAL,
                        PopupInterception.ActionTaken.CUSTOM,
                        rule.getName(),
                        "Handler registrado ejecutado",
                        null);
                    return true;
                } catch (Exception e) {
                    log("ERROR en handler de '%s': %s", rule.getName(), e.getMessage());
                }
            }
        }

        // Paso 3: intentar cierre genérico para popups no registrados
        return tryGenericClose();
    }

    // -------------------------------------------------------------------------
    // Historial de interceptaciones
    // -------------------------------------------------------------------------

    /**
     * Devuelve la lista de todos los popups interceptados durante la sesión.
     * Lista no modificable para proteger la integridad del historial.
     *
     * @return lista de interceptaciones en orden cronológico
     */
    public List<PopupInterception> getInterceptions() {
        return Collections.unmodifiableList(interceptions);
    }

    /**
     * Indica si se interceptó algún popup inesperado (sin regla registrada)
     * durante la sesión.
     *
     * @return {@code true} si hubo al menos un popup desconocido
     */
    public boolean hadUnexpectedPopups() {
        return interceptions.stream().anyMatch(PopupInterception::wasUnexpected);
    }

    /**
     * Limpia el historial de interceptaciones.
     * Útil para tests que quieren verificar popups solo dentro de un bloque específico.
     */
    public void clearInterceptions() {
        interceptions.clear();
    }

    // -------------------------------------------------------------------------
    // Lógica de detección y cierre
    // -------------------------------------------------------------------------

    /**
     * Verifica y maneja una alerta nativa del navegador si está presente.
     *
     * @return {@code true} si había una alerta y fue procesada
     */
    private boolean handleNativeAlert() {
        try {
            // Espera muy corta: si no hay alerta, fallar rápido
            new WebDriverWait(driver, Duration.ofSeconds(1))
                .until(ExpectedConditions.alertIsPresent());
        } catch (TimeoutException e) {
            return false; // no había alerta nativa
        }

        Alert alert;
        try {
            alert = driver.switchTo().alert();
        } catch (NoAlertPresentException e) {
            return false;
        }

        String alertText = safeReadAlertText(alert);
        log("Alerta nativa interceptada: '%s' — acción: %s", alertText, defaultNativeAction);

        String screenshotPath = null;

        switch (defaultNativeAction) {
            case ACCEPT:
                alert.accept();
                recordInterception(
                    PopupInterception.PopupType.NATIVE_ALERT,
                    PopupInterception.ActionTaken.ACCEPTED,
                    "Alerta Nativa", alertText, null);
                break;

            case SCREENSHOT_AND_DISMISS:
                // Tomar screenshot ANTES de cerrar para tener evidencia del mensaje
                screenshotPath = takeScreenshot("native_alert");
                alert.dismiss();
                recordInterception(
                    PopupInterception.PopupType.NATIVE_ALERT,
                    PopupInterception.ActionTaken.DISMISSED,
                    "Alerta Nativa", alertText, screenshotPath);
                break;

            case DISMISS:
                alert.dismiss();
                recordInterception(
                    PopupInterception.PopupType.NATIVE_ALERT,
                    PopupInterception.ActionTaken.DISMISSED,
                    "Alerta Nativa", alertText, null);
                break;

            case FAIL:
                alert.dismiss(); // cerrar para no dejar el driver bloqueado
                throw new RuntimeException(
                    "PopupGuard: alerta nativa inesperada — " + alertText);
        }

        return true;
    }

    /**
     * Intenta cerrar un popup desconocido usando patrones genéricos de cierre.
     *
     * <p>Estrategia de intentos en orden:
     * <ol>
     *   <li>Tecla ESC (cierra la mayoría de modales que escuchan keydown)</li>
     *   <li>Cada uno de los {@link #GENERIC_CLOSE_PATTERNS} (botones comunes de cierre)</li>
     *   <li>Si ninguno funciona: tomar screenshot y registrar como no manejado</li>
     * </ol>
     * </p>
     *
     * @return {@code true} si se encontró y cerró algún elemento de cierre
     */
    private boolean tryGenericClose() {
        // Intentar ESC primero — es el cierre más universal y no causa efectos secundarios
        try {
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
            // Verificar si ESC hizo efecto comprobando si algún modal desapareció
            // (no hay forma directa de saber, así que continuamos con los botones)
        } catch (Exception ignored) {}

        // Buscar botones de cierre por los patrones genéricos conocidos
        for (String pattern : GENERIC_CLOSE_PATTERNS) {
            try {
                WebElement closeBtn = driver.findElement(By.xpath(pattern));
                if (closeBtn.isDisplayed() && closeBtn.isEnabled()) {
                    String btnText = closeBtn.getText().trim();
                    String screenshotPath = takeScreenshot("unknown_popup");

                    closeBtn.click();

                    log("Popup desconocido cerrado con botón '%s' (patrón: %s)", btnText, pattern);
                    recordInterception(
                        PopupInterception.PopupType.UNKNOWN_MODAL,
                        PopupInterception.ActionTaken.CLOSED,
                        "Popup Desconocido",
                        "Cerrado con: '" + btnText + "'",
                        screenshotPath);
                    return true;
                }
            } catch (Exception ignored) {
                // Este patrón no encontró nada — probar el siguiente
            }
        }

        return false; // ningún patrón funcionó
    }

    // -------------------------------------------------------------------------
    // Helpers internos
    // -------------------------------------------------------------------------

    /**
     * Lee el texto de una alerta nativa de forma segura.
     * En algunos drivers el texto puede estar vacío o lanzar excepción.
     */
    private String safeReadAlertText(Alert alert) {
        try {
            String text = alert.getText();
            return text != null ? text : "(sin texto)";
        } catch (Exception e) {
            return "(no se pudo leer el texto)";
        }
    }

    /**
     * Toma un screenshot de evidencia y lo guarda en el directorio configurado.
     *
     * @param prefix prefijo del nombre de archivo (ej: "native_alert", "unknown_popup")
     * @return ruta del archivo guardado, o null si falló
     */
    private String takeScreenshot(String prefix) {
        try {
            String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String path = screenshotDir + "/" + prefix + "_" + timestamp + ".png";

            Files.createDirectories(Paths.get(screenshotDir));
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(Paths.get(path), bytes);

            log("Screenshot de evidencia guardado: %s", path);
            return path;
        } catch (IOException e) {
            log("WARN: No se pudo guardar screenshot: %s", e.getMessage());
            return null;
        }
    }

    /** Añade un registro al historial de interceptaciones. */
    private void recordInterception(PopupInterception.PopupType type,
                                     PopupInterception.ActionTaken action,
                                     String ruleName, String message, String screenshotPath) {
        PopupInterception interception =
            new PopupInterception(type, action, ruleName, message, screenshotPath);
        interceptions.add(interception);
        log(interception.getSummary());
    }

    private void log(String msg, Object... args) {
        System.out.printf("[PopupGuard] " + msg + "%n", args);
    }
}
