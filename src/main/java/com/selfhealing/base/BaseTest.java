package com.selfhealing.base;

import com.selfhealing.config.ConfigLoader;
import com.selfhealing.framework.Web;
import com.selfhealing.framework.WebContext;
import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.log.StepLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Clase base de todos los tests.
 *
 * <p>Gestiona el ciclo de vida de la sesión {@link Web} y la registra en
 * {@link WebContext} para que extensiones (screenshots, listeners) y page objects
 * puedan acceder al driver del hilo actual sin referencias directas a la clase de test.</p>
 *
 * <h3>Ejecución paralela:</h3>
 * <p>Con JUnit 5 en modo {@code PER_METHOD} (por defecto), cada método de test
 * recibe su propia instancia de {@code BaseTest} → su propio {@code Web} → su propio browser.
 * El {@link WebContext} ThreadLocal garantiza que extensiones y utilidades siempre
 * accedan al driver correcto para el hilo en curso.</p>
 *
 * <h3>Configuración:</h3>
 * <p>La URL y el proyecto se leen de {@code config.properties}.
 * Las opciones del browser se configuran vía {@link WebConfig}.</p>
 */
@ExtendWith({ScreenshotExtension.class, RetryExtension.class})
public abstract class BaseTest extends SiebelWaits {

    protected static final String APP_URL = ConfigLoader.get("app.url");
    protected static final String PROJECT = ConfigLoader.get("app.project");

    /**
     * Sesión activa del framework. Cubre actions, waits, healing, cookies, storage y más.
     * Registrada en {@link WebContext} para acceso desde extensiones.
     */
    protected Web web;

    // -------------------------------------------------------------------------
    // Ciclo de vida
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUpBase() {
        StepLogger.clear();

        // Toda la configuración viene de config.properties (o -D system properties en CI)
        web = Web.init(ConfigLoader.webConfig());

        // Registrar en el ThreadLocal para extensiones y page objects
        WebContext.set(web);

        // Compatibilidad con SiebelWaits (usa 'driver' directamente)
        driver = web.driver;
    }

    @AfterEach
    void tearDownBase() {
        StepLogger.printSummary();
        try {
            if (web != null) web.close();
        } finally {
            // Limpiar siempre el ThreadLocal para evitar memory leaks en pools de hilos
            WebContext.remove();
            driver = null;
            web    = null;
        }
    }

    // -------------------------------------------------------------------------
    // Registro de baseline — delega en web.healing
    // -------------------------------------------------------------------------

    protected void registerBaseline(String xpath, String testId) {
        web.healing.register(Element.xpath(xpath), testId);
    }

    // -------------------------------------------------------------------------
    // Heal-and-find — delega en web.healing
    // -------------------------------------------------------------------------

    protected WebElement healAndFind(String xpath) {
        return web.healing.heal(
            Element.xpath(xpath),
            "heal_" + Math.abs(xpath.hashCode())
        );
    }

    protected WebElement healAndFind(String xpath, String testId) {
        return web.healing.heal(Element.xpath(xpath), testId);
    }

    // -------------------------------------------------------------------------
    // Helpers de conveniencia para los tests
    // -------------------------------------------------------------------------

    protected WebDriverWait wait(int seconds) {
        return new WebDriverWait(driver, Duration.ofSeconds(seconds));
    }

    protected void waitVisible(org.openqa.selenium.By locator) {
        wait(10).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected void waitInvisible(org.openqa.selenium.By locator) {
        wait(10).until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    protected void mutate(String jsFunction) {
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(jsFunction + "()");
    }
}
