package com.selfhealing.framework.actions;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Acciones de navegación del navegador: URLs, historial, pestañas e iframes.
 *
 * <p>Los iframes requieren manejo especial porque el contexto de WebDriver
 * está restringido a un único frame a la vez. Después de llamar a
 * {@link #switchToFrame}, todas las búsquedas de elementos se dirigen al DOM
 * del iframe hasta que se llame {@link #switchToDefaultContent}.</p>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * web.actions.navigate.to("http://mi-app.com/dashboard");
 * web.actions.navigate.openInNewTab("http://mi-app.com/ayuda");
 * web.actions.navigate.switchToTab(1);
 * web.actions.navigate.switchToFrame(contentFrame);
 * web.actions.navigate.switchToDefaultContent();
 * }</pre>
 */
public class NavigationActions {

    private final WebDriver driver;
    private final Waits     waits;

    /**
     * @param driver sesión activa de WebDriver
     * @param waits  operaciones de espera compartidas
     */
    public NavigationActions(WebDriver driver, Waits waits) {
        this.driver = driver;
        this.waits  = waits;
    }

    // -------------------------------------------------------------------------
    // Navegación básica
    // -------------------------------------------------------------------------

    /**
     * Navega a la URL indicada y espera a que la página esté lista.
     *
     * @param url URL completa a la que navegar
     */
    public void to(String url) {
        driver.get(url);
        waits.untilPageReady();
    }

    /**
     * Retrocede un paso en el historial de navegación del navegador.
     */
    public void back() {
        driver.navigate().back();
        waits.untilPageReady();
    }

    /**
     * Avanza un paso en el historial de navegación del navegador.
     */
    public void forward() {
        driver.navigate().forward();
        waits.untilPageReady();
    }

    /**
     * Recarga la página actual y espera a que termine de cargar.
     */
    public void refresh() {
        driver.navigate().refresh();
        waits.untilPageReady();
    }

    /**
     * Devuelve la URL actual mostrada en la barra de direcciones del navegador.
     *
     * @return URL actual
     */
    public String getUrl() {
        return driver.getCurrentUrl();
    }

    /**
     * Devuelve el título de la página actual (contenido del tag {@code <title>}).
     *
     * @return título de la página
     */
    public String getTitle() {
        return driver.getTitle();
    }

    // -------------------------------------------------------------------------
    // Gestión de pestañas
    // -------------------------------------------------------------------------

    /**
     * Abre la URL indicada en una nueva pestaña del navegador y transfiere
     * el foco del driver a esa nueva pestaña.
     *
     * @param url URL a abrir en la nueva pestaña
     */
    public void openInNewTab(String url) {
        ((JavascriptExecutor) driver).executeScript("window.open(arguments[0], '_blank')", url);
        // Cambiar al handle de la ventana más recientemente abierta
        List<String> handles = new ArrayList<>(driver.getWindowHandles());
        driver.switchTo().window(handles.get(handles.size() - 1));
        waits.untilPageReady();
    }

    /**
     * Cambia el foco del driver a la pestaña en el índice base-cero indicado.
     * Las pestañas se ordenan según el orden en que fueron abiertas.
     *
     * @param index índice base-cero de la pestaña (0 = primera pestaña abierta)
     * @throws RuntimeException si el índice supera el número de pestañas abiertas
     */
    public void switchToTab(int index) {
        List<String> handles = new ArrayList<>(driver.getWindowHandles());
        if (index >= handles.size()) {
            throw new RuntimeException(
                "Solo hay " + handles.size() + " pestaña(s) abiertas; se solicitó índice " + index);
        }
        driver.switchTo().window(handles.get(index));
        waits.untilPageReady();
    }

    /**
     * Cambia el foco del driver a la primera pestaña cuyo título contenga
     * la subcadena indicada. Útil cuando el orden de pestañas no es predecible.
     *
     * @param titlePart subcadena del título de la pestaña destino (sensible a mayúsculas)
     * @throws RuntimeException si no se encuentra ninguna pestaña con ese título
     */
    public void switchToTabByTitle(String titlePart) {
        String originalHandle = driver.getWindowHandle();
        for (String handle : driver.getWindowHandles()) {
            driver.switchTo().window(handle);
            if (driver.getTitle().contains(titlePart)) {
                waits.untilPageReady();
                return;
            }
        }
        // Restaurar la pestaña original antes de lanzar la excepción
        driver.switchTo().window(originalHandle);
        throw new RuntimeException("No se encontró pestaña con título que contenga: '" + titlePart + "'");
    }

    /**
     * Cierra la pestaña actualmente en foco y transfiere el foco a la anterior.
     * No tiene efecto si solo hay una pestaña abierta (terminaría la sesión).
     */
    public void closeCurrentTab() {
        driver.close();
        List<String> handles = new ArrayList<>(driver.getWindowHandles());
        if (!handles.isEmpty()) {
            driver.switchTo().window(handles.get(handles.size() - 1));
        }
    }

    /**
     * Devuelve el número de pestañas del navegador actualmente abiertas.
     *
     * @return cantidad de pestañas abiertas
     */
    public int getTabCount() {
        return driver.getWindowHandles().size();
    }

    // -------------------------------------------------------------------------
    // Gestión de iframes
    // -------------------------------------------------------------------------

    /**
     * Cambia el contexto del WebDriver al iframe identificado por el elemento.
     *
     * <p><strong>Importante:</strong> Después de llamar este método, todas las
     * búsquedas de elementos se restringen al DOM del iframe. Siempre llamar
     * {@link #switchToDefaultContent()} al terminar de trabajar en el iframe.</p>
     *
     * @param frameElement elemento que identifica el tag {@code <iframe>}
     */
    public void switchToFrame(Element frameElement) {
        WebElement frame = waits.untilVisible(frameElement);
        driver.switchTo().frame(frame);
    }

    /**
     * Cambia el contexto al iframe en el índice base-cero indicado.
     * Los índices siguen el orden del DOM de todos los elementos {@code <iframe>}.
     *
     * @param index índice base-cero del iframe
     */
    public void switchToFrame(int index) {
        driver.switchTo().frame(index);
    }

    /**
     * Cambia el contexto al iframe con el atributo {@code name} o {@code id} indicado.
     *
     * @param nameOrId valor del atributo {@code name} o {@code id} del iframe
     */
    public void switchToFrame(String nameOrId) {
        driver.switchTo().frame(nameOrId);
    }

    /**
     * Devuelve el contexto del WebDriver al documento principal, saliendo de todos los iframes.
     * Llamar siempre después de terminar el trabajo dentro de un iframe.
     */
    public void switchToDefaultContent() {
        driver.switchTo().defaultContent();
    }

    /**
     * Sube el contexto un nivel — de un iframe anidado al iframe padre.
     * Usar con iframes doblemente anidados cuando no se quiere volver hasta el documento raíz.
     */
    public void switchToParentFrame() {
        driver.switchTo().parentFrame();
    }
}
