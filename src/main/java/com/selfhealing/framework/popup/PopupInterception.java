package com.selfhealing.framework.popup;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Registro inmutable de un popup que fue interceptado y manejado por {@link PopupGuard}.
 *
 * <p>Cada vez que {@code PopupGuard} detecta y actúa sobre un popup, crea una
 * instancia de esta clase y la añade al historial. El historial puede consultarse
 * desde el test para hacer aserciones o incluirlo en reportes de evidencia.</p>
 *
 * <h3>Ejemplo de uso en un test:</h3>
 * <pre>{@code
 * web.popupGuard.safely(() -> web.actions.click(guardarButton));
 *
 * // Verificar que no apareció ningún popup inesperado
 * assertTrue(web.popupGuard.getInterceptions().isEmpty());
 *
 * // O consultar qué popups aparecieron
 * web.popupGuard.getInterceptions().forEach(i -> System.out.println(i.getSummary()));
 * }</pre>
 */
public class PopupInterception {

    /** Tipos de popup que el guard puede interceptar. */
    public enum PopupType {
        /** Alertas nativas del navegador: alert(), confirm(), prompt() */
        NATIVE_ALERT,
        /** Modal HTML registrado con una regla de usuario */
        REGISTERED_MODAL,
        /** Popup desconocido cerrado por los patrones genéricos del guard */
        UNKNOWN_MODAL,
        /** Popup desconocido que no pudo cerrarse automáticamente */
        UNHANDLED
    }

    /** Acción que tomó el guard sobre el popup. */
    public enum ActionTaken {
        ACCEPTED,    // alerta nativa aceptada
        DISMISSED,   // alerta nativa descartada / ESC
        CLOSED,      // botón de cierre encontrado y pulsado
        CUSTOM,      // handler personalizado del usuario ejecutado
        SCREENSHOT,  // solo se tomó screenshot (no se pudo cerrar)
        NONE         // no se tomó ninguna acción
    }

    private final PopupType   type;
    private final ActionTaken action;
    private final String      ruleName;       // nombre de la regla que lo capturó
    private final String      message;        // texto del popup o descripción del elemento
    private final String      screenshotPath; // ruta al screenshot de evidencia (puede ser null)
    private final LocalDateTime timestamp;

    /** Constructor completo — usado internamente por PopupGuard. */
    public PopupInterception(PopupType type, ActionTaken action,
                              String ruleName, String message, String screenshotPath) {
        this.type           = type;
        this.action         = action;
        this.ruleName       = ruleName;
        this.message        = message;
        this.screenshotPath = screenshotPath;
        this.timestamp      = LocalDateTime.now();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public PopupType   getType()           { return type;           }
    public ActionTaken getAction()         { return action;         }
    public String      getRuleName()       { return ruleName;       }
    public String      getMessage()        { return message;        }
    public String      getScreenshotPath() { return screenshotPath; }
    public LocalDateTime getTimestamp()    { return timestamp;      }

    /** Indica si el popup pudo manejarse exitosamente (no quedó bloqueando la pantalla). */
    public boolean wasHandled() {
        return action != ActionTaken.NONE && action != ActionTaken.SCREENSHOT;
    }

    /** Indica si el popup era de un tipo desconocido (no tenía regla registrada). */
    public boolean wasUnexpected() {
        return type == PopupType.UNKNOWN_MODAL || type == PopupType.UNHANDLED;
    }

    /**
     * Resumen en una línea para logs y reportes.
     *
     * @return cadena descriptiva del evento de interceptación
     */
    public String getSummary() {
        return String.format("[%s] PopupGuard interceptó (%s) — Tipo: %s | Acción: %s | Mensaje: '%s'%s",
            timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            ruleName,
            type,
            action,
            message,
            screenshotPath != null ? " | Screenshot: " + screenshotPath : "");
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
