package com.selfhealing.framework.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que mapea la respuesta del endpoint {@code POST /heal}.
 *
 * <h3>Ejemplo de respuesta exitosa (DOM):</h3>
 * <pre>
 * {
 *   "healed": true,
 *   "new_selector": "//button[@data-testid='login-btn']",
 *   "selector_type": "xpath",
 *   "strategy_used": "DOM",
 *   "confidence": 0.87,
 *   "healing_event_id": 42,
 *   "from_cache": false
 * }
 * </pre>
 *
 * <h3>Ejemplo de respuesta exitosa (CV — coordenadas):</h3>
 * <pre>
 * {
 *   "healed": true,
 *   "new_selector": "coords::320,150",
 *   "selector_type": "coords",
 *   "strategy_used": "CV",
 *   "confidence": 0.91,
 *   "healing_event_id": 44,
 *   "from_cache": false
 * }
 * </pre>
 *
 * <p>Cuando {@code selector_type == "coords"}, {@code new_selector} contiene las
 * coordenadas en pantalla en el formato {@code "coords::x,y"} y el cliente debe
 * usar {@code document.elementFromPoint(x, y)} para localizar el elemento.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealResponse {

    private boolean healed;

    @JsonProperty("new_selector")
    private String newSelector;

    @JsonProperty("selector_type")
    private String selectorType;

    @JsonProperty("strategy_used")
    private String strategyUsed;

    private double confidence;
    private String message;

    /** ID del evento de healing en el servidor. Necesario para enviar POST /heal/feedback. */
    @JsonProperty("healing_event_id")
    private int healingEventId;

    /** {@code true} si el selector sanado viene de la caché de eventos anteriores. */
    @JsonProperty("from_cache")
    private boolean fromCache;

    public boolean isHealed()           { return healed; }
    public String  getNewSelector()     { return newSelector; }
    public String  getSelectorType()    { return selectorType; }
    public String  getStrategyUsed()    { return strategyUsed; }
    public double  getConfidence()      { return confidence; }
    public String  getMessage()         { return message; }
    public int     getHealingEventId()  { return healingEventId; }
    public boolean isFromCache()        { return fromCache; }

    @Override
    public String toString() {
        return String.format(
            "HealResponse{healed=%b, selector='%s', type='%s', strategy='%s', " +
            "confidence=%.2f, eventId=%d, fromCache=%b, message='%s'}",
            healed, newSelector, selectorType, strategyUsed,
            confidence, healingEventId, fromCache, message);
    }
}
