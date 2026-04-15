package com.selfhealing.framework.actions;

import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Acciones sobre alertas nativas del navegador (alert, confirm, prompt).
 *
 * <p>Estas son ventanas del sistema operativo disparadas por JavaScript
 * — NO son modales HTML. Selenium las maneja mediante {@code driver.switchTo().alert()}.
 * Tipos soportados:</p>
 * <ul>
 *   <li>{@code alert()} — mensaje informativo, solo botón "Aceptar"</li>
 *   <li>{@code confirm()} — pregunta con "Aceptar" y "Cancelar"</li>
 *   <li>{@code prompt()} — igual que confirm() pero con un campo de texto</li>
 * </ul>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * web.actions.alert.waitForAlert();
 * String message = web.actions.alert.readAndAccept();
 * }</pre>
 */
public class AlertActions {

    private final WebDriver driver;
    private final Waits     waits;

    /**
     * @param driver sesión activa de WebDriver
     * @param waits  operaciones de espera compartidas
     */
    public AlertActions(WebDriver driver, Waits waits) {
        this.driver = driver;
        this.waits  = waits;
    }

    // -------------------------------------------------------------------------
    // Verificación de presencia
    // -------------------------------------------------------------------------

    /**
     * Comprueba si hay una alerta visible en este momento sin lanzar excepción.
     * Útil para código condicional: actuar solo si hay alerta presente.
     *
     * @return {@code true} si hay una alerta activa
     */
    public boolean isAlertPresent() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(2))
                .until(ExpectedConditions.alertIsPresent());
            return true;
        } catch (TimeoutException e) {
            return false; // no había alerta en los 2 segundos de espera
        }
    }

    /**
     * Espera hasta que aparezca una alerta (máximo 10 segundos).
     * Útil cuando la alerta se muestra tras una acción asíncrona.
     *
     * @throws TimeoutException si no aparece ninguna alerta en el tiempo máximo
     */
    public void waitForAlert() {
        new WebDriverWait(driver, Duration.ofSeconds(10))
            .until(ExpectedConditions.alertIsPresent());
    }

    // -------------------------------------------------------------------------
    // Lectura del mensaje
    // -------------------------------------------------------------------------

    /**
     * Lee el texto de la alerta sin cerrarla.
     * Permite validar el mensaje antes de decidir si aceptar o cancelar.
     *
     * @return el texto visible en la alerta
     */
    public String read() {
        return driver.switchTo().alert().getText();
    }

    // -------------------------------------------------------------------------
    // Cierre de alertas
    // -------------------------------------------------------------------------

    /**
     * Acepta la alerta haciendo clic en "OK" o "Aceptar".
     * Funciona con los tres tipos: alert(), confirm() y prompt().
     * Tras cerrar la alerta espera a que la página esté lista.
     */
    public void accept() {
        driver.switchTo().alert().accept();
        waits.untilPageReady();
    }

    /**
     * Descarta la alerta haciendo clic en "Cancelar" o cerrando la ventana.
     * Solo tiene efecto real en confirm() y prompt();
     * en un alert() simple equivale a accept().
     */
    public void dismiss() {
        driver.switchTo().alert().dismiss();
        waits.untilPageReady();
    }

    /**
     * Escribe texto en el campo de un prompt() y luego lo acepta.
     * El prompt() es el único tipo de alerta nativa con campo de texto.
     *
     * @param text texto a escribir en el campo del prompt
     */
    public void typeAndAccept(String text) {
        Alert alert = driver.switchTo().alert();
        alert.sendKeys(text);
        alert.accept();
        waits.untilPageReady();
    }

    /**
     * Lee el mensaje de la alerta, la acepta y devuelve el texto leído.
     * Combinación habitual cuando necesitas validar el mensaje en el test
     * después de haber cerrado la alerta.
     *
     * @return el texto que mostraba la alerta antes de cerrarla
     */
    public String readAndAccept() {
        Alert alert = driver.switchTo().alert();
        String text = alert.getText();
        alert.accept();
        waits.untilPageReady();
        return text;
    }

    /**
     * Lee el mensaje de la alerta, la descarta y devuelve el texto leído.
     *
     * @return el texto que mostraba la alerta antes de descartarla
     */
    public String readAndDismiss() {
        Alert alert = driver.switchTo().alert();
        String text = alert.getText();
        alert.dismiss();
        waits.untilPageReady();
        return text;
    }
}
