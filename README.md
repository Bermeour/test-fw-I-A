# Framework de Automatización Web con Self-Healing

Framework Java sobre Selenium 4 con auto-reparación de selectores, acciones robustas,
assertions fluidas, manejo de popups y ejecución paralela.

> Versión actual: **2.0.0** | Java 11+ | Selenium 4.20 | JUnit 5.10 | Maven 3.6+

---

## Índice

1. [Requisitos previos](#1-requisitos-previos)
2. [Onboarding — nuevo proyecto en 5 minutos](#2-onboarding--nuevo-proyecto-en-5-minutos)
3. [Configuración completa](#3-configuración-completa)
4. [Elementos](#4-elementos)
5. [Acciones](#5-acciones)
6. [Esperas](#6-esperas)
7. [Self-Healing — guía completa](#7-self-healing--guía-completa)
8. [Assertions](#8-assertions)
9. [Tablas](#9-tablas)
10. [PopupGuard](#10-popupguard)
11. [Cookies y Storage](#11-cookies-y-storage)
12. [Ejecución paralela](#12-ejecución-paralela)
13. [Retry automático](#13-retry-automático)
14. [BaseTest — clase base para tests](#14-basetest--clase-base-para-tests)
15. [Patrones recomendados](#15-patrones-recomendados)
16. [Preguntas frecuentes y troubleshooting](#16-preguntas-frecuentes-y-troubleshooting)
17. [Referencia rápida de la API](#17-referencia-rápida-de-la-api)

---

## 1. Requisitos previos

| Requisito | Versión mínima | Notas |
|---|---|---|
| Java | 11 | Compatible con 17 y 21 |
| Maven | 3.6 | |
| Chrome | Cualquier reciente | Selenium Manager descarga ChromeDriver automáticamente |
| Servicio self-healing | corriendo en `localhost:8765` | Ver sección 7 |

El framework **no requiere instalar ChromeDriver manualmente** — Selenium 4 lo gestiona.

---

## 2. Onboarding — nuevo proyecto en 5 minutos

### Paso 1 — Copiar la configuración base

Editar `src/test/resources/config.properties` con los datos de tu proyecto:

```properties
app.url     = http://mi-aplicacion.empresa.com
app.project = nombre_proyecto          # único por proyecto, sin espacios
healing.url = http://localhost:8765
healing.scoring_profile = default      # ver sección 7.3
driver.browser  = chrome
driver.headless = false
driver.timeout.element  = 30
driver.timeout.pageLoad = 60
```

> **Importante:** `app.project` identifica tu proyecto en la base de datos del servicio de
> self-healing. Usa un nombre descriptivo y único entre los 45 proyectos (ej: `portal_rrhh`,
> `erp_compras`, `intranet_logistica`). **No lo cambies una vez creados los baselines** — si lo
> cambias, el servicio perderá todos los baselines registrados para ese proyecto.

### Paso 2 — Crear la primera clase de test

```java
@DisplayName("Login — Portal RRHH")
class TestLogin extends BaseTest {

    // Declarar elementos una vez, reutilizarlos en todos los métodos
    private static final Element USUARIO  = Element.id("input-username").label("Campo usuario");
    private static final Element PASSWORD = Element.id("input-password").label("Campo contraseña");
    private static final Element BTN_LOGIN = Element.id("btn-login").label("Botón login");
    private static final Element RESULTADO = Element.id("login-result").label("Resultado login");

    @Test
    @DisplayName("Login exitoso con credenciales válidas")
    void testLoginExitoso() {
        web.actions.type(USUARIO,   "admin");
        web.actions.type(PASSWORD,  "secreto");
        web.actions.click(BTN_LOGIN);

        web.assertThat(RESULTADO)
           .isVisible()
           .containsText("Bienvenido");
    }
}
```

### Paso 3 — Ejecutar

```bash
# Ejecución normal
mvn test

# Apuntar a otro entorno sin tocar config.properties
mvn test -Dapp.url=http://staging.empresa.com -Ddriver.headless=true

# Solo una clase de test
mvn test -Dtest=TestLogin

# Solo un método
mvn test -Dtest=TestLogin#testLoginExitoso
```

---

## 3. Configuración completa

### `config.properties` — referencia de todas las claves

```properties
# ── Aplicación ────────────────────────────────────────────────
app.url     = http://localhost:9000
app.project = mi_proyecto           # identificador único en el servicio de healing

# ── Servicio de self-healing ──────────────────────────────────
healing.url             = http://localhost:8765
# Perfil de scoring — ajusta los pesos de los motores según el tipo de app:
#   default  → equilibrado, válido para la mayoría de apps web
#   siebel   → prioriza jerarquía DOM (Siebel CRM, SAP, Oracle Forms)
#   angular  → adapta scoring para apps Angular/React/Vue
#   legacy   → apps con IDs dinámicos o generados automáticamente
healing.scoring_profile = default

# ── Driver ────────────────────────────────────────────────────
driver.browser  = chrome            # chrome | firefox | edge
driver.headless = false             # true en CI/CD
driver.timeout.element  = 30       # segundos para esperas de elementos
driver.timeout.pageLoad = 60       # segundos para carga de página

# ── Proxy (dejar vacíos si no hay proxy) ──────────────────────
proxy.host =
proxy.port =

# ── Chrome: argumentos extra (separados por comas) ────────────
chrome.args =
# Ejemplos:
#   chrome.args = --window-size=1920,1080,--disable-extensions
#   chrome.args = --incognito,--lang=es

# ── Chrome: preferencias (chrome://settings) ──────────────────
# chrome.pref.download.default_directory = C:/descargas
# chrome.pref.download.prompt_for_download = false
# chrome.pref.plugins.always_open_pdf_externally = true
```

### Sobreescrituras por entorno en CI/CD

```bash
# Sin tocar config.properties, desde el pipeline:
mvn test \
  -Dapp.url=http://staging.empresa.com \
  -Dapp.project=portal_rrhh \
  -Ddriver.headless=true \
  -Ddriver.browser=edge \
  -Dhealing.scoring_profile=angular
```

### Configuración programática con `WebConfig`

Para casos avanzados que necesiten opciones no disponibles en `config.properties`:

```java
WebConfig config = WebConfig.builder()
    .url("http://mi-app.com")
    .project("portal_rrhh")
    .browser(WebConfig.Browser.CHROME)
    .headless(true)
    .timeoutSeconds(45)
    .pageLoadTimeoutSeconds(90)
    .scoringProfile(WebConfig.ScoringProfile.ANGULAR)
    .proxy("proxy.empresa.com", 8080)
    .chromeArg("--window-size=1920,1080")
    .chromeArg("--disable-extensions")
    .chromePref("download.default_directory", "C:/descargas")
    .chromePref("download.prompt_for_download", false)
    .customizeChrome(opts -> {
        // Escape hatch: opciones avanzadas no cubiertas por el builder
        opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
    })
    .build();

Web web = Web.init(config);
```

---

## 4. Elementos

`Element` describe un localizador. Se declara una vez como constante y se reutiliza en todos
los métodos del test.

```java
// Tipos de localizador
Element.id("btn-login")
Element.xpath("//button[@id='btn-login']")
Element.css(".modal .btn-primary")
Element.name("username")
Element.text("Iniciar sesión")        // busca por texto visible exacto

// Etiqueta legible — aparece en logs y mensajes de error en lugar del selector técnico
Element campo = Element.id("input-username").label("Campo usuario");
// Logs: "[14:32:01] STEP  type → Campo usuario = 'admin'"
// Error: "isVisible(Campo usuario): el elemento no existe en el DOM"
```

**Buena práctica** — declarar los elementos como constantes estáticas en la clase de test o
en una clase de Page Object, nunca inline en cada método:

```java
// ✅ Correcto — declarado una vez, legible y reutilizable
private static final Element BTN_GUARDAR = Element.id("btn-save").label("Botón Guardar");

// ❌ Incorrecto — duplicado en cada método, sin etiqueta
driver.findElement(By.id("btn-save")).click();
```

---

## 5. Acciones

### Escritura

```java
web.actions.type(campo, "admin");             // limpiar + escribir + verificar que persistió
web.actions.append(campo, " apellido");       // añadir sin limpiar (autocomplete)
web.actions.typeSlow(campo, "admin", 80);     // 80ms entre teclas (validación live)
web.actions.typeJS(campo, "valor");           // vía JS — campos protegidos o custom
web.actions.clear(campo);
web.actions.pressKey(campo, Keys.ENTER);
web.actions.pressKey(campo, Keys.chord(Keys.CONTROL, "a")); // Ctrl+A
```

> **`typeSlow`** — úsalo cuando el campo tiene autocompletado o validación que se dispara
> con cada tecla y no responde a `sendKeys` normal (Siebel, SAP, apps Angular con `(input)`).

### Clicks

```java
web.actions.click(boton);
web.actions.clickJS(boton);           // vía JS — evita overlays y elementos fuera del viewport
web.actions.doubleClick(elemento);
web.actions.rightClick(elemento);
web.actions.clickAt(canvas, 120, 45); // coordenadas relativas al elemento
web.actions.clickByKeyboard(boton);   // foco + ENTER (accesibilidad / Siebel)
```

### Llenado masivo de formularios

```java
// LinkedHashMap garantiza el orden si hay campos que validan al perder el foco
Map<Element, String> campos = new LinkedHashMap<>();
campos.put(nombre,   "Juan García");
campos.put(email,    "juan@empresa.com");
campos.put(telefono, "600123456");
campos.put(empresa,  "ACME S.L.");

web.actions.fillForm(campos);
web.actions.click(guardar);
```

### Lectura de valores

```java
String texto  = web.actions.read(elemento);               // innerText
String valor  = web.actions.readValue(inputField);        // atributo value
String href   = web.actions.readAttribute(enlace, "href");
boolean ok    = web.actions.isVisible(elemento);
boolean activo = web.actions.isEnabled(boton);
boolean marcado = web.actions.isSelected(checkbox);
```

### Sub-namespaces de acciones

```java
web.actions.scroll.toElement(tablaGrande);
web.actions.scroll.toTop();
web.actions.scroll.toBottom();
web.actions.scroll.byPixels(0, 500);

web.actions.visual.highlight(elemento);      // borde rojo para depuración
web.actions.visual.blink(elemento, 3);       // parpadea N veces
web.actions.visual.screenshot("paso-1");     // guarda screenshot en disco

web.actions.select.byText(pais, "Colombia");
web.actions.select.byValue(pais, "CO");
web.actions.select.byIndex(pais, 2);

web.actions.drag.dragAndDrop(tarjeta, columna);

web.actions.alert.readAndAccept();           // leer texto y aceptar
web.actions.alert.dismiss();
web.actions.alert.typeAndAccept("texto");    // para prompt

web.actions.navigate.back();
web.actions.navigate.refresh();
web.actions.navigate.openNewTab("http://otra-url.com");
web.actions.navigate.switchToFrame(Element.id("frame-contenido"));
web.actions.navigate.switchToDefaultContent();
```

---

## 6. Esperas

Todas usan `FluentWait` internamente — nunca bloquean más de lo necesario y toleran DOM inestable.

```java
web.waits.untilPageReady();                          // readyState + AJAX idle + spinner
web.waits.untilVisible(Element.id("resultado"));
web.waits.untilClickable(Element.id("btn-enviar"));  // visible + enabled
web.waits.untilGone(Element.css(".loading-overlay"));
web.waits.untilTextPresent(Element.id("estado"), "Procesado");
web.waits.sleep(2);                                  // pausa fija — último recurso
```

**`untilPageReady`** — comprueba tres condiciones en secuencia:
1. `document.readyState === 'complete'`
2. Cola AJAX de jQuery vacía (se ignora si no hay jQuery)
3. Spinners/overlays desaparecidos (`loading`, `busy`, `spinner`, `loadingPanel`)

Fundamental para Siebel, SAP u Oracle Forms que reportan `readyState=complete` mientras aún
procesan internamente.

---

## 7. Self-Healing — guía completa

El servicio de self-healing repara selectores rotos automáticamente cuando cambian tras un
deploy. Usa tres motores: análisis de DOM, visión por computadora (CV) e historial de
sanaciones anteriores.

### 7.1 Cómo funciona internamente

```
findElement(selector)
  │
  ├─ Selector funciona → devuelve el elemento (flujo normal)
  │
  └─ NoSuchElementException
        │
        ├─ Captura: driver.getPageSource() → DOM completo
        ├─ Captura: screenshot PANTALLA COMPLETA → motor CV
        ├─ POST /heal al servicio
        │     ├─ 404 → sin baseline → el test falla con el error original
        │     ├─ 422 → ningún motor encontró el elemento → el test falla
        │     ├─ 5xx/timeout → reintentar 3 veces (500ms→1s→2s) → el test falla
        │     └─ 200 healed=true
        │           ├─ selector_type="xpath" → findElement(By.xpath(newSelector))
        │           ├─ selector_type="coords" → document.elementFromPoint(x, y)
        │           ├─ Elemento encontrado → POST /heal/feedback(correct=true) en background
        │           │                      → retorna el elemento
        │           └─ Elemento no encontrado → POST /heal/feedback(correct=false) en background
        │                                     → el test falla con el error original
        └─ En todos los casos de fallo: relanza la excepción original de Selenium
```

### 7.2 Registrar baselines

El baseline es la "foto" del elemento cuando su selector funciona. El servicio lo usa como
referencia para encontrar el elemento después de que el selector cambie.

```java
// En @BeforeEach — registrar cuando la app está en estado conocido
@BeforeEach
void registrarBaselines() {
    web.healing.register(Element.id("btn-login"),       "login_btn");
    web.healing.register(Element.id("input-username"),  "login_username");
    web.healing.register(Element.id("input-password"),  "login_password");
}
```

**Qué envía el cliente al registrar:**
- `selector_value` — el selector que funciona hoy
- `element_meta` — tag, id, texto, clases CSS, aria-label, placeholder, tipo, rol, data-testid,
  tag del padre, número de hermanos
- `screenshot_base64` — **recorte del elemento** (no pantalla completa). Selenium 4 lo genera
  con `element.getScreenshotAs()`. El motor CV lo usa como template para template-matching.

> **Por qué el recorte y no la pantalla completa en register:**
> El motor CV guarda el recorte como template. Cuando el selector falla, busca ese template
> en la pantalla completa. Si mandas pantalla completa en el register, el template-matching
> pierde precisión porque el "template" incluye todo el contexto de la página.

### 7.3 Perfiles de scoring

El perfil controla cómo el servicio pondera cada motor al buscar el elemento:

| Perfil | Cuándo usarlo |
|---|---|
| `default` | Apps web estándar (la mayoría de proyectos) |
| `siebel` | Siebel CRM, SAP WebGUI, Oracle Forms — DOM con jerarquía profunda y IDs dinámicos largos |
| `angular` | Apps Angular, React, Vue — componentes con bindings y atributos generados |
| `legacy` | Apps legacy con IDs generados dinámicamente (ej: `id="ctrl0x3A2F"`) |

Configurar en `config.properties`:
```properties
healing.scoring_profile = siebel
```

O por proyecto al crear el `Web`:
```java
WebConfig.builder()
    .scoringProfile(WebConfig.ScoringProfile.SIEBEL)
    .build();
```

### 7.4 Usar el healing en un test

```java
// Cuando el selector puede haberse roto tras un deploy
WebElement boton = web.healing.heal(
    Element.id("btn-login-ROTO"),  // selector que ya no funciona
    "login_btn"                    // testId del baseline registrado
);
boton.click();

// Atajos directos
web.healing.healAndClick(Element.id("btn-login-ROTO"));
web.healing.healAndType(Element.id("input-user-ROTO"), "admin");
```

### 7.5 Lo que el servicio devuelve

**Respuesta exitosa (selector xpath):**
```json
{
  "healed": true,
  "new_selector": "//button[@data-testid='login-btn']",
  "selector_type": "xpath",
  "strategy_used": "DOM",
  "confidence": 0.87,
  "healing_event_id": 42,
  "from_cache": false
}
```

**Respuesta desde caché** (el servicio ya sanó este selector antes y lo recuerda):
```json
{
  "healed": true,
  "new_selector": "//button[@data-testid='login-btn']",
  "selector_type": "HISTORY",
  "confidence": 1.0,
  "healing_event_id": 43,
  "from_cache": true
}
```

**Respuesta del motor CV** (el elemento se encontró por imagen, no por DOM):
```json
{
  "healed": true,
  "new_selector": "coords::320,150",
  "selector_type": "coords",
  "strategy_used": "CV",
  "confidence": 0.91,
  "healing_event_id": 44
}
```
> Cuando `selector_type="coords"`, el cliente usa `document.elementFromPoint(x,y)` para
> localizar el elemento. Esto es transparente — el cliente lo maneja automáticamente.

### 7.6 Feedback automático (aprendizaje del servicio)

Tras cada healing exitoso (o fallido), el cliente envía feedback al servicio en un **hilo de
fondo** para que el motor aprenda. Este proceso es invisible para el test y no añade latencia.

```
Healing exitoso + elemento encontrado  → POST /heal/feedback { correct: true }
Healing exitoso + elemento NO encontrado → POST /heal/feedback { correct: false }
```

No necesitas hacer nada — el framework lo gestiona solo.

### 7.7 Errores del servicio y comportamiento esperado

| Situación | Qué hace el cliente | Mensaje en consola |
|---|---|---|
| 404 — sin baseline | Falla con error original de Selenium | `Sin baseline registrado para '...'` |
| 422 — ningún motor resolvió | Falla con error original de Selenium | `Ningún motor pudo sanar '...'` |
| Servicio caído (5xx) | Reintenta 3 veces, luego falla normal | `Servicio no disponible (HTTP 503)` |
| Timeout de red | Reintenta 3 veces, luego falla normal | `Servicio no disponible tras 3 intentos` |

> **El servicio nunca debe colgar un test.** Si no responde en 10 segundos, el cliente
> lanza excepción y el test falla con el error original de Selenium — como si el healing
> no existiera.

### 7.8 Monitorizar el servicio desde los tests

```java
// Estado del servicio
Map<String, Object> health = web.healingClient.getHealth();
assertEquals("ok", health.get("status"));

// Estadísticas de tu proyecto
Map<String, Object> metrics = web.healingClient.getMetrics("mi_proyecto");
System.out.println("Sanaciones totales: " + metrics.get("total_heals"));

// Historial de eventos
Map<String, Object> history = web.healingClient.getHistory("mi_proyecto", 50);
```

### 7.9 Lo que el self-healing NO hace

- **No arregla bugs** en el código de tests. Si el test falla por lógica incorrecta, no se sana.
- **No funciona sin baseline.** Si no registraste el elemento antes de que rompiera, el servicio
  devuelve 404 y el test falla normalmente.
- **No sana `StaleElementReferenceException`** — ese error es del DOM cambiando durante la
  interacción, no del selector. El framework lo reintenta directamente sin llamar al servicio.
- **No cachea selectores localmente.** El servicio ya tiene su propia caché — el cliente
  nunca guarda resultados de healing entre ejecuciones.
- **No reintenta el healing más de una vez por elemento** en la misma ejecución. Si falla,
  falla definitivamente.

---

## 8. Assertions

### Estrictas — fallan en el primer problema

```java
web.assertThat(resultado)
   .isVisible()
   .hasText("Login exitoso")           // texto exacto
   .containsText("exitoso")            // subcadena
   .hasAttribute("class", "alert-success")
   .hasClass("success");               // class contiene la subcadena

web.assertThat(inputField)
   .hasValue("admin")
   .isEnabled();

web.assertThat(checkbox).isSelected();
web.assertThat(boton).isDisabled();
web.assertThat(lista).matchesCount(10);  // exactamente 10 elementos
web.assertThat(error).doesNotExist();
web.assertThat(panel).exists();
```

### Soft assertions — evalúan todo, reportan todos los fallos

Cuando necesitas verificar múltiples aspectos de una pantalla y quieres ver todos los fallos
de una vez (sin que el primero detenga la ejecución):

```java
web.softAssert(sa -> {
    sa.check(titulo)    .hasText("Dashboard");
    sa.check(menuLateral).isVisible();
    sa.check(campoUser) .hasValue("admin");
    sa.check(contador)  .containsText("15 resultados");
    sa.check(btnExportar).isEnabled();
});
// Si hay N fallos: lanza AssertionError con todos los mensajes agrupados
// Si todo pasa: continúa sin interrupciones
```

---

## 9. Tablas

Helper para tablas HTML (`<table>`) sin escribir XPaths manuales.

```java
Table tabla = web.table(Element.id("tabla-usuarios"));

// Leer
String nombre = tabla.cell(0, "Nombre");        // fila 0, columna por cabecera
String estado = tabla.cell(2, "Estado");
int total     = tabla.rowCount();

// Buscar y actuar
tabla.rowWhere("Email", "juan@empresa.com").click();

// Obtener columna completa
List<String> nombres = tabla.columnValues("Nombre");
assertTrue(nombres.contains("Juan García"));

// Verificar existencia de fila
assertTrue(tabla.hasRowWhere("Estado", "Activo"));
```

---

## 10. PopupGuard

Detecta y maneja popups inesperados (banners de cookies, alertas de sesión, modales de error)
que de otro modo romperían el test con `ElementClickInterceptedException`.

### Uso básico

```java
// Proteger una acción puntual
web.popupGuard.safely(() -> web.actions.click(guardarButton));

// Con valor de retorno
String texto = web.popupGuard.safely(() -> web.actions.read(mensajeElement));
```

### Registrar popups conocidos

```java
// En @BeforeEach — registrar los popups que puede mostrar tu app
web.popupGuard.register("Banner de cookies",
    By.id("cookie-consent"),
    driver -> driver.findElement(By.id("btn-accept-all")).click()
);

web.popupGuard.register(PopupRule.byText(
    "Sesión por expirar",
    "Su sesión expirará en",
    driver -> driver.findElement(By.id("btn-extender-sesion")).click()
));
```

### Protección global (hilo de fondo)

Inicia un hilo daemon que escanea cada 2 segundos. Útil para apps con notificaciones
imprevisibles durante ejecuciones largas.

```java
web.popupGuard.enableGlobalProtection();
// ... ejecución del test ...
web.popupGuard.disableGlobalProtection();
```

### Configurar alertas nativas

```java
// Por defecto: SCREENSHOT_AND_DISMISS (toma evidencia y cierra)
web.popupGuard.onNativeAlert(PopupGuard.NativeAlertAction.FAIL);   // falla el test
web.popupGuard.onNativeAlert(PopupGuard.NativeAlertAction.ACCEPT);
web.popupGuard.onNativeAlert(PopupGuard.NativeAlertAction.DISMISS);
```

### Verificar historial al final del test

```java
assertFalse(web.popupGuard.hadUnexpectedPopups(),
    "El test encontró popups no registrados — revisar la aplicación");

web.popupGuard.getInterceptions()
    .forEach(i -> System.out.println(i.getSummary()));
```

---

## 11. Cookies y Storage

```java
// Cookies
web.cookies.set("session_token", "abc123");
String token = web.cookies.get("session_token");
web.cookies.delete("session_token");
web.cookies.deleteAll();

// localStorage
web.storage.local.set("preferencias", "{\"tema\":\"oscuro\"}");
String prefs = web.storage.local.get("preferencias");
web.storage.local.remove("preferencias");
web.storage.local.clear();

// sessionStorage (misma API que local)
web.storage.session.set("carrito", "[{\"id\":1}]");
```

---

## 12. Ejecución paralela

### Configuración en `junit-platform.properties`

```properties
# Activar paralelismo (false = secuencial, útil para depurar)
junit.jupiter.execution.parallel.enabled = true
junit.jupiter.execution.parallel.mode.default = concurrent
junit.jupiter.execution.parallel.mode.classes.default = concurrent

# 3 browsers en paralelo — ajustar según RAM disponible (cada Chrome ≈ 150-300 MB)
junit.jupiter.execution.parallel.config.strategy = fixed
junit.jupiter.execution.parallel.config.fixed.parallelism = 3
```

### Cómo funciona

JUnit 5 usa `PER_METHOD` por defecto: cada método de test recibe su propia instancia de
`BaseTest` → su propio `Web` → su propio browser. No hay estado compartido entre tests.

```
Test A (hilo 1) → instancia BaseTest_1 → Web_1 → Chrome_1
Test B (hilo 2) → instancia BaseTest_2 → Web_2 → Chrome_2
Test C (hilo 3) → instancia BaseTest_3 → Web_3 → Chrome_3
```

### WebContext — acceder al driver sin referencias directas

Para extensiones JUnit, page objects o utilidades que no tienen referencia al test:

```java
Web       web    = WebContext.get();     // sesión del hilo actual
WebDriver driver = WebContext.driver(); // driver del hilo actual
```

`BaseTest` gestiona el ciclo automáticamente:
```
@BeforeEach → WebContext.set(web)   // registrar al inicio
@AfterEach  → WebContext.remove()  // limpiar al final (evita memory leaks)
```

### Tests que no pueden correr en paralelo

```java
@Test
@ResourceLock("datos-compartidos")   // exclusividad para tests que modifican datos globales
void testQueModificaConfiguracionGlobal() { ... }
```

### Ajustar paralelismo según el entorno

```bash
# En un servidor CI con 8 cores y 16 GB RAM
mvn test -Djunit.jupiter.execution.parallel.config.fixed.parallelism=6

# En local para depurar un fallo puntual
mvn test -Djunit.jupiter.execution.parallel.enabled=false
```

---

## 13. Retry automático

Reinicia el browser y reintenta el test si falla. Para flakiness del entorno, **no** para
enmascarar bugs.

```java
@Test
@RetryOnFailure(times = 2)   // 2 reintentos → hasta 3 intentos en total
void testInestable() { ... }
```

Cada reintento ejecuta `@AfterEach` → cierra browser → `@BeforeEach` → abre browser nuevo.
Si el test pasa en cualquier intento, se considera exitoso.

---

## 14. BaseTest — clase base para tests

Heredar de `BaseTest` proporciona automáticamente:

| Qué | Cómo se usa |
|---|---|
| `web` | Sesión `Web` lista, configurada desde `config.properties` |
| `driver` | `WebDriver` Selenium directo (para operaciones nativas) |
| Screenshot en fallo | Se guarda en `screenshots/failures/` con timestamp |
| StepLogger | Imprime cada acción: `[14:32:01] STEP  click → Botón login` |
| `@RetryOnFailure` | Disponible en cualquier método |
| Soporte paralelo | Cada método tiene su propio browser |
| `web.healingClient` | Cliente HTTP al servicio (para tests de métricas/salud) |

```java
@DisplayName("Mi módulo")
class TestMiModulo extends BaseTest {

    @BeforeEach
    void prepararBaselines() {
        // Registrar antes de que los selectores puedan cambiar
        web.healing.register(Element.id("btn-accion"), "btn_accion");
    }

    @Test
    void testEscenarioPrincipal() {
        web.actions.type(Element.id("campo"), "valor");
        web.assertThat(Element.id("resultado")).isVisible();
    }

    @Test
    @RetryOnFailure(times = 1)
    void testConReintento() { ... }
}
```

### Helpers heredados de `SiebelWaits`

Para apps lentas con DOM inestable (Siebel, SAP, Oracle Forms):

```java
safeClick("//button[@id='guardar']");          // click con reintento ante DOM inestable
safeSendKeys("//input[@id='campo']", "valor"); // escritura con verificación de persistencia
waitForPageReady();                             // readyState + AJAX + spinner
WebElement el = waitReady("//input[@id='x']"); // visible + enabled
waitGone("//div[@id='modal']");                // esperar que desaparezca
```

---

## 15. Patrones recomendados

### Page Object Model

```java
// BasePage — heredar en todas las páginas
public abstract class BasePage {
    protected final Web web;

    protected BasePage(Web web) { this.web = web; }
}

// Página concreta
public class LoginPage extends BasePage {
    private static final Element USUARIO   = Element.id("input-username").label("Usuario");
    private static final Element PASSWORD  = Element.id("input-password").label("Password");
    private static final Element BTN_LOGIN = Element.id("btn-login").label("Botón Login");

    public LoginPage(Web web) { super(web); }

    public void login(String usuario, String password) {
        web.actions.type(USUARIO,  usuario);
        web.actions.type(PASSWORD, password);
        web.actions.click(BTN_LOGIN);
    }
}

// En el test
class TestLogin extends BaseTest {
    @Test
    void testLoginExitoso() {
        new LoginPage(web).login("admin", "secreto");
        web.assertThat(Element.id("dashboard")).isVisible();
    }
}
```

### Registrar baselines una sola vez por clase

```java
class TestGestionUsuarios extends BaseTest {

    // Registrar todos los baselines del módulo antes de los tests
    @BeforeEach
    void baselines() {
        web.healing.register(Element.id("btn-nuevo-usuario"), "btn_nuevo");
        web.healing.register(Element.id("tabla-usuarios"),    "tabla_usuarios");
        web.healing.register(Element.id("btn-guardar"),       "btn_guardar");
    }

    @Test
    void testCrearUsuario() { ... }

    @Test
    void testEditarUsuario() { ... }
}
```

### Separar datos de prueba del código

```java
@ParameterizedTest
@CsvSource({
    "admin,      secreto,  Dashboard",
    "supervisor, clave123, Panel supervisión",
    "consultor,  pass456,  Vista consulta"
})
void testLoginMultiplesRoles(String usuario, String password, String panelEsperado) {
    web.actions.type(USUARIO,  usuario);
    web.actions.type(PASSWORD, password);
    web.actions.click(BTN_LOGIN);
    web.assertThat(TITULO_PANEL).containsText(panelEsperado);
}
```

### Configurar popups una vez a nivel de clase

```java
class TestPortalRRHH extends BaseTest {

    @BeforeEach
    void configurarEntorno() {
        // Popups específicos de este portal
        web.popupGuard.register("Aviso de cookies",
            By.id("cookie-banner"),
            driver -> driver.findElement(By.id("btn-aceptar")).click()
        );
        web.popupGuard.register("Sesión por expirar",
            By.xpath("//*[contains(text(),'expirará en')]"),
            driver -> driver.findElement(By.id("btn-extender")).click()
        );
    }
}
```

---

## 16. Preguntas frecuentes y troubleshooting

### El healing devuelve 404 — ¿qué significa?

El servicio no tiene baseline para ese elemento en ese proyecto. Solución: añadir
`web.healing.register(element, testId)` en `@BeforeEach` cuando el selector funcione.

### El healing devuelve 422 — ¿qué significa?

El servicio tiene el baseline pero ningún motor (DOM, CV, historial) encontró el elemento
con confianza suficiente. Causas comunes:
- La página cambió demasiado para que el matching funcione
- El elemento desapareció de la aplicación (bug real, no problema de selector)
- El perfil de scoring no es el adecuado — probar otro `scoring_profile`

### El test sigue fallando aunque el healing "sanó" el selector

Ocurre cuando el servicio devuelve `healed=true` pero el `new_selector` tampoco funciona
en el DOM actual. El cliente envía `feedback(correct=false)` automáticamente para que el
servicio aprenda. El test falla con el error original — es el comportamiento correcto.

### Los tests pasan en local pero fallan en CI

Verificar:
1. `driver.headless=true` en CI — sin pantalla no hay display
2. `driver.timeout.element` y `pageLoad` — CI suele ser más lento
3. `healing.url` apunta al servicio correcto en el entorno CI
4. El servicio de healing está corriendo en CI

```bash
# Verificar antes de lanzar los tests en CI
curl http://localhost:8765/health
```

### Los tests fallan con `StaleElementReferenceException`

No es un problema de selector — el DOM cambió durante la interacción. Usar los helpers de
`SiebelWaits` (`safeClick`, `safeSendKeys`) o `web.waits.untilPageReady()` antes de la
acción. El healing **no** se activa para este tipo de error.

### ¿Cómo desactivar el paralelismo para depurar?

```bash
mvn test -Djunit.jupiter.execution.parallel.enabled=false -Dtest=TestMiClase
```

O temporalmente en `junit-platform.properties`:
```properties
junit.jupiter.execution.parallel.enabled = false
```

### ¿Cuántos browsers puedo lanzar en paralelo?

Regla general: `(RAM disponible en GB) / 0.5 = máximo de browsers`. Con 8 GB de RAM libre,
máximo ~16 — pero la experiencia indica que 4-6 es el rango óptimo para CI. En local, 3.

### ¿Por qué `@RetryOnFailure` no debería usarse siempre?

Enmasca problemas reales. Si un test falla consistentemente, es un bug — no hay que
reintentar. Reservar para flakiness confirmada del entorno (red, servicio externo inestable).

### El servicio de healing no responde — ¿el test cuelga?

No. El cliente tiene un timeout de 10 segundos (conexión + respuesta). Si el servicio no
responde, el cliente reintenta 3 veces con backoff (500ms → 1s → 2s) y luego lanza la
excepción original de Selenium. El test falla normalmente, nunca se queda colgado.

---

## 17. Referencia rápida de la API

```
Web
├── actions
│   ├── type(el, text) / append / typeSlow(el, text, ms) / typeJS / pressKey / clear / clearJS
│   ├── click(el) / clickJS / doubleClick / rightClick / clickAt(el, x, y) / clickByKeyboard
│   ├── read(el) / readValue / readAttribute(el, attr)
│   ├── isVisible(el) / isEnabled / isSelected
│   ├── fillForm(Map<Element, String>)
│   ├── scroll.*    → toElement / toTop / toBottom / byPixels(x, y)
│   ├── visual.*    → highlight / blink(el, times) / screenshot(name)
│   ├── select.*    → byText / byValue / byIndex
│   ├── drag.*      → dragAndDrop(from, to)
│   ├── alert.*     → readAndAccept / dismiss / typeAndAccept(text)
│   └── navigate.*  → back / refresh / openNewTab(url) / switchToFrame / switchToDefaultContent
│
├── waits
│   └── untilPageReady / untilVisible / untilClickable / untilGone / untilTextPresent / sleep
│
├── healing
│   ├── register(element) / register(element, testId)
│   ├── heal(element) / heal(element, testId)
│   ├── healAndClick(element)
│   └── healAndType(element, text)
│
├── healingClient                          ← HTTP directo al servicio
│   ├── getHealth()
│   ├── getMetrics(project)
│   └── getHistory(project, limit)
│
├── popupGuard
│   ├── register(name, By, handler) / register(PopupRule)
│   ├── safely(Runnable) / safely(Supplier<T>)
│   ├── enableGlobalProtection / disableGlobalProtection
│   ├── scan()
│   ├── onNativeAlert(NativeAlertAction)
│   ├── getInterceptions / hadUnexpectedPopups / clearInterceptions
│   └── screenshotDir(path)
│
├── cookies
│   └── set / get / delete / deleteAll
│
├── storage
│   ├── local.*   → set / get / remove / clear
│   └── session.* → set / get / remove / clear
│
├── driver                  ← WebDriver directo (escape hatch para operaciones no cubiertas)
│
├── assertThat(element)     → WebAssert
│   └── isVisible / isNotVisible / isEnabled / isDisabled / isSelected / isNotSelected
│       hasText / containsText / hasValue / hasAttribute / hasClass
│       exists / doesNotExist / matchesCount(n)
│
├── softAssert(Consumer<SoftAssertions>)   → acumula fallos, lanza al final
│
├── table(element)          → Table
│   └── cell(row, colHeader) / rowCount / columnValues(header)
│       rowWhere(col, val).click() / hasRowWhere(col, val)
│
└── navigateTo(url)
```

### `Element` — factory methods

```
Element.id(value)    .label(name)
Element.xpath(value) .label(name)
Element.css(value)   .label(name)
Element.name(value)  .label(name)
Element.text(value)  .label(name)
```

### `WebConfig.Builder` — opciones de configuración

```
.url(String)                       obligatorio
.project(String)                   obligatorio — único por proyecto
.browser(Browser.CHROME/FIREFOX/EDGE)
.headless(boolean)
.timeoutSeconds(int)               esperas de elementos — default 30
.pageLoadTimeoutSeconds(int)       carga de página — default 60
.scoringProfile(ScoringProfile)    DEFAULT / SIEBEL / ANGULAR / LEGACY
.healingUrl(String)                default http://localhost:8765
.proxy(host, port)
.chromeArg(String)                 se puede llamar múltiples veces
.chromeArgs(String...)
.chromePref(key, value)            preferencias de Chrome
.customizeChrome(Consumer<ChromeOptions>)
.customizeEdge(Consumer<EdgeOptions>)
.customizeFirefox(Consumer<FirefoxOptions>)
```
