package com.selfhealing.config;

import com.selfhealing.framework.config.WebConfig;
import com.selfhealing.framework.waits.StabilityConfig;
import com.selfhealing.framework.watchdog.WatchdogConfig;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Carga la configuración del framework desde {@code config.properties}
 * y aplica sobreescrituras de system properties.
 *
 * <h3>Prioridad de valores (de menor a mayor):</h3>
 * <ol>
 *   <li>{@code src/test/resources/config.properties} — valores por defecto</li>
 *   <li>System properties ({@code -Dapp.url=...}) — ganan siempre</li>
 * </ol>
 *
 * <h3>Uso básico:</h3>
 * <pre>{@code
 * // Carga config.properties + system properties automáticamente
 * Web web = Web.init(ConfigLoader.webConfig());
 * }</pre>
 *
 * <h3>Uso en CI/CD:</h3>
 * <pre>{@code
 * // Sin tocar config.properties, desde la línea de comandos:
 * mvn test -Dapp.url=http://staging.com \
 *          -Ddriver.headless=true \
 *          -Dproxy.host=proxy.empresa.com \
 *          -Dproxy.port=8080
 * }</pre>
 */
public class ConfigLoader {

    private static final String CONFIG_FILE = "config.properties";

    // Instancia singleton cargada una sola vez por JVM
    private static final Properties PROPS = load();

    private ConfigLoader() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Punto de entrada principal
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Construye un {@link WebConfig} con todos los valores del archivo de
     * configuración, sobreescritos por cualquier system property que esté presente.
     *
     * @return configuración lista para pasarle a {@code Web.init()}
     */
    public static WebConfig webConfig() {
        WebConfig.Builder builder = WebConfig.builder()
            .url(get("app.url"))
            .project(get("app.project"))
            .healingUrl(get("healing.url"))
            .browser(parseBrowser(get("driver.browser")))
            .headless(bool("driver.headless"))
            .timeoutSeconds(integer("driver.timeout.element"))
            .pageLoadTimeoutSeconds(integer("driver.timeout.pageLoad"));

        // Scoring profile del servicio de healing
        builder.scoringProfile(parseScoringProfile(get("healing.scoring_profile")));

        // Ruta local del driver (desactiva descarga automática de Selenium Manager)
        String driverPath = get("driver.path");
        if (!driverPath.isEmpty()) builder.driverPath(driverPath);

        // Caché local de reparaciones
        String repairCacheEnabled = get("repair.cache.enabled");
        if (!repairCacheEnabled.isEmpty()) builder.repairCacheEnabled(bool("repair.cache.enabled"));
        String repairDbPath = get("repair.db.path");
        if (!repairDbPath.isEmpty()) builder.repairDbPath(repairDbPath);
        String repairTtl = get("repair.cache.ttl.days");
        if (!repairTtl.isEmpty()) builder.repairCacheTtlDays(Integer.parseInt(repairTtl.trim()));
        String repairMinScore = get("repair.cache.min.score");
        if (!repairMinScore.isEmpty()) builder.repairCacheMinScore(Integer.parseInt(repairMinScore.trim()));

        // Proxy — solo si el host está definido
        String proxyHost = get("proxy.host");
        String proxyPort = get("proxy.port");
        if (!proxyHost.isEmpty() && !proxyPort.isEmpty()) {
            builder.proxy(proxyHost, Integer.parseInt(proxyPort.trim()));
        }

        // Argumentos extra de Chrome — lista separada por comas
        String chromeArgs = get("chrome.args");
        if (!chromeArgs.isEmpty()) {
            for (String arg : chromeArgs.split(",")) {
                String trimmed = arg.trim();
                if (!trimmed.isEmpty()) builder.chromeArg(trimmed);
            }
        }

        // Preferencias de Chrome — claves con prefijo "chrome.pref."
        for (String key : PROPS.stringPropertyNames()) {
            if (key.startsWith("chrome.pref.")) {
                String prefKey = key.substring("chrome.pref.".length());
                String rawVal  = get(key);
                if (!rawVal.isEmpty()) {
                    builder.chromePref(prefKey, inferType(rawVal));
                }
            }
        }

        return builder.build();
    }

    /**
     * Construye un {@link WatchdogConfig} con los valores de {@code config.properties}.
     * Si una clave no está definida se usa el default hardcodeado en {@link WatchdogConfig}.
     */
    public static WatchdogConfig watchdogConfig() {
        WatchdogConfig cfg = WatchdogConfig.defaultConfig();

        String enabled = get("watchdog.enabled");
        if (!enabled.isEmpty()) cfg.setEnabled(bool("watchdog.enabled"));

        String loadersBlock = get("watchdog.loaders.block");
        if (!loadersBlock.isEmpty()) cfg.setLoadersBlockExecution(bool("watchdog.loaders.block"));

        String overlaysBlock = get("watchdog.overlays.block");
        if (!overlaysBlock.isEmpty()) cfg.setOverlaysBlockExecution(bool("watchdog.overlays.block"));

        String loaderSels = get("watchdog.loader.selectors");
        if (!loaderSels.isEmpty()) cfg.setLoaderSelectors(parseList(loaderSels));

        String overlaySels = get("watchdog.overlay.selectors");
        if (!overlaySels.isEmpty()) cfg.setOverlaySelectors(parseList(overlaySels));

        String modalSels = get("watchdog.modal.selectors");
        if (!modalSels.isEmpty()) cfg.setModalSelectors(parseList(modalSels));

        String errorSels = get("watchdog.error.selectors");
        if (!errorSels.isEmpty()) cfg.setErrorSelectors(parseList(errorSels));

        return cfg;
    }

    /**
     * Construye un {@link StabilityConfig} con los valores de {@code config.properties}.
     * Incluye el {@link WatchdogConfig} construido desde el mismo archivo.
     * Si una clave no está definida se usa el default hardcodeado en {@link StabilityConfig}.
     */
    public static StabilityConfig stabilityConfig() {
        StabilityConfig cfg = StabilityConfig.defaultConfig();

        String timeout = get("stability.timeout.seconds");
        if (!timeout.isEmpty()) cfg.setDefaultTimeout(Duration.ofSeconds(Long.parseLong(timeout.trim())));

        String polling = get("stability.polling.millis");
        if (!polling.isEmpty()) cfg.setPollingInterval(Duration.ofMillis(Long.parseLong(polling.trim())));

        String maxRetries = get("stability.max.retries");
        if (!maxRetries.isEmpty()) cfg.setMaxRetries(Integer.parseInt(maxRetries.trim()));

        String docReady = get("stability.wait.document.ready");
        if (!docReady.isEmpty()) cfg.setWaitForDocumentReady(bool("stability.wait.document.ready"));

        String jquery = get("stability.wait.jquery");
        if (!jquery.isEmpty()) cfg.setWaitForJQueryInactive(bool("stability.wait.jquery"));

        String fastRecovery = get("stability.fast.recovery.seconds");
        if (!fastRecovery.isEmpty()) cfg.setFastRecoveryTimeout(Duration.ofSeconds(Long.parseLong(fastRecovery.trim())));

        String cacheWindow = get("stability.cache.window.millis");
        if (!cacheWindow.isEmpty()) cfg.setStabilityCacheWindow(Duration.ofMillis(Long.parseLong(cacheWindow.trim())));

        cfg.setWatchdogConfig(watchdogConfig());

        return cfg;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Acceso directo a propiedades individuales
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Devuelve el valor de la propiedad. Las system properties tienen prioridad.
     * Devuelve cadena vacía si la clave no existe.
     */
    public static String get(String key) {
        // System property primero — permite override sin tocar el archivo
        String sysProp = System.getProperty(key);
        if (sysProp != null) return sysProp.trim();
        return PROPS.getProperty(key, "").trim();
    }

    /** Devuelve el valor como {@code int}. Lanza {@link IllegalArgumentException} con mensaje claro si la clave no existe o el valor no es un número. */
    public static int integer(String key) {
        String value = get(key);
        if (value.isEmpty())
            throw new IllegalArgumentException(
                "[ConfigLoader] Propiedad requerida no encontrada: '" + key + "'" +
                " — defínela en config.properties o como -D" + key + "=<valor>");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "[ConfigLoader] La propiedad '" + key + "' debe ser un número entero" +
                " pero tiene el valor: '" + value + "'");
        }
    }

    /** Devuelve el valor como {@code boolean} ({@code "true"} → true, cualquier otro → false). */
    public static boolean bool(String key) {
        return Boolean.parseBoolean(get(key));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers internos
    // ─────────────────────────────────────────────────────────────────────────

    /** Carga el archivo de propiedades desde el classpath. */
    private static Properties load() {
        Properties props = new Properties();
        try (InputStream is = ConfigLoader.class
                .getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                System.err.println("[ConfigLoader] WARN: no se encontró " + CONFIG_FILE
                    + " en el classpath — usando solo system properties");
                return props;
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("[ConfigLoader] Error al cargar " + CONFIG_FILE, e);
        }
        return props;
    }

    /**
     * Infiere el tipo Java del valor de una preferencia de Chrome:
     * {@code "true"}/{@code "false"} → {@link Boolean},
     * número entero → {@link Integer},
     * cualquier otro → {@link String}.
     */
    private static Object inferType(String value) {
        if ("true".equalsIgnoreCase(value))  return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        return value;
    }

    private static WebConfig.Browser parseBrowser(String value) {
        switch (value.toLowerCase().replaceAll("[^a-z]", "")) {
            case "firefox":                         return WebConfig.Browser.FIREFOX;
            case "edge":                            return WebConfig.Browser.EDGE;
            case "ie":
            case "internetexplorer":
            case "ie11":
            case "iexplore":                        return WebConfig.Browser.IE;
            default:                                return WebConfig.Browser.CHROME;
        }
    }

    private static WebConfig.ScoringProfile parseScoringProfile(String value) {
        if (value == null || value.isEmpty()) return WebConfig.ScoringProfile.DEFAULT;
        switch (value.toLowerCase()) {
            case "siebel":  return WebConfig.ScoringProfile.SIEBEL;
            case "angular": return WebConfig.ScoringProfile.ANGULAR;
            case "legacy":  return WebConfig.ScoringProfile.LEGACY;
            default:        return WebConfig.ScoringProfile.DEFAULT;
        }
    }

    private static List<String> parseList(String csv) {
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}