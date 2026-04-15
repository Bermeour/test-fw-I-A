package com.selfhealing.framework.client;

/**
 * Excepción lanzada por {@link HealingClient} cuando el servicio de healing
 * devuelve un error HTTP conocido o no está disponible.
 *
 * <p>El código HTTP permite a {@link com.selfhealing.framework.healing.HealingActions}
 * distinguir entre los distintos escenarios y loguear el mensaje apropiado:</p>
 *
 * <ul>
 *   <li>{@code 404} — no hay baseline registrado para ese selector/proyecto</li>
 *   <li>{@code 422} — el servicio recibió la petición pero ningún motor encontró el elemento</li>
 *   <li>{@code 5xx} — error interno del servicio (se reintentó y agotó los intentos)</li>
 *   <li>{@code 0}   — error de conexión o timeout sin respuesta HTTP</li>
 * </ul>
 *
 * <p>En todos los casos, {@code HealingActions} captura esta excepción y relanza
 * la excepción original de Selenium para que el test falle con un mensaje claro.</p>
 */
public class HealingException extends RuntimeException {

    private final int statusCode;

    /**
     * @param message    descripción del error (incluye detalle del servicio si está disponible)
     * @param statusCode código HTTP de la respuesta; {@code 0} si no hubo respuesta
     */
    public HealingException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Código HTTP de la respuesta del servicio.
     * {@code 0} indica error de conexión o timeout (sin respuesta HTTP recibida).
     */
    public int getStatusCode() {
        return statusCode;
    }
}
