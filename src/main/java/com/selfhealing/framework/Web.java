package com.selfhealing.framework;

import com.selfhealing.framework.assertions.SoftAssertions;
import com.selfhealing.framework.assertions.WebAssert;
import com.selfhealing.framework.browser.CookieActions;
import com.selfhealing.framework.browser.StorageActions;
import com.selfhealing.framework.client.HealingClient;
import com.selfhealing.framework.actions.Actions;
import com.selfhealing.framework.config.WebConfig;
import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.healing.HealingActions;
import com.selfhealing.framework.popup.PopupGuard;
import com.selfhealing.framework.table.Table;
import com.selfhealing.framework.waits.Waits;

import java.util.function.Consumer;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

/**
 * Punto de entrada principal del framework de automatización web.
 *
 * <p>Actúa como fachada que integra y expone los tres módulos del framework:</p>
 * <ul>
 *   <li>{@code web.actions}  — escritura, clicks, scroll, visual, alertas, drag, etc.</li>
 *   <li>{@code web.waits}    — esperas basadas en condiciones para páginas lentas</li>
 *   <li>{@code web.healing}  — registro de baselines y sanación de selectores rotos</li>
 * </ul>
 *
 * <p>Compatible con cualquier framework de testing — el usuario gestiona el ciclo
 * de vida según su herramienta:</p>
 * <pre>{@code
 * // JUnit 5
 * @BeforeEach void setUp()    { web = Web.init(config); }
 * @AfterEach  void tearDown() { web.close(); }
 *
 * // TestNG
 * @BeforeMethod public void setUp()    { web = Web.init(config); }
 * @AfterMethod  public void tearDown() { web.close(); }
 *
 * // Cucumber
 * @Before public void setUp()    { web = Web.init(config); }
 * @After  public void tearDown() { web.close(); }
 *
 * // Main directo
 * Web web = Web.init("http://mi-app.com", "mi-proyecto");
 * web.close();
 * }</pre>
 *
 * <h3>Ejemplo de uso en un test:</h3>
 * <pre>{@code
 * Element username = Element.id("input-username").label("Campo usuario");
 * Element password = Element.id("input-password").label("Campo contraseña");
 * Element loginBtn = Element.id("btn-login").label("Botón login");
 *
 * web.actions.type(username, "admin");
 * web.actions.type(password, "secret");
 * web.actions.click(loginBtn);
 * web.waits.untilVisible(Element.id("login-result"));
 *
 * // Registrar popups conocidos y envolver acciones sensibles
 * web.popupGuard.register("Cookie Banner", By.id("cookie-consent"),
 *     driver -> driver.findElement(By.id("btn-accept-all")).click());
 * web.popupGuard.safely(() -> web.actions.click(loginBtn));
 * }</pre>
 */
public class Web {

    // -------------------------------------------------------------------------
    // Módulos del framework — acceso público e intencional
    // -------------------------------------------------------------------------

    /** Acciones de interacción: escritura, clicks, scroll, visual, alertas, drag, selects. */
    public final Actions        actions;

    /** Esperas basadas en condiciones para páginas lentas o con DOM inestable. */
    public final Waits          waits;

    /** Operaciones del servicio de self-healing: registro de baselines y sanación. */
    public final HealingActions healing;

    /**
     * Guardia de popups: detecta y maneja automáticamente ventanas emergentes inesperadas.
     * Registra reglas para popups conocidos y envuelve acciones sensibles con {@code safely()}.
     */
    public final PopupGuard     popupGuard;

    /** Gestión de cookies del navegador (leer, escribir, eliminar). */
    public final CookieActions  cookies;

    /** Gestión de localStorage y sessionStorage via JavaScript. */
    public final StorageActions storage;

    /**
     * Driver de Selenium expuesto para operaciones avanzadas que el framework
     * no cubre directamente. Usar con moderación.
     */
    public final WebDriver driver;

    /**
     * Cliente HTTP del servicio de healing. Expuesto para tests que verifican
     * directamente los endpoints del servicio (/health, /metrics, /history).
     * Para operaciones de healing habituales usa {@link #healing} en su lugar.
     */
    public final HealingClient healingClient;
    private final WebConfig     config;

    // -------------------------------------------------------------------------
    // Constructor privado — solo se crea via Web.init()
    // -------------------------------------------------------------------------

    private Web(WebConfig config) {
        this.config        = config;
        this.driver        = buildDriver(config);
        this.healingClient = new HealingClient(config.getHealingUrl());

        // Instanciar módulos compartiendo el mismo driver
        this.waits      = new Waits(driver, config.getTimeoutSeconds());
        this.actions    = new Actions(driver, waits);
        this.healing    = new HealingActions(driver, healingClient, waits,
                              config.getProject(), config.getScoringProfile().toApiValue());
        this.popupGuard = new PopupGuard(driver);
        this.cookies    = new CookieActions(driver);
        this.storage    = new StorageActions(driver);

        // Navegar a la URL base y esperar que la página esté lista
        driver.get(config.getUrl());
        waits.untilPageReady();
    }

    // -------------------------------------------------------------------------
    // Factory methods — puntos de entrada para el usuario
    // -------------------------------------------------------------------------

    /**
     * Inicia una sesión con configuración completa.
     *
     * <pre>{@code
     * WebConfig config = WebConfig.builder()
     *     .url("http://mi-app.com")
     *     .project("mi-proyecto")
     *     .timeoutSeconds(60)
     *     .headless(true)
     *     .build();
     * Web web = Web.init(config);
     * }</pre>
     *
     * @param config configuración de la sesión
     * @return instancia lista para usar
     */
    public static Web init(WebConfig config) {
        return new Web(config);
    }

    /**
     * Atajo para iniciar una sesión con URL y proyecto, usando valores por defecto
     * para el resto de opciones (Chrome, sin headless, timeouts estándar).
     *
     * <pre>{@code
     * Web web = Web.init("http://mi-app.com", "mi-proyecto");
     * }</pre>
     *
     * @param url     URL base de la aplicación
     * @param project nombre del proyecto para el servicio de healing
     * @return instancia lista para usar
     */
    public static Web init(String url, String project) {
        return new Web(WebConfig.builder().url(url).project(project).build());
    }

    // -------------------------------------------------------------------------
    // Assertions
    // -------------------------------------------------------------------------

    /**
     * Inicia una cadena de assertions estrictas sobre el elemento indicado.
     * El test falla inmediatamente al primer assertion que no se cumpla.
     *
     * <pre>{@code
     * web.assertThat(resultado)
     *    .isVisible()
     *    .hasText("Login exitoso")
     *    .hasAttribute("class", "success");
     * }</pre>
     *
     * @param element elemento a verificar
     * @return {@link WebAssert} encadenable sobre ese elemento
     */
    public WebAssert assertThat(Element element) {
        return new WebAssert(driver, waits, element);
    }

    /**
     * Ejecuta un bloque de assertions suaves: todas las verificaciones se evalúan
     * y los fallos se acumulan. Si hay algún fallo, se lanza al final del bloque.
     *
     * <pre>{@code
     * web.softAssert(sa -> {
     *     sa.check(titulo)  .hasText("Dashboard");
     *     sa.check(menu)    .isVisible();
     *     sa.check(usuario) .hasValue("admin");
     * });
     * }</pre>
     *
     * @param consumer bloque que recibe el {@link SoftAssertions} collector
     */
    public void softAssert(Consumer<SoftAssertions> consumer) {
        SoftAssertions sa = new SoftAssertions(driver, waits);
        consumer.accept(sa);
        sa.assertAll();
    }

    // -------------------------------------------------------------------------
    // Tabla helper
    // -------------------------------------------------------------------------

    /**
     * Crea un helper para interactuar con la tabla HTML indicada.
     *
     * <pre>{@code
     * Table tabla = web.table(Element.id("tabla-usuarios"));
     * String nombre = tabla.cell(0, "Nombre");
     * tabla.rowWhere("Estado", "Activo").click();
     * assertEquals(5, tabla.rowCount());
     * }</pre>
     *
     * @param element localizador del elemento {@code <table>}
     * @return {@link Table} helper listo para usar
     */
    public Table table(Element element) {
        return new Table(driver, waits, element);
    }

    // -------------------------------------------------------------------------
    // Navegación
    // -------------------------------------------------------------------------

    /**
     * Navega a otra URL sin reiniciar el driver ni la sesión.
     * El ciclo de login/setup se mantiene; solo cambia la página activa.
     *
     * @param url URL a la que navegar
     */
    public void navigateTo(String url) {
        driver.get(url);
        waits.untilPageReady();
    }

    // -------------------------------------------------------------------------
    // Cierre de la sesión
    // -------------------------------------------------------------------------

    /**
     * Cierra el navegador y libera todos los recursos del framework.
     *
     * <p>Llamar siempre al final del test o en el método de teardown para
     * evitar procesos de ChromeDriver o GeckoDriver huérfanos.</p>
     */
    public void close() {
        try {
            if (driver != null) driver.quit();
        } catch (Exception ignored) {
            // Si el driver ya está cerrado, ignorar silenciosamente
        }
        try {
            if (healingClient != null) healingClient.close();
        } catch (IOException ignored) {}
    }

    // -------------------------------------------------------------------------
    // Construcción del WebDriver según la configuración
    // -------------------------------------------------------------------------

    /**
     * Crea y configura el WebDriver aplicando las 3 capas de configuración en orden:
     *
     * <ol>
     *   <li>Opciones base del framework (headless, no-sandbox, proxy)</li>
     *   <li>Argumentos y preferencias extra declarados en {@link WebConfig}</li>
     *   <li>Escape hatch — customizador del usuario, aplicado al final</li>
     * </ol>
     */
    private static WebDriver buildDriver(WebConfig config) {
        applyDriverPath(config);

        WebDriver driver;

        switch (config.getBrowser()) {
            case FIREFOX:
                driver = new FirefoxDriver(buildFirefoxOptions(config));
                break;

            case EDGE:
                driver = new EdgeDriver(buildEdgeOptions(config));
                break;

            case IE:
                driver = new InternetExplorerDriver(buildIeOptions(config));
                break;

            case CHROME:
            default:
                driver = new ChromeDriver(buildChromeOptions(config));
                break;
        }

        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));
        driver.manage().timeouts().pageLoadTimeout(
            Duration.ofSeconds(config.getPageLoadTimeoutSeconds()));

        return driver;
    }

    /**
     * Registra la ruta del ejecutable del driver como system property antes de crear el driver.
     * Solo actúa si {@code config.getDriverPath()} está definido y la propiedad no fue ya
     * sobreescrita desde la línea de comandos ({@code -Dwebdriver.chrome.driver=...}).
     */
    private static void applyDriverPath(WebConfig config) {
        String dir = config.getDriverPath();
        if (dir == null || dir.isBlank()) return;

        String propKey;
        String exeName;
        switch (config.getBrowser()) {
            case FIREFOX: propKey = "webdriver.gecko.driver";  exeName = "geckodriver.exe";    break;
            case EDGE:    propKey = "webdriver.edge.driver";   exeName = "msedgedriver.exe";   break;
            case IE:      propKey = "webdriver.ie.driver";     exeName = "IEDriverServer.exe"; break;
            default:      propKey = "webdriver.chrome.driver"; exeName = "chromedriver.exe";   break;
        }

        if (System.getProperty(propKey) == null) {
            System.setProperty(propKey, dir + File.separator + exeName);
        }
    }

    // ── Construcción de ChromeOptions en 3 capas ─────────────────────────────

    private static ChromeOptions buildChromeOptions(WebConfig config) {
        ChromeOptions opts = new ChromeOptions();

        // Capa 1 — opciones base del framework
        if (config.isHeadless())
            opts.addArguments("--headless=new");
        opts.addArguments("--no-sandbox", "--disable-dev-shm-usage");

        // Capa 1b — proxy
        if (config.hasProxy())
            opts.setProxy(buildProxy(config));

        // Capa 2 — argumentos extra declarados por el usuario
        if (!config.getExtraArguments().isEmpty())
            opts.addArguments(config.getExtraArguments());

        // Capa 2b — preferencias de Chrome (chrome://settings)
        if (!config.getChromePreferences().isEmpty())
            opts.setExperimentalOption("prefs", config.getChromePreferences());

        // Capa 3 — escape hatch: el usuario recibe el objeto ya preparado
        if (config.getChromeCustomizer() != null)
            config.getChromeCustomizer().accept(opts);

        return opts;
    }

    // ── Construcción de EdgeOptions en 3 capas ───────────────────────────────
    // En Selenium 4, EdgeOptions y ChromeOptions son clases hermanas (no hay herencia),
    // por lo que el chromeCustomizer NO puede aplicarse a EdgeOptions directamente.
    // Para personalización avanzada de Edge, usa .chromeArg() y .chromePref() del builder
    // (se aplican a ambos navegadores), o .customizeChrome() solo aplica a Chrome.

    private static EdgeOptions buildEdgeOptions(WebConfig config) {
        EdgeOptions opts = new EdgeOptions();

        // Capa 1 — opciones base
        if (config.isHeadless())
            opts.addArguments("--headless=new");
        opts.addArguments("--no-sandbox", "--disable-dev-shm-usage");

        // Capa 1b — proxy
        if (config.hasProxy())
            opts.setProxy(buildProxy(config));

        // Capa 2 — argumentos y preferencias extra (compartidos con Chrome/Edge)
        if (!config.getExtraArguments().isEmpty())
            opts.addArguments(config.getExtraArguments());

        if (!config.getChromePreferences().isEmpty())
            opts.setExperimentalOption("prefs", config.getChromePreferences());

        // Capa 3 — escape hatch con tipo correcto (Consumer<EdgeOptions>)
        if (config.getEdgeCustomizer() != null)
            config.getEdgeCustomizer().accept(opts);

        return opts;
    }

    // ── Construcción de FirefoxOptions en 3 capas ────────────────────────────

    private static FirefoxOptions buildFirefoxOptions(WebConfig config) {
        FirefoxOptions opts = new FirefoxOptions();

        // Capa 1 — opciones base del framework
        if (config.isHeadless())
            opts.addArguments("-headless");

        // Capa 1b — proxy
        if (config.hasProxy())
            opts.setProxy(buildProxy(config));

        // Capa 2 — argumentos extra
        if (!config.getExtraArguments().isEmpty())
            opts.addArguments(config.getExtraArguments());

        // Capa 3 — escape hatch
        if (config.getFirefoxCustomizer() != null)
            config.getFirefoxCustomizer().accept(opts);

        return opts;
    }

    // ── Construcción de InternetExplorerOptions ──────────────────────────────

    private static InternetExplorerOptions buildIeOptions(WebConfig config) {
        InternetExplorerOptions opts = new InternetExplorerOptions();

        // Necesario en entornos corporativos donde las zonas de seguridad
        // de IE no están uniformemente configuradas
        opts.introduceFlakinessByIgnoringSecurityDomains();

        // Ignora si el nivel de zoom no está al 100% (frecuente en PCs corporativas)
        opts.ignoreZoomSettings();

        // Proxy
        if (config.hasProxy()) opts.setProxy(buildProxy(config));

        return opts;
    }

    // ── Construcción del objeto Proxy de Selenium ────────────────────────────

    private static Proxy buildProxy(WebConfig config) {
        String proxyAddress = config.getProxyHost() + ":" + config.getProxyPort();
        return new Proxy()
            .setHttpProxy(proxyAddress)
            .setSslProxy(proxyAddress);
    }
}
