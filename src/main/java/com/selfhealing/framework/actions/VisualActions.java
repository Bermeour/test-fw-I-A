package com.selfhealing.framework.actions;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Acciones visuales: resaltado de elementos y capturas de pantalla.
 *
 * <p>El resaltado se hace via JavaScript porque Selenium no tiene API nativa
 * para modificar estilos de elementos. Estas acciones son especialmente
 * valiosas para:</p>
 * <ul>
 *   <li>Demos en vivo — resaltar elementos a medida que se interactúa con ellos</li>
 *   <li>Debugging — confirmar visualmente qué elemento encontró el framework</li>
 *   <li>Reportes de pruebas — adjuntar screenshots como evidencia de paso/fallo</li>
 * </ul>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * web.actions.visual.highlight(loginButton);
 * web.actions.visual.highlightSuccess(confirmedField);
 * web.actions.visual.blink(targetElement, 3);
 * web.actions.visual.screenshotToFile(null);    // guarda con nombre automático
 * }</pre>
 */
public class VisualActions {

    // Estilos de borde predefinidos (formato CSS shorthand: grosor tipo color)
    private static final String BORDER_DEFAULT = "3px solid #FF4500"; // naranja-rojo
    private static final String BORDER_SUCCESS = "3px solid #00C851"; // verde
    private static final String BORDER_ERROR   = "3px solid #FF4444"; // rojo
    private static final String BORDER_INFO    = "3px solid #33B5E5"; // azul

    private final WebDriver driver;
    private final Waits     waits;

    /**
     * @param driver sesión activa de WebDriver
     * @param waits  operaciones de espera compartidas
     */
    public VisualActions(WebDriver driver, Waits waits) {
        this.driver = driver;
        this.waits  = waits;
    }

    // -------------------------------------------------------------------------
    // Resaltado de elementos
    // -------------------------------------------------------------------------

    /**
     * Dibuja un borde naranja-rojo alrededor del elemento.
     * Resaltado de propósito general para marcado visual en demos y debugging.
     *
     * @param element elemento a resaltar
     */
    public void highlight(Element element) {
        highlight(element, BORDER_DEFAULT);
    }

    /**
     * Dibuja un borde con el estilo CSS indicado.
     *
     * <p>Valores de ejemplo: {@code "3px solid red"}, {@code "2px dashed #0078d4"}</p>
     *
     * @param element     elemento a resaltar
     * @param borderStyle valor CSS de borde (grosor, estilo, color)
     */
    public void highlight(Element element, String borderStyle) {
        WebElement el = waits.untilVisible(element);
        js().executeScript("arguments[0].style.border = arguments[1]", el, borderStyle);
    }

    /**
     * Resalta el elemento con borde verde para indicar un estado exitoso.
     * Usar después de que un campo fue llenado correctamente o una condición verificada.
     *
     * @param element elemento a marcar como exitoso
     */
    public void highlightSuccess(Element element) {
        highlight(element, BORDER_SUCCESS);
    }

    /**
     * Resalta el elemento con borde rojo para indicar un error o problema.
     * Usar para llamar la atención sobre el elemento que causó un fallo.
     *
     * @param element elemento a marcar como erróneo
     */
    public void highlightError(Element element) {
        highlight(element, BORDER_ERROR);
    }

    /**
     * Resalta el elemento con borde azul para marcado informativo.
     *
     * @param element elemento a marcar informativamente
     */
    public void highlightInfo(Element element) {
        highlight(element, BORDER_INFO);
    }

    /**
     * Resalta el elemento durante el tiempo indicado y luego quita el borde.
     * Ideal para demos donde se quiere llamar la atención momentáneamente.
     *
     * @param element      elemento a resaltar temporalmente
     * @param durationMs   duración en milisegundos del resaltado (ej: 1500)
     */
    public void highlightTemporary(Element element, int durationMs) {
        highlight(element);
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        removeHighlight(element);
    }

    /**
     * Elimina cualquier borde previamente aplicado al elemento.
     *
     * @param element elemento cuyo borde debe eliminarse
     */
    public void removeHighlight(Element element) {
        // Re-buscar el elemento por si fue re-renderizado después del resaltado
        WebElement el = driver.findElement(element.toBy());
        js().executeScript("arguments[0].style.border = ''", el);
    }

    /**
     * Hace parpadear el elemento alternando entre resaltado y sin borde.
     * Efectivo para llamar la atención durante demos en vivo o grabaciones.
     *
     * @param element elemento a hacer parpadear
     * @param times   número de ciclos de parpadeo (un ciclo = encendido + apagado)
     */
    public void blink(Element element, int times) {
        WebElement el = waits.untilVisible(element);
        for (int i = 0; i < times; i++) {
            js().executeScript("arguments[0].style.border = '" + BORDER_DEFAULT + "'", el);
            try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            js().executeScript("arguments[0].style.border = ''", el);
            try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /**
     * Hace parpadear el elemento 3 veces con el color de resaltado por defecto.
     *
     * @param element elemento a hacer parpadear
     */
    public void blink(Element element) {
        blink(element, 3);
    }

    // -------------------------------------------------------------------------
    // Capturas de pantalla
    // -------------------------------------------------------------------------

    /**
     * Captura el viewport completo del navegador como array de bytes PNG.
     *
     * @return bytes PNG crudos de la captura de pantalla
     */
    public byte[] screenshot() {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    /**
     * Captura solo el bounding box del elemento especificado como array de bytes PNG.
     * Más enfocado que una captura de página completa para evidencia a nivel de elemento.
     *
     * @param element elemento a capturar
     * @return bytes PNG crudos de la captura del elemento
     */
    public byte[] screenshotElement(Element element) {
        WebElement el = waits.untilVisible(element);
        return el.getScreenshotAs(OutputType.BYTES);
    }

    /**
     * Captura el viewport completo, lo guarda con nombre automático en
     * {@code ./screenshots/page_yyyyMMdd_HHmmss.png} y devuelve la ruta.
     *
     * <pre>{@code
     * String ruta = web.actions.visual.screenshotToFile();
     * System.out.println("Foto guardada en: " + ruta);
     * }</pre>
     *
     * @return ruta del archivo guardado
     */
    public String screenshotToFile() {
        String path = buildDefaultPath("page");
        writeFile(path, screenshot());
        log("Screenshot guardado: %s", path);
        return path;
    }

    /**
     * Captura el viewport completo y lo guarda en la ruta indicada.
     *
     * @param filePath ruta absoluta o relativa del archivo de salida
     * @return la ruta del archivo guardado
     */
    public String screenshotToFile(String filePath) {
        writeFile(filePath, screenshot());
        log("Screenshot guardado: %s", filePath);
        return filePath;
    }

    /**
     * Captura solo el elemento especificado y lo guarda en un archivo.
     *
     * <p>Si {@code filePath} es {@code null}, el archivo se nombra con la etiqueta
     * del elemento más un sufijo de timestamp.</p>
     *
     * @param element  elemento a capturar
     * @param filePath ruta del archivo de salida, o {@code null} para nombre automático
     * @return la ruta del archivo guardado
     */
    public String screenshotElementToFile(Element element, String filePath) {
        String name = element.getDisplayLabel().replaceAll("\\s+", "_");
        String path = (filePath != null) ? filePath : buildDefaultPath(name);
        writeFile(path, screenshotElement(element));
        log("Screenshot de elemento guardado: %s", path);
        return path;
    }

    // -------------------------------------------------------------------------
    // Helpers internos
    // -------------------------------------------------------------------------

    /**
     * Construye la ruta por defecto en {@code ./screenshots/} con timestamp,
     * creando el directorio si no existe.
     */
    private String buildDefaultPath(String name) {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "screenshots/" + name + "_" + timestamp + ".png";
    }

    /** Escribe los bytes en el archivo, creando los directorios padres si es necesario. */
    private void writeFile(String path, byte[] bytes) {
        try {
            if (Paths.get(path).getParent() != null) {
                Files.createDirectories(Paths.get(path).getParent());
            }
            Files.write(Paths.get(path), bytes);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el screenshot en: " + path, e);
        }
    }

    private JavascriptExecutor js() {
        return (JavascriptExecutor) driver;
    }

    private void log(String msg, Object... args) {
        System.out.printf("[VisualActions] " + msg + "%n", args);
    }
}
