package com.selfhealing.framework.healing;

import com.selfhealing.framework.client.ElementMeta;
import com.selfhealing.framework.client.HealContext;
import com.selfhealing.framework.client.HealResponse;
import com.selfhealing.framework.client.HealingClient;
import com.selfhealing.framework.client.HealingException;
import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.repair.RepairRepository;
import com.selfhealing.framework.repair.SuggestedLocator;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.*;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Operaciones del servicio de self-healing.
 *
 * <h3>Flujo de registro y sanación:</h3>
 * <ol>
 *   <li>Llamar {@link #register} cuando el selector funciona para guardar el baseline.</li>
 *   <li>Cuando el selector falla, llamar {@link #heal} — el servicio busca el elemento
 *       usando el baseline y el DOM/screenshot actuales.</li>
 *   <li>Tras usar el selector sanado, el cliente envía {@code POST /heal/feedback}
 *       en un hilo de fondo para que el servicio aprenda del resultado.</li>
 * </ol>
 *
 * <h3>Manejo de errores:</h3>
 * <ul>
 *   <li>404 — sin baseline → el test falla con el error original de Selenium</li>
 *   <li>422 — ningún motor resolvió → el test falla con el error original</li>
 *   <li>5xx / timeout → el test falla con el error original (nunca cuelga)</li>
 *   <li>selector_type="coords" → se resuelve con {@code document.elementFromPoint(x,y)}</li>
 * </ul>
 */
public class HealingActions {

    private final WebDriver        driver;
    private final HealingClient    client;
    private final Waits            waits;
    private final String           project;
    private final String           scoringProfile;
    private final RepairRepository repairRepo;      // null = caché desactivado
    private final int              cacheTtlDays;
    private final int              cacheMinScore;

    /**
     * Constructor sin caché local — siempre llama al servicio Python.
     */
    public HealingActions(WebDriver driver, HealingClient client, Waits waits,
                          String project, String scoringProfile) {
        this(driver, client, waits, project, scoringProfile, null, 7, 80);
    }

    /**
     * Constructor con caché local habilitado.
     *
     * @param repairRepo   repositorio SQLite local (puede ser null para deshabilitar caché)
     * @param cacheTtlDays días máximos de vida de una entrada en caché
     * @param cacheMinScore score mínimo para usar una entrada del caché
     */
    public HealingActions(WebDriver driver, HealingClient client, Waits waits,
                          String project, String scoringProfile,
                          RepairRepository repairRepo, int cacheTtlDays, int cacheMinScore) {
        this.driver         = driver;
        this.client         = client;
        this.waits          = waits;
        this.project        = project;
        this.scoringProfile = scoringProfile;
        this.repairRepo     = repairRepo;
        this.cacheTtlDays   = cacheTtlDays;
        this.cacheMinScore  = cacheMinScore;
    }

    // ── Registro de baseline ──────────────────────────────────────────────────

    /**
     * Registra el baseline del elemento en el servicio de healing usando un testId
     * derivado automáticamente de la etiqueta del elemento.
     *
     * @param element elemento a registrar (debe ser encontrable con su selector actual)
     */
    public void register(Element element) {
        String testId = "baseline_" + element.getDisplayLabel()
            .toLowerCase()
            .replaceAll("\\s+", "_")
            .replaceAll("[^a-z0-9_]", "");
        register(element, testId);
    }

    /**
     * Registra el baseline del elemento con un identificador de test explícito.
     *
     * <p>El baseline incluye:</p>
     * <ul>
     *   <li>Metadatos del elemento: tag, id, texto, clases, atributos ARIA, contexto del padre</li>
     *   <li>Screenshot del elemento (recorte, no pantalla completa) para el motor CV</li>
     * </ul>
     *
     * @param element elemento a registrar
     * @param testId  identificador único de este baseline en el proyecto
     */
    public void register(Element element, String testId) {
        try {
            WebElement el = driver.findElement(element.toBy());
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // ── Extraer atributos del elemento ────────────────────────────
            String tag        = el.getTagName();
            String id         = nvl(el.getAttribute("id"));
            String text       = nvl(el.getText());
            String classAttr  = nvl(el.getAttribute("class"));

            List<String> classes = classAttr.isEmpty()
                ? Collections.emptyList()
                : Arrays.asList(classAttr.split("\\s+"));

            String type        = nvl(el.getAttribute("type"));
            String name        = nvl(el.getAttribute("name"));
            String ariaLabel   = nvl(el.getAttribute("aria-label"));
            String placeholder = nvl(el.getAttribute("placeholder"));
            String role        = nvl(el.getAttribute("role"));
            String dataTestId  = nvl(el.getAttribute("data-testid"));

            // ── Contexto DOM via JS ───────────────────────────────────────
            // siblings_count = total de hijos del padre (spec: children.length)
            String parentTag = nvl((String) js.executeScript(
                "return arguments[0].parentElement ? arguments[0].parentElement.tagName.toLowerCase() : ''", el));
            long siblings = ((Number) js.executeScript(
                "return arguments[0].parentElement ? arguments[0].parentElement.children.length : 0", el))
                .longValue();

            ElementMeta meta = new ElementMeta(
                tag, id, text, classes, type, name,
                ariaLabel, placeholder, role, dataTestId,
                parentTag, (int) siblings);

            // ── Screenshot del elemento (recorte — no pantalla completa) ──
            // Selenium 4 permite capturar solo el área del elemento directamente.
            // El motor CV usa este recorte como template para template-matching.
            String elementScreenshot = Base64.getEncoder()
                .encodeToString(el.getScreenshotAs(OutputType.BYTES));

            client.registerBaseline("xpath", element.toXpath(),
                project, testId, meta, elementScreenshot);

            log("Baseline registrado: %s [%s]", testId, element);

        } catch (Exception e) {
            // El registro falla silenciosamente — el healing puede trabajar con información parcial.
            // Un fallo aquí no debe interrumpir el test.
            log("WARN: No se pudo registrar baseline de %s: %s", element, e.getMessage());
        }
    }

    // ── Sanación de selectores rotos ──────────────────────────────────────────

    /**
     * Intenta encontrar el elemento con el selector original. Si falla, llama al
     * servicio de healing con el DOM actual y devuelve el elemento con el selector reparado.
     *
     * @param element elemento cuyo selector puede estar roto
     * @return el {@link WebElement} encontrado (con selector original o reparado)
     * @throws NoSuchElementException si el healing tampoco logra encontrar el elemento
     */
    public WebElement heal(Element element) {
        return heal(element, "heal_" + Math.abs(element.getValue().hashCode()), null);
    }

    /**
     * Igual que {@link #heal(Element)} pero con un identificador de test explícito.
     *
     * @param element elemento cuyo selector puede estar roto
     * @param testId  identificador para el historial de healing del servicio
     * @return el {@link WebElement} encontrado
     * @throws NoSuchElementException si el healing tampoco logra encontrar el elemento
     */
    public WebElement heal(Element element, String testId) {
        return heal(element, testId, null);
    }

    /**
     * Healing con filtros de contexto para afinar la búsqueda del motor DOM.
     *
     * <p>Usar cuando la página tiene múltiples elementos similares y el motor
     * necesita contexto adicional para elegir el correcto.</p>
     *
     * <pre>{@code
     * HealContext ctx = HealContext.create()
     *     .anchorById("campo-monto")
     *     .inForm("form-pago")
     *     .excludeId("btn-cancelar-header");
     *
     * web.healing.heal(Element.id("btn-pagar"), "test_pago", ctx);
     * }</pre>
     *
     * @param element elemento cuyo selector puede estar roto
     * @param testId  identificador para el historial de healing del servicio
     * @param context filtros de contexto (anchors, container, form, excludeIds).
     *                Pasar {@code null} para comportamiento estándar.
     * @return el {@link WebElement} encontrado
     * @throws NoSuchElementException si el healing tampoco logra encontrar el elemento
     */
    public WebElement heal(Element element, String testId, HealContext context) {
        // Deshabilitar espera implícita: necesitamos que findElement falle INMEDIATAMENTE
        // para no añadir N segundos de espera extra antes de intentar el healing.
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(0));
        try {
            return driver.findElement(element.toBy());

        } catch (NoSuchElementException original) {
            String ctxLabel = (context != null && !context.isEmpty()) ? " [con contexto]" : "";
            log("Selector roto: %s — solicitando sanación [perfil=%s%s]...",
                element, scoringProfile, ctxLabel);

            // ── 1. Consultar caché local ──────────────────────────────────────
            String pageUrl = currentPageUrl();
            if (repairRepo != null) {
                SuggestedLocator cached = null;
                try {
                    cached = repairRepo.findApprovedRepair(
                        project, pageUrl, "xpath", element.toXpath(),
                        cacheMinScore, cacheTtlDays);
                } catch (Exception e) {
                    log("WARN: Error consultando caché local: %s", e.getMessage());
                }

                if (cached != null) {
                    log("Caché local: encontrada reparación para %s → %s (score=%d)",
                        element.getValue(), cached.getValue(), cached.getScore());
                    try {
                        WebElement healed = driver.findElement(cached.toBy());
                        // Selector cacheado sigue funcionando:
                        // 1) Actualizar times_seen en caché
                        // 2) Revalidar con el servicio Python en background (mantiene el feedback loop)
                        touchCacheAsync(project, pageUrl, "xpath", element.toXpath(),
                            cached.getType(), cached.getValue());
                        revalidateAsync(element, testId, context, pageUrl,
                            driver.getPageSource(), cached);
                        log("Resuelto desde caché local: %s", cached.getValue());
                        return healed;

                    } catch (NoSuchElementException stale) {
                        // El selector cacheado ya no existe en el DOM — invalidar
                        log("Caché inválido para %s: selector '%s' no encontrado, invalidando...",
                            element.getValue(), cached.getValue());
                        try {
                            repairRepo.reject(project, pageUrl, "xpath", element.toXpath(),
                                cached.getType(), cached.getValue(),
                                "selector no encontrado en DOM");
                        } catch (Exception e) {
                            log("WARN: Error invalidando caché: %s", e.getMessage());
                        }
                        // caer al servicio Python
                    }
                }
            }

            // ── 2. Llamar al servicio Python ──────────────────────────────────
            try {
                String fullScreenshot = Base64.getEncoder()
                    .encodeToString(((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));

                HealResponse response = client.heal(
                    "xpath",
                    element.toXpath(),
                    driver.getPageSource(),
                    project,
                    testId,
                    fullScreenshot,
                    scoringProfile,
                    context);

                log("Respuesta del servicio: %s", response);

                if (response.isHealed()) {
                    WebElement healed = resolveHealedElement(response);

                    if (healed != null) {
                        log("Selector reparado: %s → %s (estrategia=%s, confianza=%.2f%s)",
                            element.getValue(),
                            response.getNewSelector(),
                            response.getStrategyUsed(),
                            response.getConfidence(),
                            response.isFromCache() ? ", desde caché del servicio" : "");

                        // Guardar en caché local para próximas ejecuciones
                        saveToLocalCacheAsync(project, pageUrl, "xpath", element.toXpath(), response);

                        // Feedback positivo al servicio en background
                        sendFeedbackAsync(response.getHealingEventId(), true, null);
                        return healed;

                    } else {
                        log("WARN: El selector sanado '%s' no se encontró en el DOM",
                            response.getNewSelector());
                        sendFeedbackAsync(response.getHealingEventId(), false, null);
                    }
                }

            } catch (HealingException he) {
                logHealingError(he, element);
            } catch (Exception e) {
                log("ERROR inesperado durante healing de %s: %s", element, e.getMessage());
            }

            // En todos los casos de fallo: relanzar la excepción original de Selenium
            throw original;

        } finally {
            // Restaurar siempre el implicit wait al valor original del framework
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(3));
        }
    }

    // ── Atajos heal + acción ──────────────────────────────────────────────────

    /**
     * Sana el selector y hace click directamente sobre el elemento encontrado.
     *
     * @param element elemento a sanar y hacer click
     */
    public void healAndClick(Element element) {
        heal(element).click();
    }

    /**
     * Sana el selector con contexto y hace click.
     *
     * @param element elemento a sanar y hacer click
     * @param context filtros de contexto para afinar la búsqueda
     */
    public void healAndClick(Element element, HealContext context) {
        heal(element, "heal_" + Math.abs(element.getValue().hashCode()), context).click();
    }

    /**
     * Sana el selector, limpia el campo y escribe el texto indicado.
     *
     * @param element elemento a sanar
     * @param text    texto a escribir en el campo
     */
    public void healAndType(Element element, String text) {
        WebElement el = heal(element);
        el.clear();
        el.sendKeys(text);
    }

    /**
     * Sana el selector con contexto, limpia el campo y escribe el texto indicado.
     *
     * @param element elemento a sanar
     * @param text    texto a escribir en el campo
     * @param context filtros de contexto para afinar la búsqueda
     */
    public void healAndType(Element element, String text, HealContext context) {
        WebElement el = heal(element, "heal_" + Math.abs(element.getValue().hashCode()), context);
        el.clear();
        el.sendKeys(text);
    }

    // ── Resolución del selector sanado ────────────────────────────────────────

    /**
     * Obtiene el {@link WebElement} a partir de la respuesta del servicio.
     *
     * <p>Dos casos posibles:</p>
     * <ul>
     *   <li>XPath / CSS normal → {@code driver.findElement(By.xpath(newSelector))}</li>
     *   <li>Coordenadas CV ({@code "coords::x,y"}) → {@code document.elementFromPoint(x,y)}</li>
     * </ul>
     *
     * @return el elemento encontrado, o {@code null} si no se pudo localizar
     */
    private WebElement resolveHealedElement(HealResponse response) {
        String newSelector  = response.getNewSelector();
        String selectorType = response.getSelectorType();

        try {
            if ("coords".equals(selectorType)) {
                return resolveByCoords(newSelector);
            } else {
                return driver.findElement(By.xpath(newSelector));
            }
        } catch (NoSuchElementException | NullPointerException e) {
            return null;
        }
    }

    /**
     * Resuelve un selector de tipo {@code "coords::x,y"} usando JavaScript
     * {@code document.elementFromPoint(x, y)}.
     *
     * @param coordsSelector selector en formato {@code "coords::320,150"}
     * @return el elemento en esa posición de pantalla, o {@code null} si no hay ninguno
     */
    private WebElement resolveByCoords(String coordsSelector) {
        String raw   = coordsSelector.replace("coords::", "").trim();
        String[] parts = raw.split(",");
        int x = Integer.parseInt(parts[0].trim());
        int y = Integer.parseInt(parts[1].trim());

        log("Resolviendo por coordenadas: elementFromPoint(%d, %d)", x, y);

        Object result = ((JavascriptExecutor) driver).executeScript(
            "return document.elementFromPoint(arguments[0], arguments[1]);", x, y);

        return (result instanceof WebElement) ? (WebElement) result : null;
    }

    // ── Caché local — operaciones asíncronas ─────────────────────────────────

    /**
     * Incrementa times_seen en caché en background — no bloquea el test.
     */
    private void touchCacheAsync(String app, String pageUrl,
                                 String origType, String origValue,
                                 String repType, String repValue) {
        if (repairRepo == null) return;
        Thread t = new Thread(() -> {
            try {
                repairRepo.touch(app, pageUrl, origType, origValue, repType, repValue);
            } catch (Exception e) {
                log("WARN: Error actualizando caché local: %s", e.getMessage());
            }
        });
        t.setDaemon(true);
        t.setName("SelfHealing-Cache-Touch");
        t.start();
    }

    /**
     * Guarda la reparación devuelta por el servicio en el caché local en background.
     */
    private void saveToLocalCacheAsync(String app, String pageUrl,
                                       String origType, String origValue,
                                       HealResponse response) {
        if (repairRepo == null) return;
        // Excluir coords — no son selectores almacenables
        if ("coords".equals(response.getSelectorType())) return;

        SuggestedLocator toCache = new SuggestedLocator();
        toCache.setType(response.getSelectorType());
        toCache.setValue(response.getNewSelector());
        toCache.setScore((int) (response.getConfidence() * 100));
        toCache.setReason(response.getMessage());

        Thread t = new Thread(() -> {
            try {
                repairRepo.saveOrUpdate(app, pageUrl, origType, origValue, toCache);
            } catch (Exception e) {
                log("WARN: Error guardando en caché local: %s", e.getMessage());
            }
        });
        t.setDaemon(true);
        t.setName("SelfHealing-Cache-Save");
        t.start();
    }

    /**
     * Revalida una entrada de caché llamando al servicio Python en background.
     *
     * <p>Patrón stale-while-revalidate: el test usa el resultado del caché inmediatamente,
     * pero en background se verifica con el servicio si sigue siendo la mejor opción.
     * Si el servicio devuelve un selector diferente y mejor, se actualiza el caché.</p>
     *
     * <p>También garantiza que el servicio Python reciba actividad y pueda actualizar
     * sus propias estadísticas y pesos — manteniendo el feedback loop vivo.</p>
     */
    private void revalidateAsync(Element element, String testId, HealContext context,
                                 String pageUrl, String domSnapshot,
                                 SuggestedLocator currentCached) {
        if (repairRepo == null) return;

        Thread t = new Thread(() -> {
            try {
                HealResponse response = client.heal(
                    "xpath", element.toXpath(),
                    domSnapshot,
                    project, testId,
                    null,           // sin screenshot — revalidación ligera
                    scoringProfile,
                    context);

                if (response.isHealed()) {
                    // Feedback positivo al servicio (mantiene sus estadísticas actualizadas)
                    sendFeedbackAsync(response.getHealingEventId(), true, null);

                    // Si el servicio devuelve un selector diferente y mejor, actualizar caché
                    boolean sameSelector = currentCached.getValue().equals(response.getNewSelector())
                        && currentCached.getType().equals(response.getSelectorType());
                    if (!sameSelector && !"coords".equals(response.getSelectorType())) {
                        SuggestedLocator fresh = new SuggestedLocator();
                        fresh.setType(response.getSelectorType());
                        fresh.setValue(response.getNewSelector());
                        fresh.setScore((int) (response.getConfidence() * 100));
                        fresh.setReason(response.getMessage());
                        repairRepo.saveOrUpdate(project, pageUrl, "xpath",
                            element.toXpath(), fresh);
                        log("Caché actualizado por revalidación: %s → %s",
                            currentCached.getValue(), fresh.getValue());
                    }
                }
            } catch (Exception e) {
                // La revalidación es opcional — nunca debe afectar el test
                log("WARN: Error en revalidación background de %s: %s",
                    element.getValue(), e.getMessage());
            }
        });
        t.setDaemon(true);
        t.setName("SelfHealing-Revalidate-" + element.getDisplayLabel());
        t.start();
    }

    /** Captura la URL actual de forma segura (sin lanzar si el driver está en mal estado). */
    private String currentPageUrl() {
        try {
            return driver.getCurrentUrl();
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ── Feedback asíncrono ────────────────────────────────────────────────────

    /**
     * Envía feedback al servicio en un hilo de fondo para no añadir latencia al test.
     * Los errores de feedback se silencian — son opcionales para el test.
     *
     * @param healingEventId  ID del evento devuelto por {@code /heal}
     * @param correct         {@code true} si el elemento fue encontrado con el selector sanado
     * @param confirmedSelector selector correcto confirmado (puede ser {@code null})
     */
    private void sendFeedbackAsync(int healingEventId, boolean correct, String confirmedSelector) {
        Thread feedbackThread = new Thread(() -> {
            try {
                client.sendFeedback(healingEventId, correct, confirmedSelector);
                log("Feedback enviado: eventId=%d, correct=%b", healingEventId, correct);
            } catch (Exception e) {
                // El feedback nunca debe hacer fallar el test — silenciar
                log("WARN: No se pudo enviar feedback (eventId=%d): %s",
                    healingEventId, e.getMessage());
            }
        });
        feedbackThread.setDaemon(true);  // el hilo termina cuando la JVM termina
        feedbackThread.setName("SelfHealing-Feedback-" + healingEventId);
        feedbackThread.start();
    }

    // ── Helpers internos ──────────────────────────────────────────────────────

    /** Loguea el error de healing con el mensaje apropiado según el código HTTP. */
    private void logHealingError(HealingException he, Element element) {
        switch (he.getStatusCode()) {
            case 404:
                log("Sin baseline registrado para '%s' — el test fallará con el selector original",
                    element);
                break;
            case 422:
                log("Ningún motor pudo sanar '%s': %s — el test fallará con el selector original",
                    element, he.getMessage());
                break;
            case 0:
                log("Servicio no disponible (timeout/conexión) para '%s' — el test fallará normalmente",
                    element);
                break;
            default:
                log("Servicio devolvió HTTP %d para '%s' — el test fallará normalmente",
                    he.getStatusCode(), element);
                break;
        }
    }

    /** Convierte {@code null} a cadena vacía para evitar NPE al construir {@link ElementMeta}. */
    private static String nvl(String value) {
        return value != null ? value : "";
    }

    private void log(String msg, Object... args) {
        System.out.printf("[HealingActions] " + msg + "%n", args);
    }
}
