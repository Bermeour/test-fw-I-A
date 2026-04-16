package com.selfhealing.framework.config;

import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Configuración inmutable del framework de automatización web.
 *
 * <p>Se construye con el patrón Builder. Soporta tres capas de configuración
 * del driver, aplicadas en este orden por {@code Web.init()}:</p>
 *
 * <ol>
 *   <li><strong>Opciones base del framework</strong> — headless, no-sandbox, etc.
 *       (siempre aplicadas)</li>
 *   <li><strong>Campos estructurados</strong> — proxy, argumentos extra, preferencias
 *       de Chrome. Cubren el 90% de los casos sin necesitar conocer la API de Selenium.</li>
 *   <li><strong>Escape hatch</strong> — {@code customizeChrome} / {@code customizeFirefox}:
 *       el usuario recibe el {@code ChromeOptions} ya preparado y puede añadir
 *       cualquier opción avanzada. Aplicado al final, tiene prioridad sobre todo lo demás.</li>
 * </ol>
 *
 * <h3>Ejemplos de uso:</h3>
 * <pre>{@code
 * // Configuración mínima
 * WebConfig config = WebConfig.builder()
 *     .url("http://mi-app.com")
 *     .project("mi-proyecto")
 *     .build();
 *
 * // Con proxy corporativo
 * WebConfig config = WebConfig.builder()
 *     .url("http://mi-app.com")
 *     .project("mi-proyecto")
 *     .proxy("proxy.empresa.com", 8080)
 *     .build();
 *
 * // Con argumentos y preferencias de Chrome
 * WebConfig config = WebConfig.builder()
 *     .url("http://mi-app.com")
 *     .project("mi-proyecto")
 *     .chromeArg("--window-size=1920,1080")
 *     .chromeArg("--disable-extensions")
 *     .chromePref("download.default_directory", "/tmp/descargas")
 *     .chromePref("download.prompt_for_download", false)
 *     .build();
 *
 * // Escape hatch para opciones avanzadas
 * WebConfig config = WebConfig.builder()
 *     .url("http://mi-app.com")
 *     .project("mi-proyecto")
 *     .customizeChrome(opts -> {
 *         opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
 *         opts.setExperimentalOption("useAutomationExtension", false);
 *     })
 *     .build();
 * }</pre>
 */
public class WebConfig {

    // ── Conexión ──────────────────────────────────────────────────
    private final String         url;
    private final String         project;
    private final String         healingUrl;
    private final ScoringProfile scoringProfile;

    // ── Driver local ──────────────────────────────────────────────
    /** Directorio donde se encuentran los ejecutables del driver (ej: C:/Selenium/driver).
     *  Vacío o null → Selenium Manager intenta descargarlo automáticamente. */
    private final String driverPath;

    // ── Caché local de reparaciones ───────────────────────────────
    private final boolean repairCacheEnabled;
    private final String  repairDbPath;
    private final int     repairCacheTtlDays;
    private final int     repairCacheMinScore;

    // ── Timeouts ──────────────────────────────────────────────────
    private final int     timeoutSeconds;
    private final int     pageLoadTimeoutSeconds;

    // ── Navegador ─────────────────────────────────────────────────
    private final boolean headless;
    private final Browser browser;

    // ── Proxy ─────────────────────────────────────────────────────
    private final String  proxyHost;
    private final int     proxyPort;

    // ── Opciones adicionales de Chrome ────────────────────────────
    private final List<String>        extraArguments;
    private final Map<String, Object> chromePreferences;

    // ── Escape hatch ──────────────────────────────────────────────
    private final Consumer<ChromeOptions>  chromeCustomizer;
    private final Consumer<EdgeOptions>    edgeCustomizer;
    private final Consumer<FirefoxOptions> firefoxCustomizer;

    /** Navegadores soportados. */
    public enum Browser { CHROME, FIREFOX, EDGE, IE }

    /**
     * Perfil de scoring del servicio de self-healing.
     *
     * <p>Cada perfil ajusta los pesos de los motores de healing (DOM, CV, historial)
     * según las características del tipo de aplicación bajo prueba:</p>
     * <ul>
     *   <li>{@code DEFAULT}  — equilibrado, válido para la mayoría de aplicaciones web</li>
     *   <li>{@code SIEBEL}   — prioriza contexto DOM y jerarquía para apps Siebel/SAP</li>
     *   <li>{@code ANGULAR}  — adapta el scoring para apps Angular (componentes, bindings)</li>
     *   <li>{@code LEGACY}   — optimizado para apps legacy con IDs dinámicos o frágiles</li>
     * </ul>
     *
     * <p>Se envía como campo {@code scoring_profile} en cada POST /heal.</p>
     */
    public enum ScoringProfile {
        DEFAULT, SIEBEL, ANGULAR, LEGACY;

        /** Devuelve el valor en minúsculas tal como lo espera el servicio. */
        public String toApiValue() { return name().toLowerCase(); }
    }

    private WebConfig(Builder b) {
        this.url                    = b.url;
        this.project                = b.project;
        this.healingUrl             = b.healingUrl;
        this.scoringProfile         = b.scoringProfile;
        this.driverPath             = b.driverPath;
        this.repairCacheEnabled     = b.repairCacheEnabled;
        this.repairDbPath           = b.repairDbPath;
        this.repairCacheTtlDays     = b.repairCacheTtlDays;
        this.repairCacheMinScore    = b.repairCacheMinScore;
        this.timeoutSeconds         = b.timeoutSeconds;
        this.pageLoadTimeoutSeconds = b.pageLoadTimeoutSeconds;
        this.headless               = b.headless;
        this.browser                = b.browser;
        this.proxyHost              = b.proxyHost;
        this.proxyPort              = b.proxyPort;
        this.extraArguments         = Collections.unmodifiableList(new ArrayList<>(b.extraArguments));
        this.chromePreferences      = Collections.unmodifiableMap(new LinkedHashMap<>(b.chromePreferences));
        this.chromeCustomizer       = b.chromeCustomizer;
        this.edgeCustomizer         = b.edgeCustomizer;
        this.firefoxCustomizer      = b.firefoxCustomizer;
    }

    // ── Getters ───────────────────────────────────────────────────

    public String         getUrl()                  { return url;                  }
    public String         getProject()             { return project;              }
    public String         getHealingUrl()          { return healingUrl;           }
    public ScoringProfile getScoringProfile()      { return scoringProfile;       }
    public String         getDriverPath()          { return driverPath;           }
    public boolean        isRepairCacheEnabled()   { return repairCacheEnabled;   }
    public String         getRepairDbPath()        { return repairDbPath;         }
    public int            getRepairCacheTtlDays()  { return repairCacheTtlDays;   }
    public int            getRepairCacheMinScore() { return repairCacheMinScore;  }
    public int     getTimeoutSeconds()         { return timeoutSeconds;         }
    public int     getPageLoadTimeoutSeconds() { return pageLoadTimeoutSeconds; }
    public boolean isHeadless()                { return headless;               }
    public Browser getBrowser()                { return browser;                }

    /** Host del proxy HTTP/HTTPS, o {@code null} si no se configuró proxy. */
    public String  getProxyHost()              { return proxyHost;              }

    /** Puerto del proxy. Solo relevante si {@link #getProxyHost()} no es nulo. */
    public int     getProxyPort()              { return proxyPort;              }

    /** Lista inmutable de argumentos extra para Chrome/Firefox (sin duplicar los del framework). */
    public List<String> getExtraArguments()    { return extraArguments;         }

    /** Mapa inmutable de preferencias de Chrome ({@code chrome://settings}). */
    public Map<String, Object> getChromePreferences() { return chromePreferences; }

    /** Customizador avanzado de {@link ChromeOptions}, o {@code null} si no se definió. */
    public Consumer<ChromeOptions>  getChromeCustomizer()  { return chromeCustomizer;  }

    /** Customizador avanzado de {@link EdgeOptions}, o {@code null} si no se definió. */
    public Consumer<EdgeOptions>    getEdgeCustomizer()    { return edgeCustomizer;    }

    /** Customizador avanzado de {@link FirefoxOptions}, o {@code null} si no se definió. */
    public Consumer<FirefoxOptions> getFirefoxCustomizer() { return firefoxCustomizer; }

    /** ¿Hay proxy configurado? */
    public boolean hasProxy() { return proxyHost != null && !proxyHost.isBlank(); }

    // ── Factory ───────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    // ─────────────────────────────────────────────────────────────
    // Builder
    // ─────────────────────────────────────────────────────────────

    public static class Builder {

        // Valores por defecto
        private String         url             = "http://localhost:9000";
        private String         project         = "default";
        private String         healingUrl      = "http://localhost:8765";
        private ScoringProfile scoringProfile  = ScoringProfile.DEFAULT;
        private String         driverPath      = "";
        // caché local
        private boolean repairCacheEnabled  = true;
        private String  repairDbPath        = "jdbc:sqlite:repair-history.db";
        private int     repairCacheTtlDays  = 7;
        private int     repairCacheMinScore = 80;
        private int     timeoutSeconds         = 30;
        private int     pageLoadTimeoutSeconds = 60;
        private boolean headless               = false;
        private Browser browser                = Browser.CHROME;

        private String  proxyHost              = null;
        private int     proxyPort              = 0;

        private final List<String>        extraArguments    = new ArrayList<>();
        private final Map<String, Object> chromePreferences = new LinkedHashMap<>();

        private Consumer<ChromeOptions>  chromeCustomizer  = null;
        private Consumer<EdgeOptions>    edgeCustomizer    = null;
        private Consumer<FirefoxOptions> firefoxCustomizer = null;

        // ── Conexión ──────────────────────────────────────────────

        /** URL base de la aplicación. Requerido. */
        public Builder url(String url)             { this.url = url;         return this; }

        /** Nombre del proyecto para el servicio de healing. Requerido. */
        public Builder project(String project)     { this.project = project; return this; }

        /** URL del servicio de self-healing. Por defecto: {@code http://localhost:8765}. */
        public Builder healingUrl(String url)      { this.healingUrl = url;  return this; }

        /**
         * Directorio local donde están los ejecutables del driver.
         * El framework selecciona el ejecutable correcto según el navegador:
         * {@code chromedriver.exe}, {@code msedgedriver.exe}, {@code geckodriver.exe},
         * {@code IEDriverServer.exe}.
         *
         * <pre>{@code
         * .driverPath("C:/Selenium/driver")
         * }</pre>
         *
         * Si no se establece, Selenium Manager intenta descargarlo automáticamente.
         */
        public Builder driverPath(String path)     { this.driverPath = path; return this; }

        /**
         * Activa o desactiva el caché local SQLite de reparaciones. Por defecto: {@code true}.
         * Desactivar en entornos CI donde el caché no debe persistir entre ejecuciones.
         */
        public Builder repairCacheEnabled(boolean enabled) {
            this.repairCacheEnabled = enabled; return this;
        }

        /**
         * Ruta JDBC del SQLite para el caché local. Por defecto: {@code jdbc:sqlite:repair-history.db}.
         *
         * <pre>{@code
         * .repairDbPath("jdbc:sqlite:/var/lib/tests/repair-history.db")
         * }</pre>
         */
        public Builder repairDbPath(String jdbcUrl) {
            this.repairDbPath = jdbcUrl; return this;
        }

        /**
         * Días que una entrada del caché se considera válida desde su último uso.
         * Por defecto: 7. Usar 0 para deshabilitar expiración.
         */
        public Builder repairCacheTtlDays(int days) {
            this.repairCacheTtlDays = days; return this;
        }

        /**
         * Score mínimo (0-100) para usar una entrada del caché local.
         * Por defecto: 80. Bajar para más hits de caché, subir para más precisión.
         */
        public Builder repairCacheMinScore(int score) {
            this.repairCacheMinScore = score; return this;
        }

        /**
         * Perfil de scoring del servicio de healing. Por defecto: {@link ScoringProfile#DEFAULT}.
         * Usar {@link ScoringProfile#SIEBEL} para Siebel/SAP, {@link ScoringProfile#ANGULAR}
         * para apps Angular, {@link ScoringProfile#LEGACY} para apps con IDs dinámicos.
         */
        public Builder scoringProfile(ScoringProfile profile) { this.scoringProfile = profile; return this; }

        // ── Timeouts ──────────────────────────────────────────────

        /** Timeout en segundos para esperas de elementos. Por defecto: 30. */
        public Builder timeoutSeconds(int s)            { this.timeoutSeconds = s;         return this; }

        /** Timeout en segundos para carga de página. Por defecto: 60. */
        public Builder pageLoadTimeoutSeconds(int s)    { this.pageLoadTimeoutSeconds = s; return this; }

        // ── Navegador ─────────────────────────────────────────────

        /** Ejecutar el navegador sin interfaz gráfica. Recomendado para CI/CD. */
        public Builder headless(boolean headless)       { this.headless = headless; return this; }

        /** Navegador a usar. Por defecto: CHROME. */
        public Builder browser(Browser browser)         { this.browser = browser;   return this; }

        // ── Proxy ─────────────────────────────────────────────────

        /**
         * Configura un proxy HTTP/HTTPS para todas las conexiones del navegador.
         *
         * <pre>{@code
         * .proxy("proxy.empresa.com", 8080)
         * }</pre>
         *
         * @param host hostname o IP del proxy
         * @param port puerto del proxy
         */
        public Builder proxy(String host, int port) {
            this.proxyHost = host;
            this.proxyPort = port;
            return this;
        }

        // ── Opciones adicionales de Chrome ────────────────────────

        /**
         * Añade un argumento de línea de comandos a Chrome o Firefox.
         * Se puede llamar múltiples veces para añadir varios argumentos.
         *
         * <pre>{@code
         * .chromeArg("--window-size=1920,1080")
         * .chromeArg("--disable-extensions")
         * .chromeArg("--incognito")
         * }</pre>
         *
         * @param arg argumento completo, con o sin valor (ej: {@code "--lang=es"})
         */
        public Builder chromeArg(String arg) {
            this.extraArguments.add(arg);
            return this;
        }

        /**
         * Añade múltiples argumentos de Chrome en una sola llamada.
         *
         * <pre>{@code
         * .chromeArgs("--window-size=1920,1080", "--disable-extensions")
         * }</pre>
         */
        public Builder chromeArgs(String... args) {
            for (String arg : args) this.extraArguments.add(arg);
            return this;
        }

        /**
         * Establece una preferencia de Chrome (equivalente a {@code chrome://settings}).
         * Se puede llamar múltiples veces para distintas preferencias.
         *
         * <pre>{@code
         * .chromePref("download.default_directory", "/tmp/descargas")
         * .chromePref("download.prompt_for_download", false)
         * .chromePref("plugins.always_open_pdf_externally", true)
         * }</pre>
         *
         * @param key   clave de la preferencia (notación con puntos)
         * @param value valor: {@code String}, {@code Integer}, {@code Boolean}, etc.
         */
        public Builder chromePref(String key, Object value) {
            this.chromePreferences.put(key, value);
            return this;
        }

        // ── Escape hatch ──────────────────────────────────────────

        /**
         * Customizador avanzado de {@link ChromeOptions}.
         *
         * <p>El framework ya habrá aplicado sus opciones base (headless, proxy, argumentos
         * y preferencias). Este customizador se ejecuta <strong>al final</strong>, por lo
         * que puede añadir o sobreescribir cualquier opción anterior.</p>
         *
         * <pre>{@code
         * .customizeChrome(opts -> {
         *     opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
         *     opts.setExperimentalOption("useAutomationExtension", false);
         *     opts.addExtensions(new File("mi-extension.crx"));
         * })
         * }</pre>
         */
        public Builder customizeChrome(Consumer<ChromeOptions> customizer) {
            this.chromeCustomizer = customizer;
            return this;
        }

        /**
         * Customizador avanzado de {@link EdgeOptions}.
         * En Selenium 4, EdgeOptions y ChromeOptions son clases independientes,
         * por lo que Edge tiene su propio customizador de tipo correcto.
         *
         * <pre>{@code
         * .customizeEdge(opts -> {
         *     opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
         * })
         * }</pre>
         */
        public Builder customizeEdge(Consumer<EdgeOptions> customizer) {
            this.edgeCustomizer = customizer;
            return this;
        }

        /**
         * Customizador avanzado de {@link FirefoxOptions}.
         * Mismo comportamiento que {@link #customizeChrome} pero para Firefox.
         *
         * <pre>{@code
         * .customizeFirefox(opts -> {
         *     opts.addPreference("browser.download.folderList", 2);
         *     opts.addPreference("browser.download.dir", "/tmp/descargas");
         * })
         * }</pre>
         */
        public Builder customizeFirefox(Consumer<FirefoxOptions> customizer) {
            this.firefoxCustomizer = customizer;
            return this;
        }

        // ── Build ─────────────────────────────────────────────────

        /**
         * Valida los campos requeridos y construye la configuración inmutable.
         *
         * @throws IllegalArgumentException si {@code url} o {@code project} están vacíos
         */
        public WebConfig build() {
            if (url == null || url.isBlank())
                throw new IllegalArgumentException("WebConfig: 'url' es requerido");
            if (project == null || project.isBlank())
                throw new IllegalArgumentException("WebConfig: 'project' es requerido");
            return new WebConfig(this);
        }
    }
}