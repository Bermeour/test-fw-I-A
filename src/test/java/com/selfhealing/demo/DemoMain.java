package com.selfhealing.demo;

import com.selfhealing.config.ConfigLoader;
import com.selfhealing.framework.Web;
import com.selfhealing.framework.WebContext;
import com.selfhealing.framework.actions.stable.ClickActions;
import com.selfhealing.framework.actions.stable.ReadActions;
import com.selfhealing.framework.actions.stable.WriteActions;
import com.selfhealing.framework.audit.SmartAuditEventType;
import com.selfhealing.framework.audit.SmartAuditRecorder;
import com.selfhealing.framework.client.HealContext;
import com.selfhealing.framework.client.HealingClient;
import com.selfhealing.framework.config.WebConfig;
import com.selfhealing.framework.config.WebConfig.Browser;
import com.selfhealing.framework.config.WebConfig.ScoringProfile;
import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.repair.RepairRepository;
import com.selfhealing.framework.repair.SuggestedLocator;
import com.selfhealing.framework.waits.SiebelWaits;
import com.selfhealing.framework.waits.StabilityConfig;
import com.selfhealing.framework.waits.StabilityWait;
import com.selfhealing.framework.watchdog.UiWatchdog;
import com.selfhealing.framework.watchdog.WatchdogConfig;
import com.selfhealing.framework.watchdog.WatchdogResult;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.Map;

/**
 * Demo ejecutable del framework — runner-agnostic (sin JUnit, TestNG ni Cucumber).
 *
 * Cubre todos los módulos disponibles:
 *
 *   [CONFIG]  — Formas de configurar y arrancar Web
 *   [HEAL-A]  — HealingClient directo (/health, /metrics, /history)
 *   [HEAL-B]  — HealingActions: register, heal, HealContext (anchors / container / form)
 *   [STABLE]  — ClickActions, WriteActions, ReadActions (stable action family)
 *   [SIEBEL]  — SiebelWaits standalone (sin herencia)
 *   [WATCH]   — UiWatchdog + StabilityWait standalone
 *   [REPO]    — RepairRepository (cache SQLite local)
 *   [AUDIT]   — SmartAuditRecorder (trazabilidad del healing)
 *
 * ─────────────────────────────────────────────────────────
 * Ejecutar:
 *   mvn test-compile exec:java \
 *       -Dexec.mainClass="com.selfhealing.demo.DemoMain" \
 *       -Dexec.classpathScope=test
 *
 * Requisitos:
 *   · Self-Healing Service en http://localhost:8765
 *   · Demo app en http://localhost:9000
 * ─────────────────────────────────────────────────────────
 */
public class DemoMain {

    public static void main(String[] args) throws Exception {

        banner("Self-Healing Framework — Demo completo");

        demoConfiguraciones();
        demoServicioDirecto();
        demoFrameworkCompleto();
        demoStableActions();
        demoSiebelWaits();
        demoWatchdogYStability();
        demoRepairRepository();
        demoSmartAuditRecorder();

        banner("Demo finalizado");
    }

    // =========================================================================
    // [CONFIG] — Formas de configurar y arrancar Web
    // =========================================================================

    private static void demoConfiguraciones() {
        seccion("CONFIG", "Formas de iniciar Web (sin abrir browser — solo ejemplos de código)");

        log("── 1. Mínima: url + proyecto");
        log("   Web web = Web.init(\"http://mi-app.com\", \"mi-proyecto\");");

        log("");
        log("── 2. Builder completo");
        log("   WebConfig config = WebConfig.builder()");
        log("       .url(\"http://mi-app.com\")");
        log("       .project(\"mi-proyecto\")");
        log("       .browser(Browser.CHROME)      // CHROME | FIREFOX | EDGE");
        log("       .headless(true)               // útil en CI/CD");
        log("       .timeoutSeconds(30)");
        log("       .pageLoadTimeoutSeconds(60)");
        log("       .scoringProfile(ScoringProfile.SIEBEL)  // DEFAULT|SIEBEL|ANGULAR|LEGACY");
        log("       .healingUrl(\"http://localhost:8765\")");
        log("       .build();");
        log("   Web web = Web.init(config);");

        log("");
        log("── 3. Con proxy corporativo");
        log("   WebConfig config = WebConfig.builder()");
        log("       .url(\"http://mi-app.com\").project(\"mi-proyecto\")");
        log("       .proxy(\"proxy.empresa.com\", 8080)");
        log("       .build();");

        log("");
        log("── 4. Con argumentos y preferencias de Chrome");
        log("   WebConfig config = WebConfig.builder()");
        log("       .url(\"http://mi-app.com\").project(\"mi-proyecto\")");
        log("       .chromeArg(\"--window-size=1920,1080\")");
        log("       .chromeArg(\"--disable-extensions\")");
        log("       .chromePref(\"download.default_directory\", \"/tmp/descargas\")");
        log("       .chromePref(\"download.prompt_for_download\", false)");
        log("       .build();");

        log("");
        log("── 5. Escape hatch — opciones avanzadas de Chrome");
        log("   WebConfig config = WebConfig.builder()");
        log("       .url(\"http://mi-app.com\").project(\"mi-proyecto\")");
        log("       .customizeChrome(opts -> {");
        log("           opts.setExperimentalOption(\"excludeSwitches\", List.of(\"enable-automation\"));");
        log("           opts.setExperimentalOption(\"useAutomationExtension\", false);");
        log("       })");
        log("       .build();");

        log("");
        log("── 6. Desde config.properties (ConfigLoader)");
        log("   Web web = Web.init(ConfigLoader.webConfig());");
        log("   // Lee app.url, app.project, driver.browser, driver.headless, etc.");
        log("   // Las system properties (-Dapp.url=...) tienen prioridad");

        log("");
        log("── 7. JUnit 5 (el proyecto consumidor gestiona el ciclo de vida)");
        log("   @BeforeEach void setUp()    { web = Web.init(config); }");
        log("   @AfterEach  void tearDown() { web.close(); }");

        log("");
        log("── 8. TestNG");
        log("   @BeforeMethod public void setUp()    { web = Web.init(config); }");
        log("   @AfterMethod  public void tearDown() { web.close(); }");

        log("");
        log("── 9. Cucumber");
        log("   @Before public void setUp()    { web = Web.init(config); WebContext.set(web); }");
        log("   @After  public void tearDown() { web.close(); WebContext.remove(); }");

        log("");
        log("── 10. Main de Java puro");
        log("   Web web = Web.init(config);");
        log("   try { web.nav.goTo(...); } finally { web.close(); }");
    }

    // =========================================================================
    // [HEAL-A] — HealingClient directo
    // =========================================================================

    private static void demoServicioDirecto() throws Exception {
        seccion("HEAL-A", "HealingClient — acceso directo al servicio de healing");

        String healingUrl = ConfigLoader.get("healing.url");
        String project    = ConfigLoader.get("app.project");

        HealingClient client = new HealingClient(healingUrl);
        try {
            paso("A1", "/health — verificar que el servicio está activo");
            try {
                Map<String, Object> health = client.getHealth();
                log("  Respuesta: %s", health);
            } catch (Exception e) {
                log("  WARN: servicio no alcanzable (%s)", e.getMessage());
            }

            paso("A2", "/metrics/:project — estadísticas de sanaciones");
            try {
                Map<String, Object> metrics = client.getMetrics(project);
                log("  /metrics/%s → %s", project, metrics);
            } catch (Exception e) {
                log("  WARN: %s", e.getMessage());
            }

            paso("A3", "/history/:project — últimos eventos de healing");
            try {
                Map<String, Object> history = client.getHistory(project, 5);
                log("  /history/%s (últimas 5) → %s", project, history);
            } catch (Exception e) {
                log("  WARN: %s", e.getMessage());
            }
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // [HEAL-B] — Framework Web completo: acciones, healing y HealContext
    // =========================================================================

    private static void demoFrameworkCompleto() {
        seccion("HEAL-B", "Framework Web — acciones + healing + HealContext");

        WebConfig config = ConfigLoader.webConfig();
        Web web = Web.init(config);
        WebContext.set(web);

        try {

            // ── Elementos de la demo app ──────────────────────────────────────
            Element username  = Element.id("input-username").label("Campo usuario");
            Element password  = Element.id("input-password").label("Campo contraseña");
            Element loginBtn  = Element.id("btn-login").label("Botón login");
            Element clearBtn  = Element.id("btn-clear").label("Botón limpiar");
            Element resultado = Element.id("login-result").label("Resultado login");

            // ── Registro de baselines ─────────────────────────────────────────
            paso("B1", "Registrando baselines (mientras los selectores funcionan)");
            web.healing.register(username);
            web.healing.register(password);
            web.healing.register(loginBtn, "demo_btn_login");
            web.healing.register(clearBtn);
            log("  Baselines registrados correctamente");

            // ── API fluida de acciones ────────────────────────────────────────
            paso("B2", "API fluida: type → border / click → blink / read");
            web.actions.type(username, "admin").border();
            web.actions.type(password, "secret").highlightSuccess();
            web.actions.click(loginBtn).scroll().blink(2);
            web.waits.untilVisible(resultado);
            String texto = web.actions.click(resultado).scroll().read();
            log("  Resultado leído: \"%s\"", texto);
            web.actions.click(clearBtn).highlightInfo();
            web.waits.untilPageReady();

            // ── Mutar DOM y ejercer healing básico ───────────────────────────
            paso("B3", "Simular selector roto → heal básico");
            JavascriptExecutor js = (JavascriptExecutor) web.driver;
            js.executeScript(
                "document.getElementById('input-username').id = 'input-username-v2';" +
                "document.getElementById('btn-login').id      = 'btn-login-v2';"
            );
            log("  DOM mutado: input-username → v2 | btn-login → v2");

            try {
                WebElement healed = web.healing.heal(username, "demo_campo_usuario");
                log("  Campo sanado: <%s> id='%s'", healed.getTagName(), healed.getAttribute("id"));
                web.actions.type(username, "admin_healed").border();
            } catch (Exception e) {
                log("  INFO: healing no resolvió '%s': %s", username.getDisplayLabel(), e.getMessage());
            }

            try {
                web.healing.healAndClick(loginBtn);
                log("  Click ejecutado sobre botón sanado");
            } catch (Exception e) {
                log("  INFO: healing no resolvió '%s': %s", loginBtn.getDisplayLabel(), e.getMessage());
            }

            // ── HealContext — filtros de contexto para el motor DOM ───────────
            paso("B4", "HealContext — afinar búsqueda con anchors, container, form y excludeIds");

            log("  Ejemplo 1: anchor por id (principal) + anchor por texto (secundario)");
            HealContext ctxAnchors = HealContext.create()
                .anchorById("campo-monto")         // type="id",  weight=40
                .anchorByText("Datos de pago")     // type="text", weight=30
                .anchorByName("importe")           // type="css", value="[name='importe']", weight=35
                .anchorByAriaLabel("Monto");       // type="css", value="[aria-label='Monto']", weight=25
            log("  ctx anchors: %d anchors configurados", ctxAnchors.getAnchors().size());

            log("  Ejemplo 2: anchor CSS + container por clase (modal)");
            HealContext ctxModal = HealContext.create()
                .anchor("css", ".login-panel", 20)
                .inContainerClass("modal-body");   // buscar solo dentro del modal
            log("  ctx modal: containerId=%s, containerClass=%s",
                ctxModal.getContainerId(), ctxModal.getContainerClass());

            log("  Ejemplo 3: form_id + excludeIds (evitar homónimos en header)");
            HealContext ctxForm = HealContext.create()
                .inForm("loginForm")
                .excludeId("header-close-btn", "nav-logout", "topbar-x");
            log("  ctx form: formId=%s, excludeIds=%s",
                ctxForm.getFormId(), ctxForm.getExcludeIds());

            log("  Ejemplo 4: container_id estricto para grid/tabla");
            HealContext ctxGrid = HealContext.create()
                .anchor("css", "tr[aria-selected='true']", 40)
                .inContainer("main-grid-panel");
            log("  ctx grid: containerId=%s", ctxGrid.getContainerId());

            log("  Uso con healAndClick:");
            log("  web.healing.healAndClick(Element.id(\"btn-pagar\"), ctxForm);");

            log("  Uso con healAndType:");
            log("  web.healing.healAndType(Element.id(\"campo\"), \"valor\", ctxModal);");

            log("  Uso con heal explícito:");
            log("  web.healing.heal(Element.id(\"btn\"), \"TC_001\", ctxAnchors);");

            // ── Assertions ───────────────────────────────────────────────────
            paso("B5", "Assertions: assertThat + softAssert");
            log("  web.assertThat(resultado).isVisible().hasText(\"Login exitoso\");");
            log("  web.softAssert(sa -> {");
            log("      sa.check(resultado).isVisible();");
            log("      sa.check(loginBtn).isEnabled();");
            log("  });");

            // ── Table helper ─────────────────────────────────────────────────
            paso("B6", "Table helper");
            log("  Table tabla = web.table(Element.id(\"tabla-usuarios\"));");
            log("  String nombre = tabla.cell(0, \"Nombre\");");
            log("  tabla.rowWhere(\"Estado\", \"Activo\").click();");
            log("  int filas = tabla.rowCount();");

            // ── PopupGuard ───────────────────────────────────────────────────
            paso("B7", "PopupGuard — registrar y manejar popups automáticamente");
            log("  web.popupGuard.register(\"Cookie Banner\",");
            log("      By.id(\"cookie-consent\"),");
            log("      d -> d.findElement(By.id(\"btn-accept-all\")).click());");
            log("  web.popupGuard.safely(() -> web.actions.click(loginBtn));");

        } finally {
            web.close();
            WebContext.remove();
        }
    }

    // =========================================================================
    // [STABLE] — Familia de acciones estables (AbstractActionGroup)
    // =========================================================================

    private static void demoStableActions() {
        seccion("STABLE", "Stable Actions — ClickActions / WriteActions / ReadActions");

        log("Las stable actions integran StabilityWait + UiWatchdog + reintento automático.");
        log("Se crean con un WebDriver y se usan independientemente del framework Web.");
        log("");

        // Ejemplo de instanciación standalone (requiere un driver activo)
        log("── Instanciación standalone (solo driver):");
        log("   ClickActions  clicks  = new ClickActions(driver);");
        log("   WriteActions  writes  = new WriteActions(driver);");
        log("   ReadActions   reads   = new ReadActions(driver);");
        log("   ScrollActions scrolls = new ScrollActions(driver);");
        log("   SelectActions selects = new SelectActions(driver);");
        log("   VisualActions visuals = new VisualActions(driver);");

        log("");
        log("── Instanciación con configuración personalizada:");
        log("   StabilityConfig cfg = StabilityConfig.defaultConfig();");
        log("   ClickActions clicks = new ClickActions(driver, cfg, stabilityWait, watchdog, logger);");

        log("");
        log("── ClickActions:");
        log("   clicks.click(By.id(\"btn-submit\"));");
        log("   clicks.click(webElement);");
        log("   clicks.doubleClick(By.xpath(\"//tr[1]\"));");
        log("   clicks.jsClick(By.id(\"btn-oculto\"));  // via JS cuando el click normal falla");

        log("");
        log("── WriteActions:");
        log("   writes.write(By.id(\"campo\"), \"valor\");");
        log("   writes.clear(By.id(\"campo\"));");
        log("   writes.append(By.id(\"campo\"), \" texto adicional\");");
        log("   writes.pressEnter(By.id(\"campo\"));");
        log("   writes.pressTab(By.id(\"campo\"));");

        log("");
        log("── ReadActions:");
        log("   String texto  = reads.text(By.id(\"label\"));");
        log("   String valor  = reads.attribute(By.id(\"input\"), \"value\");");
        log("   boolean vis   = reads.isVisible(By.id(\"panel\"));");
        log("   boolean existe = reads.exists(By.id(\"error-msg\"));");

        log("");
        log("── SelectActions:");
        log("   selects.selectByText(By.id(\"combo\"), \"Opción A\");");
        log("   selects.selectByValue(By.id(\"combo\"), \"OPT_A\");");
        log("   selects.selectByIndex(By.id(\"combo\"), 0);");
        log("   String sel = selects.getSelectedText(By.id(\"combo\"));");

        log("");
        log("── Comportamiento ante errores:");
        log("   - ElementClickInterceptedException → watchdog detecta el overlay → fallback JS");
        log("   - InvalidElementStateException    → watchdog revisa el estado → retry");
        log("   - StaleElementReferenceException  → reintento automático transparente");
    }

    // =========================================================================
    // [SIEBEL] — SiebelWaits standalone
    // =========================================================================

    private static void demoSiebelWaits() {
        seccion("SIEBEL", "SiebelWaits — esperas para apps lentas (Siebel, SAP, Oracle)");

        log("SiebelWaits ya no usa herencia. Se crea con un WebDriver:");
        log("");
        log("   SiebelWaits siebel = new SiebelWaits(web.driver);");
        log("");
        log("── waitForPageReady()  — readyState + jQuery.active + spinner desaparecido");
        log("   siebel.waitForPageReady();");
        log("");
        log("── waitReady(xpath)    — visible + habilitado + tolera StaleElement");
        log("   WebElement el = siebel.waitReady(\"//input[@name='monto']\");");
        log("");
        log("── safeClick(xpath)    — reintenta hasta 3x ante StaleElement e interceptaciones");
        log("   siebel.safeClick(\"//button[@id='confirmar']\");");
        log("");
        log("── safeSendKeys(xpath, valor) — verifica que el valor quedó escrito");
        log("   siebel.safeSendKeys(\"//input[@name='monto']\", \"1500.00\");");
        log("");
        log("── waitGone(xpath)     — espera que el elemento desaparezca del DOM");
        log("   siebel.waitGone(\"//div[@class='modal-siebel']\");");
        log("");
        log("Spinner XPath por defecto:");
        log("   SiebelWaits.SPINNER_XPATH = \"%s\"", SiebelWaits.SPINNER_XPATH);
        log("");
        log("Uso con cualquier runner:");
        log("   // JUnit 5");
        log("   @BeforeEach void setUp() {");
        log("       web    = Web.init(config);");
        log("       siebel = new SiebelWaits(web.driver);");
        log("   }");
    }

    // =========================================================================
    // [WATCH] — UiWatchdog + StabilityWait standalone
    // =========================================================================

    private static void demoWatchdogYStability() {
        seccion("WATCH", "UiWatchdog + StabilityWait — detección y espera inteligente");

        log("── StabilityConfig — configuración compartida:");
        log("   StabilityConfig cfg = StabilityConfig.defaultConfig();");
        log("   // defaultTimeoutSeconds=8, pollingMillis=250, stabilityCacheWindow=800ms");
        log("");

        log("── WatchdogConfig — selectores de lo que puede bloquear la UI:");
        log("   WatchdogConfig wCfg = new WatchdogConfig(");
        log("       true,                          // enabled");
        log("       true,                          // loadersBlockExecution");
        log("       true,                          // overlaysBlockExecution");
        log("       Arrays.asList(\".spinner\", \".loading\"),   // loaderSelectors");
        log("       Arrays.asList(\".modal-backdrop\"),          // overlaySelectors");
        log("       Arrays.asList(\"[role='dialog']\"),          // modalSelectors");
        log("       Arrays.asList(\".alert-danger\", \".error\") // errorSelectors");
        log("   );");
        log("");

        log("── UiWatchdog — detectar qué está bloqueando la pantalla:");
        log("   UiWatchdog watchdog = new UiWatchdog(driver, wCfg);");
        log("   WatchdogResult result = watchdog.inspect();");
        log("   // result.getStatus() → CLEAN | LOADER_DETECTED | MODAL_DETECTED |");
        log("   //                      OVERLAY_DETECTED | ERROR_DETECTED | ALERT_DETECTED");
        log("   if (result.isBlocking()) {");
        log("       log(\"Bloqueado: \" + result.toShortLog());");
        log("   }");
        log("");

        log("── StabilityWait — esperar que la UI esté estable:");
        log("   StabilityWait stability = new StabilityWait(driver, cfg);");
        log("   stability.waitUntilReady();           // cacheado — omite si ya se chequeó recientemente");
        log("   stability.waitUntilReadyFast();       // sin jQuery, solo doc + loaders");
        log("   stability.waitUntilReady(Duration.ofSeconds(15));  // timeout explícito");
        log("   stability.invalidateCache();          // forzar chequeo completo la próxima vez");
        log("");

        log("── Integración: StabilityWait lee WatchdogConfig desde StabilityConfig:");
        log("   StabilityConfig cfg = StabilityConfig.defaultConfig();");
        log("   // cfg.getWatchdogConfig() → WatchdogConfig con selectores del framework-config.yml");
        log("   StabilityWait stability = new StabilityWait(driver, cfg);");
        log("   UiWatchdog    watchdog  = new UiWatchdog(driver, cfg.getWatchdogConfig());");
    }

    // =========================================================================
    // [REPO] — RepairRepository (cache SQLite local)
    // =========================================================================

    private static void demoRepairRepository() {
        seccion("REPO", "RepairRepository — cache local SQLite de reparaciones");

        log("Guarda el historial de selectores reparados en SQLite para reutilizarlos");
        log("sin llamar al servicio de healing si la reparación ya es conocida.");
        log("");

        // Usar BD en memoria para no dejar archivo en el sistema durante la demo
        RepairRepository repo = new RepairRepository("jdbc:sqlite:repair-history.db");

        paso("REPO-1", "saveOrUpdate — guardar una reparación");
        SuggestedLocator locator = new SuggestedLocator();
        locator.setType("xpath");
        locator.setValue("//button[contains(@class,'btn-primary') and text()='Login']");
        locator.setScore(87);
        locator.setReason("Coincidencia por texto + clase CSS");

        repo.saveOrUpdate("MY_APP", "http://localhost:9000/login",
            "xpath", "//button[@id='btn-login']", locator);
        log("  Guardado: score=%d, reason='%s'", locator.getScore(), locator.getReason());

        paso("REPO-2", "findApprovedRepair — recuperar reparación con score mínimo 80");
        SuggestedLocator cached = repo.findApprovedRepair(
            "MY_APP", "http://localhost:9000/login",
            "xpath", "//button[@id='btn-login']", 80);

        if (cached != null) {
            log("  Cache HIT: %s = '%s' (score=%d)", cached.getType(), cached.getValue(), cached.getScore());
            log("  toBy() = %s", cached.toBy());
        } else {
            log("  Cache MISS — no hay reparación aprobada con score >= 80");
        }

        paso("REPO-3", "hasSuccessfulRepairs — ¿hay reparaciones exitosas en esta página?");
        boolean tiene = repo.hasSuccessfulRepairs("MY_APP", "http://localhost:9000/login");
        log("  hasSuccessfulRepairs = %b", tiene);

        paso("REPO-4", "UPSERT — segunda llamada incrementa times_seen sin duplicar");
        locator.setScore(91);
        repo.saveOrUpdate("MY_APP", "http://localhost:9000/login",
            "xpath", "//button[@id='btn-login']", locator);
        SuggestedLocator updated = repo.findApprovedRepair(
            "MY_APP", "http://localhost:9000/login",
            "xpath", "//button[@id='btn-login']", 80);
        if (updated != null) {
            log("  Score actualizado: %d (times_seen incrementado automáticamente)", updated.getScore());
        }

        log("");
        log("── Inicialización real (con archivo en disco):");
        log("   RepairRepository repo = new RepairRepository(\"jdbc:sqlite:repair-history.db\");");
        log("   // Crea la tabla y ejecuta migraciones automáticamente");
    }

    // =========================================================================
    // [AUDIT] — SmartAuditRecorder
    // =========================================================================

    private static void demoSmartAuditRecorder() {
        seccion("AUDIT", "SmartAuditRecorder — trazabilidad del ciclo de healing");

        log("Registra el ciclo de vida completo de un intento de healing.");
        log("No depende de JUnit ni de ningún runner.");
        log("");

        paso("AUDIT-1", "Ciclo exitoso: directo → sin healing necesario");
        SmartAuditRecorder recorder = new SmartAuditRecorder(
            "MY_APP", "AUTO",
            "//button[@id='btn-login']",
            "http://localhost:9000/login");

        recorder.record(SmartAuditEventType.START, "Iniciando localización del elemento");
        recorder.record(SmartAuditEventType.DIRECT_TRY, "Intentando con selector original");
        recorder.record(SmartAuditEventType.DIRECT_SUCCESS, "Elemento encontrado directamente");
        recorder.finishSuccess("//button[@id='btn-login']", "DIRECT");

        log("  Reporte: %s", recorder.getReport());

        paso("AUDIT-2", "Ciclo con healing: fallo → cache → IA → reparado");
        SmartAuditRecorder recorder2 = new SmartAuditRecorder(
            "MY_APP", "AUTO",
            "//button[@id='btn-login']",
            "http://localhost:9000/login");

        recorder2.record(SmartAuditEventType.START, "Iniciando localización");
        recorder2.record(SmartAuditEventType.DIRECT_TRY, "Intentando con selector original");
        recorder2.record(SmartAuditEventType.DIRECT_FAIL, "NoSuchElementException: selector roto");
        recorder2.record(SmartAuditEventType.CACHE_LOOKUP, "Buscando en cache SQLite local");
        recorder2.record(SmartAuditEventType.CACHE_MISS, "Sin resultado en cache");
        recorder2.record(SmartAuditEventType.IA_REQUEST, "Enviando al servicio de healing...");
        recorder2.record(SmartAuditEventType.IA_RESPONSE, "Respuesta recibida del servicio");
        recorder2.record(SmartAuditEventType.AUTO_HEAL_APPLIED,
            "Selector reparado aplicado: //button[contains(@class,'btn-primary')]");
        recorder2.finishSuccess(
            "//button[contains(@class,'btn-primary')]", "HEALING_SERVICE");

        log("  Reporte: %s", recorder2.getReport());

        paso("AUDIT-3", "Ciclo fallido: ni cache ni IA pudieron resolver");
        SmartAuditRecorder recorder3 = new SmartAuditRecorder(
            "MY_APP", "AUTO",
            "//button[@id='btn-inexistente']",
            "http://localhost:9000/login");

        recorder3.record(SmartAuditEventType.START, "Iniciando localización");
        recorder3.record(SmartAuditEventType.DIRECT_FAIL, "Selector roto");
        recorder3.record(SmartAuditEventType.CACHE_MISS, "Sin cache");
        recorder3.record(SmartAuditEventType.IA_REQUEST, "Consultando servicio...");
        recorder3.record(SmartAuditEventType.IA_RESPONSE, "HTTP 422: ningún motor resolvió");
        recorder3.finishFailure();

        log("  Reporte: %s", recorder3.getReport());
    }

    // =========================================================================
    // Helpers de salida
    // =========================================================================

    private static void banner(String titulo) {
        String linea = "=".repeat(60);
        System.out.println("\n" + linea);
        System.out.println("  " + titulo);
        System.out.println(linea + "\n");
    }

    private static void seccion(String tag, String descripcion) {
        System.out.printf("%n%n[%s] %s%n%s%n", tag, descripcion, "-".repeat(55));
    }

    private static void paso(String num, String descripcion) {
        System.out.printf("%n  (%s) %s%n", num, descripcion);
    }

    private static void log(String msg, Object... args) {
        System.out.println("  " + (args.length == 0 ? msg : String.format(msg, args)));
    }
}
