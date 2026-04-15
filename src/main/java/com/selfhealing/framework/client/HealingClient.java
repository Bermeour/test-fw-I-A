package com.selfhealing.framework.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente HTTP para el servicio de self-healing.
 *
 * <h3>Contratos con el servicio:</h3>
 * <ul>
 *   <li>{@code POST /baseline/register} — registrar metadatos + screenshot del elemento cuando funciona</li>
 *   <li>{@code POST /heal}              — solicitar reparación de un selector roto</li>
 *   <li>{@code POST /heal/feedback}     — informar si el selector sanado fue correcto</li>
 *   <li>{@code GET  /health}            — verificar que el servicio está activo</li>
 *   <li>{@code GET  /metrics/:project}  — estadísticas de sanaciones por proyecto</li>
 *   <li>{@code GET  /history/:project}  — historial de eventos de healing</li>
 * </ul>
 *
 * <h3>Manejo de errores HTTP:</h3>
 * <ul>
 *   <li>{@code 201 / 200} — éxito</li>
 *   <li>{@code 404} — sin baseline → lanza {@link HealingException}(404), sin reintentos</li>
 *   <li>{@code 422} — ningún motor resolvió → lanza {@link HealingException}(422), sin reintentos</li>
 *   <li>{@code 5xx} — error del servicio → reintenta hasta {@value #MAX_RETRIES} veces con backoff</li>
 *   <li>Timeout / IOException → reintenta hasta {@value #MAX_RETRIES} veces con backoff</li>
 * </ul>
 *
 * <h3>Timeouts:</h3>
 * <p>Conexión y respuesta: {@value #HTTP_TIMEOUT_SECONDS}s. Si el servicio no responde
 * en ese tiempo, el cliente lanza excepción para no colgar el test indefinidamente.</p>
 */
public class HealingClient {

    // ── Configuración de reintentos y timeouts ────────────────────────────────

    /** Timeout de conexión y lectura de respuesta HTTP en segundos. */
    private static final int HTTP_TIMEOUT_SECONDS = 10;

    /** Máximo de reintentos para errores 5xx y problemas de conexión. */
    private static final int MAX_RETRIES = 3;

    /** Pausas entre reintentos: 500 ms → 1000 ms → 2000 ms. */
    private static final long[] BACKOFF_MS = {500L, 1000L, 2000L};

    // ── Estado interno ────────────────────────────────────────────────────────

    private final String baseUrl;
    private final CloseableHttpClient http;
    private final ObjectMapper mapper;

    // ── Constructores ─────────────────────────────────────────────────────────

    public HealingClient() {
        this("http://localhost:8765");
    }

    public HealingClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http    = buildHttpClient();
        this.mapper  = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ── POST /baseline/register ───────────────────────────────────────────────

    /**
     * Registra el baseline de un elemento cuando su selector funciona correctamente.
     *
     * <p>El {@code screenshotBase64} debe ser el recorte del elemento (no pantalla completa).
     * Selenium 4 lo genera con {@code element.getScreenshotAs(OutputType.BYTES)}.</p>
     *
     * @param selectorType    tipo de selector: "xpath" o "css"
     * @param selectorValue   valor del selector que funciona actualmente
     * @param project         nombre del proyecto en el servicio
     * @param testId          identificador único de este elemento en el proyecto
     * @param meta            metadatos del elemento (tag, id, clases, atributos, contexto)
     * @param screenshotBase64 recorte del elemento codificado en Base64
     */
    public void registerBaseline(String selectorType, String selectorValue,
                                  String project, String testId,
                                  ElementMeta meta, String screenshotBase64) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("selector_type",   selectorType);
        body.put("selector_value",  selectorValue);
        body.put("project",         project);
        body.put("test_id",         testId);
        body.put("element_meta",    meta);
        body.put("screenshot_base64", screenshotBase64 != null ? screenshotBase64 : "");

        post("/baseline/register", body, String.class);
    }

    // ── POST /heal ────────────────────────────────────────────────────────────

    /**
     * Solicita al servicio que repare un selector roto.
     *
     * @param selectorType    tipo del selector roto: "xpath" o "css"
     * @param selectorValue   valor del selector que ya no funciona
     * @param domHtml         HTML completo de la página ({@code driver.getPageSource()})
     * @param project         nombre del proyecto en el servicio
     * @param testId          identificador del elemento (mismo que se usó en register)
     * @param screenshotBase64 pantalla completa en Base64 (para motor CV)
     * @param scoringProfile  perfil de scoring: "default" | "siebel" | "angular" | "legacy"
     * @return respuesta del servicio con el nuevo selector y el healing_event_id
     */
    public HealResponse heal(String selectorType, String selectorValue,
                              String domHtml, String project, String testId,
                              String screenshotBase64, String scoringProfile) throws IOException {
        return heal(selectorType, selectorValue, domHtml, project, testId,
                    screenshotBase64, scoringProfile, null);
    }

    /**
     * Solicita al servicio que repare un selector roto, con filtros de contexto opcionales
     * que ayudan al motor DOM a encontrar el candidato correcto cuando hay elementos similares.
     *
     * <p>El {@code screenshotBase64} debe ser la pantalla completa (no el recorte del elemento)
     * para que el motor CV pueda buscar el template visual en toda la pantalla.</p>
     *
     * @param selectorType    tipo del selector roto: "xpath" o "css"
     * @param selectorValue   valor del selector que ya no funciona
     * @param domHtml         HTML completo de la página ({@code driver.getPageSource()})
     * @param project         nombre del proyecto en el servicio
     * @param testId          identificador del elemento (mismo que se usó en register)
     * @param screenshotBase64 pantalla completa en Base64 (para motor CV)
     * @param scoringProfile  perfil de scoring: "default" | "siebel" | "angular" | "legacy"
     * @param context         filtros de contexto opcionales (anchors, container, form, excludeIds).
     *                        Pasar {@code null} para comportamiento estándar sin filtros.
     * @return respuesta del servicio con el nuevo selector y el healing_event_id
     * @throws HealingException si el servicio devuelve 404 (sin baseline), 422 (no resuelto),
     *                          5xx (error del servidor) o hay un problema de conexión
     */
    public HealResponse heal(String selectorType, String selectorValue,
                              String domHtml, String project, String testId,
                              String screenshotBase64, String scoringProfile,
                              HealContext context) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("selector_type",   selectorType);
        body.put("selector_value",  selectorValue);
        body.put("dom_html",        domHtml);
        body.put("project",         project);
        body.put("test_id",         testId);
        body.put("scoring_profile", scoringProfile);
        if (screenshotBase64 != null && !screenshotBase64.isEmpty()) {
            body.put("screenshot_base64", screenshotBase64);
        }

        // Filtros de contexto — solo se envían si hay algo definido
        if (context != null && !context.isEmpty()) {
            if (!context.getAnchors().isEmpty())
                body.put("anchors", context.getAnchors());
            if (!context.getExcludeIds().isEmpty())
                body.put("exclude_ids", context.getExcludeIds());
            if (context.getContainerId() != null)
                body.put("container_id", context.getContainerId());
            if (context.getContainerClass() != null)
                body.put("container_class", context.getContainerClass());
            if (context.getFormId() != null)
                body.put("form_id", context.getFormId());
        }

        return post("/heal", body, HealResponse.class);
    }

    // ── POST /heal/feedback ───────────────────────────────────────────────────

    /**
     * Envía feedback al servicio sobre si el selector sanado fue correcto.
     *
     * <p>Este método es síncrono pero debe llamarse desde un hilo de fondo para no
     * añadir latencia al test. Errores en el feedback se silencian — son opcionales
     * para el test pero importantes para el aprendizaje del servicio.</p>
     *
     * @param healingEventId    ID del evento devuelto por {@code /heal}
     * @param correct           {@code true} si el elemento se encontró con el selector sanado
     * @param confirmedSelector selector correcto confirmado por el cliente (puede ser {@code null})
     */
    public void sendFeedback(int healingEventId, boolean correct,
                              String confirmedSelector) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("healing_event_id",  healingEventId);
        body.put("correct",           correct);
        body.put("confirmed_selector", confirmedSelector);  // null se serializa como null en JSON

        post("/heal/feedback", body, String.class);
    }

    // ── GET endpoints de monitorización ──────────────────────────────────────

    /** Verifica que el servicio está activo. Devuelve {@code {"status": "ok"}}. */
    public Map<String, Object> getHealth() throws IOException {
        return get("/health", new TypeReference<Map<String, Object>>() {});
    }

    /** Estadísticas de sanaciones del proyecto (total, by_strategy, etc.). */
    public Map<String, Object> getMetrics(String project) throws IOException {
        return get("/metrics/" + project, new TypeReference<Map<String, Object>>() {});
    }

    /** Historial de los últimos {@code limit} eventos de healing del proyecto. */
    public Map<String, Object> getHistory(String project, int limit) throws IOException {
        return get("/history/" + project + "?limit=" + limit,
            new TypeReference<Map<String, Object>>() {});
    }

    // ── Cierre ───────────────────────────────────────────────────────────────

    public void close() throws IOException {
        http.close();
    }

    // ── HTTP interno — POST con reintentos y manejo de status ─────────────────

    /**
     * Ejecuta un POST con reintentos para errores 5xx y problemas de red.
     *
     * <p>Política de reintentos:</p>
     * <ul>
     *   <li>404 / 422 → falla inmediatamente (son respuestas definitivas del servicio)</li>
     *   <li>5xx        → reintenta hasta {@value #MAX_RETRIES} veces con backoff</li>
     *   <li>IOException / timeout → reintenta hasta {@value #MAX_RETRIES} veces con backoff</li>
     * </ul>
     */
    private <T> T post(String path, Object body, Class<T> responseType) throws IOException {
        String json = mapper.writeValueAsString(body);
        IOException lastIoException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpPost request = new HttpPost(baseUrl + path);
            request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

            try {
                // Arrays de un elemento como contenedor mutable dentro de la lambda
                int[]    statusHolder = new int[1];
                String[] bodyHolder   = new String[1];

                http.execute(request, response -> {
                    statusHolder[0] = response.getCode();
                    bodyHolder[0]   = EntityUtils.toString(response.getEntity());
                    return null;
                });

                int    status       = statusHolder[0];
                String responseBody = bodyHolder[0];

                // ── Errores definitivos — no reintentar ────────────────────
                if (status == 404) {
                    throw new HealingException(parseDetail(responseBody), 404);
                }
                if (status == 422) {
                    throw new HealingException(parseDetail(responseBody), 422);
                }

                // ── Error del servidor — reintentar ────────────────────────
                if (status >= 500) {
                    if (attempt < MAX_RETRIES) {
                        log("WARN: servicio devolvió %d en intento %d/%d — reintentando en %dms",
                            status, attempt, MAX_RETRIES, BACKOFF_MS[attempt - 1]);
                        sleep(BACKOFF_MS[attempt - 1]);
                        continue;
                    }
                    throw new HealingException(
                        "Servicio no disponible (HTTP " + status + ")", status);
                }

                // ── Éxito (200 / 201) ──────────────────────────────────────
                if (responseType == String.class) return responseType.cast(responseBody);
                return mapper.readValue(responseBody, responseType);

            } catch (HealingException he) {
                throw he; // nunca reintentar 404/422

            } catch (IOException e) {
                lastIoException = e;
                if (attempt < MAX_RETRIES) {
                    log("WARN: error de conexión en intento %d/%d (%s) — reintentando en %dms",
                        attempt, MAX_RETRIES, e.getMessage(), BACKOFF_MS[attempt - 1]);
                    sleep(BACKOFF_MS[attempt - 1]);
                }
            }
        }

        // Agotados los reintentos por IOException
        String cause = lastIoException != null ? lastIoException.getMessage() : "desconocido";
        throw new HealingException(
            "Servicio no disponible tras " + MAX_RETRIES + " intentos: " + cause, 0);
    }

    // ── HTTP interno — GET (sin reintentos — solo monitorización) ─────────────

    private <T> T get(String path, TypeReference<T> typeRef) throws IOException {
        HttpGet request = new HttpGet(baseUrl + path);
        return http.execute(request, response -> {
            String json = EntityUtils.toString(response.getEntity());
            return mapper.readValue(json, typeRef);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Construye el HttpClient con timeouts explícitos para evitar que tests cuelguen
     * si el servicio de healing no está disponible.
     */
    private static CloseableHttpClient buildHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .setResponseTimeout(Timeout.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .build();

        return HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .build();
    }

    /**
     * Extrae el campo {@code "detail"} del cuerpo JSON de error.
     * Si el body no es JSON válido o no tiene {@code detail}, devuelve el body completo.
     */
    private String parseDetail(String responseBody) {
        try {
            Map<?, ?> map = mapper.readValue(responseBody, Map.class);
            Object detail = map.get("detail");
            return detail != null ? detail.toString() : responseBody;
        } catch (Exception ignored) {
            return responseBody;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String msg, Object... args) {
        System.out.printf("[HealingClient] " + msg + "%n", args);
    }
}
