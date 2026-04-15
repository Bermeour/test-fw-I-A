# Self-Healing Framework

Framework Java sobre Selenium 4 con auto-reparación de selectores, acciones robustas,
esperas inteligentes, assertions fluidas, manejo de popups y caché local de reparaciones.

> **Versión:** 2.0.0 | **Java:** 11+ | **Selenium:** 4.20 | **Maven:** 3.6+

**Runner-agnostic** — el framework no depende de JUnit, TestNG ni Cucumber.
El proyecto consumidor elige su runner y gestiona el ciclo de vida con `Web.init()` / `web.close()`.

---

## Índice

1. [Requisitos previos](#1-requisitos-previos)
2. [Dependencia Maven](#2-dependencia-maven)
3. [Inicio rápido](#3-inicio-rápido)
4. [Configuración](#4-configuración)
5. [Elementos](#5-elementos)
6. [Acciones](#6-acciones)
7. [Stable Actions — acciones robustas](#7-stable-actions--acciones-robustas)
8. [Esperas](#8-esperas)
9. [Self-Healing — guía completa](#9-self-healing--guía-completa)
10. [HealContext — filtros de contexto](#10-healcontext--filtros-de-contexto)
11. [RepairRepository — caché local](#11-repairrepository--caché-local)
12. [SmartAuditRecorder — trazabilidad](#12-smartauditrecorder--trazabilidad)
13. [SiebelWaits — apps lentas](#13-siebelwaits--apps-lentas)
14. [UiWatchdog + StabilityWait](#14-uiwatchdog--stabilitywait)
15. [Assertions](#15-assertions)
16. [Tablas](#16-tablas)
17. [PopupGuard](#17-popupguard)
18. [Cookies y Storage](#18-cookies-y-storage)
19. [Integración con runners](#19-integración-con-runners)
20. [Referencia rápida de la API](#20-referencia-rápida-de-la-api)

---

## 1. Requisitos previos

| Requisito | Versión mínima | Notas |
|---|---|---|
| Java | 11 | Compatible con 17 y 21 |
| Maven | 3.6 | |
| Chrome / Firefox / Edge | Cualquier reciente | Selenium Manager descarga el driver automáticamente |
| Servicio self-healing | corriendo en `localhost:8765` | Microservicio Python independiente |

El framework **no requiere instalar ChromeDriver manualmente**.

---

## 2. Dependencia Maven

```xml
<dependency>
    <groupId>com.selfhealing</groupId>
    <artifactId>self-healing-framework</artifactId>
    <version>2.0.0</version>
</dependency>
```

El framework **no arrastra JUnit como dependencia transitiva**. Declara tu runner por separado:

```xml
<!-- JUnit 5 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>

<!-- TestNG -->
<dependency>
    <groupId>org.testng</groupId>
    <artifactId>testng</artifactId>
    <version>7.9.0</version>
    <scope>test</scope>
</dependency>
```

---

## 3. Inicio rápido

### Con `config.properties`

```properties
# src/test/resources/config.properties
app.url     = http://mi-aplicacion.com
app.project = portal_rrhh
healing.url = http://localhost:8765
driver.browser  = chrome
driver.headless = false
driver.timeout.element  = 30
driver.timeout.pageLoad = 60
```

```java
// JUnit 5
class LoginTest {
    Web web;

    @BeforeEach
    void setUp() { web = Web.init(ConfigLoader.webConfig()); }

    @AfterEach
    void tearDown() { web.close(); }

    @Test
    void loginExitoso() {
        web.actions.type(Element.id("username").label("Usuario"), "admin");
        web.actions.type(Element.id("password").label("Contraseña"), "secret");
        web.actions.click(Element.id("btn-login").label("Login"));
        web.assertThat(Element.id("dashboard")).isVisible();
    }
}
```

### Sin runner — `main()` puro

```java
public class Main {
    public static void main(String[] args) {
        Web web = Web.init("http://mi-app.com", "mi-proyecto");
        try {
            web.actions.type(Element.id("username"), "admin");
            web.actions.click(Element.id("btn-login"));
            web.waits.untilVisible(Element.id("dashboard"));
        } finally {
            web.close();
        }
    }
}
```

---

## 4. Configuración

### `config.properties` — referencia completa

```properties
# ── Aplicación ─────────────────────────────────────────────────
app.url     = http://localhost:9000
app.project = mi_proyecto       # único por proyecto, no cambiar tras crear baselines

# ── Servicio de self-healing ────────────────────────────────────
healing.url             = http://localhost:8765
# default | siebel | angular | legacy
healing.scoring_profile = default

# ── Driver ─────────────────────────────────────────────────────
driver.browser  = chrome        # chrome | firefox | edge
driver.headless = false         # true en CI/CD
driver.timeout.element  = 30
driver.timeout.pageLoad = 60

# ── Proxy (dejar vacíos si no hay proxy) ───────────────────────
proxy.host =
proxy.port =

# ── Chrome: argumentos extra ────────────────────────────────────
# chrome.args = --window-size=1920,1080,--disable-extensions

# ── Chrome: preferencias ────────────────────────────────────────
# chrome.pref.download.default_directory = C:/descargas
# chrome.pref.download.prompt_for_download = false
```

### `WebConfig` builder — configuración programática

```java
WebConfig config = WebConfig.builder()
    .url("http://mi-app.com")
    .project("portal_rrhh")
    .browser(WebConfig.Browser.CHROME)         // CHROME | FIREFOX | EDGE
    .headless(true)
    .timeoutSeconds(45)
    .pageLoadTimeoutSeconds(90)
    .scoringProfile(WebConfig.ScoringProfile.ANGULAR)  // DEFAULT|SIEBEL|ANGULAR|LEGACY
    .healingUrl("http://localhost:8765")
    .proxy("proxy.empresa.com", 8080)
    .chromeArg("--window-size=1920,1080")
    .chromeArg("--disable-extensions")
    .chromePref("download.default_directory", "C:/descargas")
    .chromePref("download.prompt_for_download", false)
    .customizeChrome(opts -> {
        // Escape hatch: opciones avanzadas aplicadas al final
        opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        opts.setExperimentalOption("useAutomationExtension", false);
    })
    .customizeFirefox(opts -> {
        opts.addPreference("browser.download.folderList", 2);
    })
    .build();

Web web = Web.init(config);
```

### Sobreescrituras en CI/CD

```bash
mvn test \
  -Dapp.url=http://staging.empresa.com \
  -Dapp.project=portal_rrhh \
  -Ddriver.headless=true \
  -Dhealing.scoring_profile=angular
```

---

## 5. Elementos

`Element` describe un localizador. Se declara una vez y se reutiliza en todos los métodos.

```java
// Tipos de localizador
Element.id("btn-login")
Element.xpath("//button[@id='btn-login']")
Element.css(".modal .btn-primary")
Element.name("username")
Element.text("Iniciar sesión")             // por texto visible exacto

// Etiqueta legible — aparece en logs y mensajes de error
Element boton = Element.id("btn-login").label("Botón Login");
// Log: "[STEP] click → Botón Login"
// Error: "isVisible(Botón Login): elemento no encontrado"
```

**Buena práctica:**

```java
// ✅ Constante estática, etiquetada, reutilizable
private static final Element BTN_GUARDAR = Element.id("btn-save").label("Guardar");

// ❌ Inline sin etiqueta — difícil de mantener
driver.findElement(By.id("btn-save")).click();
```

---

## 6. Acciones

```java
// ── Escritura ──────────────────────────────────────────────────
web.actions.type(campo, "admin");             // limpiar + escribir
web.actions.append(campo, " apellido");       // añadir sin limpiar
web.actions.typeSlow(campo, "admin", 80);     // 80ms entre teclas (autocomplete)
web.actions.typeJS(campo, "valor");           // vía JavaScript
web.actions.clear(campo);
web.actions.pressKey(campo, Keys.ENTER);

// ── Clicks ─────────────────────────────────────────────────────
web.actions.click(boton);
web.actions.clickJS(boton);                   // vía JS — evita overlays
web.actions.doubleClick(elemento);
web.actions.rightClick(elemento);

// ── Selects ────────────────────────────────────────────────────
web.actions.select.byText(pais, "Colombia");
web.actions.select.byValue(pais, "CO");
web.actions.select.byIndex(pais, 0);

// ── Lectura ────────────────────────────────────────────────────
String texto  = web.actions.read(elemento);
String valor  = web.actions.readValue(inputField);
boolean vis   = web.actions.isVisible(elemento);
boolean act   = web.actions.isEnabled(boton);

// ── Scroll ─────────────────────────────────────────────────────
web.actions.scroll.toElement(elemento);
web.actions.scroll.toTop();
web.actions.scroll.toBottom();

// ── Visual (depuración) ────────────────────────────────────────
web.actions.visual.highlight(elemento);
web.actions.visual.blink(elemento, 3);

// ── Formularios ────────────────────────────────────────────────
Map<Element, String> campos = new LinkedHashMap<>();
campos.put(nombre,   "Juan García");
campos.put(email,    "juan@empresa.com");
campos.put(telefono, "600123456");
web.actions.fillForm(campos);

// ── Drag & Drop ────────────────────────────────────────────────
web.actions.drag.dragAndDrop(tarjeta, columna);

// ── Alertas ────────────────────────────────────────────────────
web.actions.alert.readAndAccept();
web.actions.alert.dismiss();
web.actions.alert.typeAndAccept("texto");

// ── API fluida encadenable ─────────────────────────────────────
web.actions.type(campo, "admin").border();            // escribir + resaltar
web.actions.click(boton).scroll().blink(2);           // click + scroll + parpadeo
String texto2 = web.actions.click(elem).scroll().read(); // click + scroll + leer
```

---

## 7. Stable Actions — acciones robustas

Las **Stable Actions** integran `StabilityWait` + `UiWatchdog` + reintentos automáticos.
Son independientes del framework `Web` y se pueden usar en cualquier contexto.

```java
// Instanciación standalone — solo necesitan un WebDriver
ClickActions  clicks  = new ClickActions(driver);
WriteActions  writes  = new WriteActions(driver);
ReadActions   reads   = new ReadActions(driver);
ScrollActions scrolls = new ScrollActions(driver);
SelectActions selects = new SelectActions(driver);
VisualActions visuals = new VisualActions(driver);
```

```java
// ── ClickActions ───────────────────────────────────────────────
clicks.click(By.id("btn-submit"));
clicks.click(webElement);
clicks.doubleClick(By.xpath("//tr[1]"));
clicks.jsClick(By.id("btn-oculto"));       // fallback JS automático si click falla

// ── WriteActions ───────────────────────────────────────────────
writes.write(By.id("campo"), "valor");
writes.clear(By.id("campo"));
writes.append(By.id("campo"), " texto adicional");
writes.pressEnter(By.id("campo"));
writes.pressTab(By.id("campo"));

// ── ReadActions ────────────────────────────────────────────────
String texto   = reads.text(By.id("label"));
String valor   = reads.attribute(By.id("input"), "value");
boolean vis    = reads.isVisible(By.id("panel"));
boolean existe = reads.exists(By.id("error-msg"));

// ── SelectActions ──────────────────────────────────────────────
selects.selectByText(By.id("combo"), "Opción A");
selects.selectByValue(By.id("combo"), "OPT_A");
selects.selectByIndex(By.id("combo"), 0);
String seleccion = selects.getSelectedText(By.id("combo"));
```

**Comportamiento ante errores:**

| Excepción | Comportamiento |
|---|---|
| `ElementClickInterceptedException` | Watchdog detecta el overlay → fallback JS |
| `InvalidElementStateException` | Watchdog revisa el estado → reintento |
| `StaleElementReferenceException` | Reintento automático transparente |

---

## 8. Esperas

```java
// Esperas básicas
web.waits.untilVisible(elemento);
web.waits.untilInvisible(elemento);
web.waits.untilClickable(elemento);
web.waits.untilText(elemento, "Bienvenido");
web.waits.untilPageReady();               // readyState + jQuery.active

// Con timeout explícito
web.waits.untilVisible(elemento, Duration.ofSeconds(15));

// FluentWait personalizado
new FluentWait<>(web.driver)
    .withTimeout(Duration.ofSeconds(20))
    .pollingEvery(Duration.ofMillis(300))
    .ignoring(NoSuchElementException.class)
    .until(d -> d.findElement(By.id("resultado")).isDisplayed());
```

---

## 9. Self-Healing — guía completa

### Flujo de registro y sanación

```
1. register()  → guardar baseline cuando el selector funciona
2. DOM cambia  → el selector original ya no existe
3. heal()      → servicio localiza el elemento y devuelve selector reparado
4. feedback    → el cliente informa si el elemento fue encontrado (en background)
```

### 9.1 Registrar baseline

```java
// Con testId automático derivado del label del elemento
web.healing.register(Element.id("btn-login").label("Botón Login"));

// Con testId explícito (recomendado — más estable entre ejecuciones)
web.healing.register(Element.id("btn-login"), "TC_001_btn_login");
```

El baseline incluye: tag, id, texto, clases, atributos ARIA, contexto del padre y screenshot del elemento.

### 9.2 Sanar un selector roto

```java
// Heal básico — devuelve el WebElement encontrado (original o reparado)
WebElement el = web.healing.heal(Element.id("btn-login"));

// Con testId explícito
WebElement el = web.healing.heal(Element.id("btn-login"), "TC_001_btn_login");

// Sanar y hacer click
web.healing.healAndClick(Element.id("btn-login"));

// Sanar y escribir
web.healing.healAndType(Element.id("campo-usuario"), "admin");
```

### 9.3 Perfiles de scoring

| Perfil | Cuándo usarlo |
|---|---|
| `DEFAULT` | La mayoría de apps web |
| `SIEBEL` | Siebel CRM, SAP, Oracle Forms — prioriza jerarquía DOM |
| `ANGULAR` | Apps Angular, React, Vue — adapta el scoring para componentes |
| `LEGACY` | Apps con IDs dinámicos o generados automáticamente |

```properties
healing.scoring_profile = siebel
```

### 9.4 Motores de healing

El servicio ejecuta dos motores en paralelo:

- **Motor DOM** — analiza atributos, texto, clases, jerarquía y proximidad. Devuelve XPath.
- **Motor CV** — template matching con OpenCV sobre el screenshot. Devuelve `coords::x,y`.

El selector `coords::x,y` se resuelve automáticamente con `document.elementFromPoint(x, y)`.

### 9.5 HealingClient directo

```java
HealingClient client = new HealingClient("http://localhost:8765");

// Monitorización
Map<String, Object> health  = client.getHealth();
Map<String, Object> metrics = client.getMetrics("mi_proyecto");
Map<String, Object> history = client.getHistory("mi_proyecto", 10);

client.close();
```

---

## 10. HealContext — filtros de contexto

`HealContext` permite afinar la búsqueda del motor DOM cuando la página tiene
múltiples elementos similares. Todos los filtros son opcionales e independientes.

```java
HealContext ctx = HealContext.create()
    .anchorById("campo-monto")          // priorizar candidatos cerca de este ID
    .anchorByText("Datos de pago")      // y cerca de este texto visible
    .inForm("form-pago")                // buscar solo dentro de este <form>
    .excludeId("btn-cancelar-header");  // ignorar este ID aunque tenga score alto

web.healing.heal(Element.id("btn-pagar"), "TC_pago", ctx);
web.healing.healAndClick(Element.id("btn-pagar"), ctx);
web.healing.healAndType(Element.id("campo"), "valor", ctx);
```

### Tipos de anchor

```java
HealContext.create()
    .anchorById("campo-referencia")         // type="id",   weight=40
    .anchorByText("Texto visible")          // type="text", weight=30
    .anchorByName("nombre-campo")           // type="css",  value="[name='...']", weight=35
    .anchorByAriaLabel("Etiqueta ARIA")     // type="css",  value="[aria-label='...']", weight=25
    .anchor("css", "tr[aria-selected='true']", 40)  // CSS libre con peso personalizado
```

### Filtros de scope

```java
HealContext.create()
    .inContainer("panel-principal")         // solo descendientes del elemento con ese ID
    .inContainerClass("modal-body")         // ancestro con esa clase CSS
    .inContainerClass("modal-body active")  // ambas clases (AND)
    .inContainerClass("modal-body,form-section") // cualquiera (OR)
    .inForm("loginForm")                    // dentro del <form id="loginForm">
    .excludeId("header-close", "nav-logout") // IDs a ignorar aunque tengan score alto
```

### Guía rápida — cuándo usar cada filtro

| Situación | Filtro |
|---|---|
| Mismo botón en header y en el form | `excludeId` + `inForm` |
| Elemento dentro de un modal | `inContainerClass("modal-body")` |
| App Angular con múltiples formularios | `inForm` |
| App Siebel sin atributos estables | `anchor` por texto cercano |
| Grid con fila seleccionada | `anchor("css", "tr[aria-selected='true']", 40)` |

### Body completo enviado al servicio

```json
{
  "selector_type": "xpath",
  "selector_value": "//button[@id='btn-login']",
  "dom_html": "<html>...</html>",
  "project": "portal_rrhh",
  "test_id": "TC_001_login",
  "scoring_profile": "default",
  "screenshot_base64": "...",
  "anchors": [
    { "type": "id",   "value": "username_input", "weight": 40 },
    { "type": "text", "value": "Contraseña",      "weight": 30 },
    { "type": "css",  "value": "#login-area",     "weight": 20 }
  ],
  "exclude_ids": ["header-close-btn", "nav-logout"],
  "container_id": "main-content",
  "form_id": "loginForm"
}
```

---

## 11. RepairRepository — caché local

Almacena reparaciones aprobadas en SQLite local para evitar llamar al servicio
cuando ya existe una reparación conocida y confiable.

### Ciclo de vida de una entrada

```
saveOrUpdate() → entrada APPROVED
touch()        → incrementar times_seen (caché sigue siendo válida)
reject()       → marcar como REJECTED (selector ya no funciona en DOM)
```

```java
RepairRepository repo = new RepairRepository("jdbc:sqlite:repair-history.db");

// Guardar reparación recibida del servicio
SuggestedLocator locator = new SuggestedLocator();
locator.setType("xpath");
locator.setValue("//button[contains(@class,'btn-primary')]");
locator.setScore(87);
locator.setReason("Coincidencia por texto + clase CSS");

repo.saveOrUpdate("MY_APP", "http://app.com/login",
    "xpath", "//button[@id='btn-login']", locator);

// Consultar antes de llamar al servicio (TTL por defecto: 7 días)
SuggestedLocator cached = repo.findApprovedRepair(
    "MY_APP", "http://app.com/login",
    "xpath", "//button[@id='btn-login']",
    80);                     // score mínimo

if (cached != null) {
    driver.findElement(cached.toBy()).click();
    repo.touch("MY_APP", "http://app.com/login",
        "xpath", "//button[@id='btn-login']",
        cached.getType(), cached.getValue());   // actualizar last_seen
}

// Consultar con TTL personalizado
SuggestedLocator cached2 = repo.findApprovedRepair(
    "MY_APP", "http://app.com/login",
    "xpath", "//button[@id='btn-login']",
    80, 30);                 // 30 días de TTL

// Marcar como rechazada si el selector cacheado ya no funciona
repo.reject("MY_APP", "http://app.com/login",
    "xpath", "//button[@id='btn-login']",
    cached.getType(), cached.getValue(),
    "NoSuchElementException tras mutación del DOM");

// Verificar si la página ya tiene reparaciones previas
boolean tieneHistorial = repo.hasSuccessfulRepairs("MY_APP", "http://app.com/login");
```

---

## 12. SmartAuditRecorder — trazabilidad

Registra el ciclo de vida completo de cada intento de localización.
No depende de ningún runner.

```java
SmartAuditRecorder audit = new SmartAuditRecorder(
    "MY_APP", "AUTO",
    "//button[@id='btn-login']",
    "http://app.com/login");

audit.record(SmartAuditEventType.START,          "Iniciando localización");
audit.record(SmartAuditEventType.DIRECT_TRY,     "Intentando selector original");
audit.record(SmartAuditEventType.DIRECT_FAIL,    "NoSuchElementException");
audit.record(SmartAuditEventType.CACHE_LOOKUP,   "Buscando en caché SQLite");
audit.record(SmartAuditEventType.CACHE_MISS,     "Sin resultado en caché");
audit.record(SmartAuditEventType.IA_REQUEST,     "Enviando al servicio de healing");
audit.record(SmartAuditEventType.IA_RESPONSE,    "Selector reparado recibido");
audit.record(SmartAuditEventType.AUTO_HEAL_APPLIED, "Aplicando selector reparado");

// Finalizar
audit.finishSuccess("//button[contains(@class,'btn-primary')]", "HEALING_SERVICE");
// o
audit.finishFailure();

SmartAuditReport report = audit.getReport();
```

### Eventos disponibles

| Evento | Descripción |
|---|---|
| `START` | Inicio del intento de localización |
| `DIRECT_TRY / SUCCESS / FAIL` | Intento con selector original |
| `CACHE_LOOKUP / APPLIED / MISS` | Consulta y resultado del caché local |
| `IA_REQUEST / RESPONSE` | Llamada al servicio de healing |
| `AUTO_HEAL_APPLIED` | Selector reparado aplicado con éxito |

---

## 13. SiebelWaits — apps lentas

Utilidades para apps lentas e inestables (Siebel CRM, SAP, Oracle Forms).
No depende de ningún runner — se crea con un `WebDriver`.

```java
SiebelWaits siebel = new SiebelWaits(web.driver);

// Esperar que la página esté realmente lista:
// readyState + jQuery.active + spinner desaparecido
siebel.waitForPageReady();

// Esperar que el elemento esté visible Y habilitado
// (tolera StaleElementReferenceException — Siebel reconstruye el DOM)
WebElement el = siebel.waitReady("//input[@name='monto']");

// Click con reintento ante StaleElement y ElementClickInterceptedException
siebel.safeClick("//button[@id='confirmar']");

// Escritura con verificación de que el valor persistió
// (Siebel a veces borra lo que escribiste con un evento JS)
siebel.safeSendKeys("//input[@name='monto']", "1500.00");

// Esperar que el elemento desaparezca (ej: tras cerrar un diálogo)
siebel.waitGone("//div[@class='modal-siebel']");
```

El spinner XPath por defecto cubre `.spinner`, `.loading`, `.loader`:

```java
SiebelWaits.SPINNER_XPATH
// = "//*[contains(@class,'spinner') or contains(@class,'loading') or contains(@class,'loader')]"
```

---

## 14. UiWatchdog + StabilityWait

### UiWatchdog — detectar qué bloquea la UI

```java
WatchdogConfig config = new WatchdogConfig(
    true,                                            // enabled
    true,                                            // loadersBlockExecution
    true,                                            // overlaysBlockExecution
    Arrays.asList(".spinner", ".loading"),           // loaderSelectors
    Arrays.asList(".modal-backdrop", ".overlay"),    // overlaySelectors
    Arrays.asList("[role='dialog']"),                // modalSelectors
    Arrays.asList(".alert-danger", ".error")         // errorSelectors
);

UiWatchdog watchdog = new UiWatchdog(driver, config);
WatchdogResult result = watchdog.inspect();

// result.getStatus() → CLEAN | LOADER_DETECTED | OVERLAY_DETECTED |
//                      MODAL_DETECTED | ERROR_DETECTED | ALERT_DETECTED

if (result.isBlocking()) {
    System.out.println(result.toShortLog());
    // → "MODAL_DETECTED: [role='dialog'] — texto: '¿Confirmar pago?'"
}
```

### StabilityWait — esperar que la UI esté estable

```java
StabilityConfig cfg = StabilityConfig.defaultConfig();
// defaultTimeoutSeconds=8, pollingMillis=250, stabilityCacheWindow=800ms

StabilityWait stability = new StabilityWait(driver, cfg);

stability.waitUntilReady();                          // cacheado — omite si ya se chequeó recientemente
stability.waitUntilReadyFast();                      // sin jQuery, solo doc + loaders
stability.waitUntilReady(Duration.ofSeconds(15));    // timeout explícito
stability.invalidateCache();                         // forzar chequeo completo la próxima vez

boolean ok      = stability.isUiStableFull();        // readyState + jQuery + loaders + overlays
boolean okFast  = stability.isUiStableFast();        // readyState + loaders + overlays
```

---

## 15. Assertions

```java
// Assertions estrictas — falla al primer error
web.assertThat(elemento)
   .isVisible()
   .isEnabled()
   .hasText("Bienvenido, admin")
   .containsText("Bienvenido")
   .hasAttribute("class", "success")
   .hasValue("admin");

// Assertions suaves — acumula todos los fallos y los lanza al final
web.softAssert(sa -> {
    sa.check(titulo).hasText("Dashboard");
    sa.check(menu).isVisible();
    sa.check(usuario).hasValue("admin");
    sa.check(botonGuardar).isEnabled();
});
```

---

## 16. Tablas

```java
Table tabla = web.table(Element.id("tabla-usuarios"));

// Leer celdas
String nombre   = tabla.cell(0, "Nombre");        // fila 0, columna "Nombre"
String estado   = tabla.cell(2, "Estado");

// Navegar filas
tabla.rowWhere("Estado", "Activo").click();        // click en la fila con Estado=Activo
int totalFilas = tabla.rowCount();

// Iterar
tabla.rows().forEach(row -> {
    String nombre2 = row.cell("Nombre");
    if ("Inactivo".equals(row.cell("Estado"))) {
        row.click();
    }
});
```

---

## 17. PopupGuard

```java
// Registrar popups conocidos
web.popupGuard.register(
    "Cookie Banner",
    By.id("cookie-consent"),
    driver -> driver.findElement(By.id("btn-accept-all")).click()
);

web.popupGuard.register(
    "Sesión expirada",
    By.cssSelector(".session-timeout-modal"),
    driver -> driver.findElement(By.cssSelector(".btn-renovar")).click()
);

// Envolver acciones sensibles
web.popupGuard.safely(() -> web.actions.click(botonPago));
// Si aparece un popup durante el click, se maneja automáticamente y se reintenta
```

---

## 18. Cookies y Storage

```java
// ── Cookies ────────────────────────────────────────────────────
web.cookies.add("session_token", "abc123");
String token = web.cookies.get("session_token");
web.cookies.delete("session_token");
web.cookies.deleteAll();

// ── localStorage ───────────────────────────────────────────────
web.storage.setLocal("user_pref", "{\"theme\":\"dark\"}");
String pref = web.storage.getLocal("user_pref");
web.storage.removeLocal("user_pref");
web.storage.clearLocal();

// ── sessionStorage ─────────────────────────────────────────────
web.storage.setSession("temp_data", "valor");
String temp = web.storage.getSession("temp_data");
web.storage.clearSession();
```

---

## 19. Integración con runners

El framework no impone ningún runner. `Web.init()` abre el browser y `web.close()` lo cierra.

### JUnit 5

```java
class LoginTest {
    Web web;

    @BeforeEach
    void setUp() { web = Web.init(ConfigLoader.webConfig()); }

    @AfterEach
    void tearDown() { web.close(); }

    @Test
    void login() {
        web.actions.type(Element.id("username"), "admin");
        web.actions.click(Element.id("btn-login"));
        web.assertThat(Element.id("dashboard")).isVisible();
    }
}
```

### TestNG

```java
public class LoginTest {
    Web web;

    @BeforeMethod
    public void setUp() { web = Web.init(ConfigLoader.webConfig()); }

    @AfterMethod
    public void tearDown() { web.close(); }

    @Test
    public void login() {
        web.actions.type(Element.id("username"), "admin");
        web.actions.click(Element.id("btn-login"));
        web.assertThat(Element.id("dashboard")).isVisible();
    }
}
```

### Cucumber

```java
// Hooks.java
public class Hooks {
    private Web web;

    @Before
    public void setUp() {
        web = Web.init(ConfigLoader.webConfig());
        WebContext.set(web);
    }

    @After
    public void tearDown() {
        if (web != null) web.close();
        WebContext.remove();
    }
}

// LoginSteps.java
public class LoginSteps {
    private final Web web = WebContext.get();

    @When("el usuario ingresa credenciales válidas")
    public void ingresaCredenciales() {
        web.actions.type(Element.id("username"), "admin");
        web.actions.type(Element.id("password"), "secret");
        web.actions.click(Element.id("btn-login"));
    }

    @Then("debe ver el dashboard")
    public void verDashboard() {
        web.assertThat(Element.id("dashboard")).isVisible();
    }
}
```

### Main de Java puro

```java
public class Main {
    public static void main(String[] args) {
        WebConfig config = WebConfig.builder()
            .url("http://mi-app.com")
            .project("mi-proyecto")
            .build();

        Web web = Web.init(config);
        try {
            SiebelWaits siebel = new SiebelWaits(web.driver);
            siebel.waitForPageReady();
            web.healing.register(Element.id("btn-login"), "baseline_login");
            web.actions.type(Element.id("username"), "admin");
            web.actions.click(Element.id("btn-login"));
            web.assertThat(Element.id("dashboard")).isVisible();
        } finally {
            web.close();
        }
    }
}
```

### Ejecución paralela

Con JUnit 5 en modo `PER_METHOD`, cada test recibe su propia instancia y su propio browser.
Usar `WebContext` (ThreadLocal) para que extensiones y steps accedan al driver correcto:

```java
// En setUp
WebContext.set(web);

// En tearDown
WebContext.remove();

// En extensiones / steps
WebDriver driver = WebContext.driver();   // null si el hilo no tiene sesión activa
Web web = WebContext.get();
```

---

## 20. Referencia rápida de la API

### `Web` — punto de entrada

| Campo | Tipo | Descripción |
|---|---|---|
| `web.actions` | `Actions` | Escritura, clicks, scroll, drag, selects, alertas |
| `web.waits` | `Waits` | Esperas basadas en condiciones |
| `web.healing` | `HealingActions` | Registro de baselines y sanación |
| `web.popupGuard` | `PopupGuard` | Manejo automático de popups |
| `web.cookies` | `CookieActions` | Gestión de cookies |
| `web.storage` | `StorageActions` | localStorage y sessionStorage |
| `web.healingClient` | `HealingClient` | Acceso directo a endpoints del servicio |
| `web.driver` | `WebDriver` | Driver de Selenium (operaciones avanzadas) |

```java
Web web = Web.init(config);           // abre browser + navega a config.getUrl()
Web web = Web.init("url", "project"); // atajo rápido
web.navigateTo("http://otra-url.com");
web.assertThat(elemento);
web.softAssert(sa -> { ... });
web.table(elemento);
web.close();                          // cerrar browser y liberar recursos
```

### `HealingActions` — métodos de healing

```java
web.healing.register(element)
web.healing.register(element, testId)

web.healing.heal(element)
web.healing.heal(element, testId)
web.healing.heal(element, testId, context)

web.healing.healAndClick(element)
web.healing.healAndClick(element, context)

web.healing.healAndType(element, text)
web.healing.healAndType(element, text, context)
```

### `RepairRepository` — métodos de caché

```java
new RepairRepository("jdbc:sqlite:repair-history.db")

repo.saveOrUpdate(app, pageUrl, originalType, originalValue, suggestedLocator)
repo.touch(app, pageUrl, originalType, originalValue, repairedType, repairedValue)
repo.reject(app, pageUrl, originalType, originalValue, repairedType, repairedValue, reason)

repo.findApprovedRepair(app, pageUrl, originalType, originalValue, minScore)          // TTL=7d
repo.findApprovedRepair(app, pageUrl, originalType, originalValue, minScore, ttlDays)
repo.hasSuccessfulRepairs(app, pageUrl)
```

### `HealContext` — builder de filtros

```java
HealContext.create()
    .anchor(type, value, weight)     // "id" | "text" | "css"
    .anchorById(id)                  // weight=40
    .anchorByText(text)              // weight=30
    .anchorByName(name)              // css=[name='...'], weight=35
    .anchorByAriaLabel(label)        // css=[aria-label='...'], weight=25
    .inContainer(id)
    .inContainerClass(cssClass)      // "clase" | "c1 c2" (AND) | "c1,c2" (OR)
    .inForm(formId)
    .excludeId(ids...)
```

---

## Ejecutar la demo

```bash
# Requiere: servicio en :8765 y demo app en :9000
mvn test-compile exec:java \
    -Dexec.mainClass="com.selfhealing.demo.DemoMain" \
    -Dexec.classpathScope=test
```

La demo cubre todos los módulos sin necesitar un runner de tests:
`[CONFIG]` `[HEAL-A]` `[HEAL-B]` `[STABLE]` `[SIEBEL]` `[WATCH]` `[REPO]` `[AUDIT]`
