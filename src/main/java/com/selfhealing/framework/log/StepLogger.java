package com.selfhealing.framework.log;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logger de pasos de automatización — thread-local, sin dependencias externas.
 *
 * <p>Cada acción del framework (type, click, scroll…) llama a {@link #step} para
 * dejar trazas legibles en consola y acumularlas en memoria para reportes.</p>
 *
 * <h3>Salida en consola:</h3>
 * <pre>
 * [10:23:41] STEP  type       → Campo usuario            = "admin"
 * [10:23:42] STEP  type       → Campo contraseña         = "****"
 * [10:23:42] STEP  click      → Botón login
 * [10:23:43] STEP  visible    → Resultado login          ✓
 * </pre>
 *
 * <h3>Uso:</h3>
 * <pre>{@code
 * // El framework lo llama internamente — el usuario solo lee los logs.
 *
 * // Para imprimir el resumen al final del test:
 * StepLogger.printSummary();
 *
 * // Para limpiar entre tests (BaseTest lo hace automáticamente en @BeforeEach):
 * StepLogger.clear();
 * }</pre>
 */
public final class StepLogger {

    private static final ThreadLocal<List<StepEntry>> STEPS =
        ThreadLocal.withInitial(ArrayList::new);

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private StepLogger() {}

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    /**
     * Registra un paso de automatización.
     *
     * @param action       nombre de la acción (ej: "click", "type", "scroll")
     * @param elementLabel etiqueta legible del elemento (ej: "Botón login")
     * @param detail       detalle extra como el valor escrito, o {@code null} si no aplica
     */
    public static void step(String action, String elementLabel, String detail) {
        String time = LocalTime.now().format(TIME_FMT);
        String line = format(action, elementLabel, detail);
        System.out.printf("[%s] STEP  %s%n", time, line);
        STEPS.get().add(new StepEntry(time, action, elementLabel, detail));
    }

    /**
     * Registra un paso sin detalle extra (para acciones sin valor, como click).
     */
    public static void step(String action, String elementLabel) {
        step(action, elementLabel, null);
    }

    /**
     * Devuelve los pasos acumulados en el hilo actual (solo lectura).
     */
    public static List<StepEntry> getSteps() {
        return Collections.unmodifiableList(STEPS.get());
    }

    /**
     * Limpia el log del hilo actual. Llamar en {@code @BeforeEach}.
     */
    public static void clear() {
        STEPS.get().clear();
    }

    /**
     * Imprime un resumen de todos los pasos acumulados en el hilo actual.
     * Útil al final del test para tener contexto en caso de fallo.
     */
    public static void printSummary() {
        List<StepEntry> steps = STEPS.get();
        String line = "─".repeat(60);
        System.out.println("\n" + line);
        System.out.printf("  Resumen de pasos (%d)%n", steps.size());
        System.out.println(line);
        if (steps.isEmpty()) {
            System.out.println("  (sin pasos registrados)");
        } else {
            for (int i = 0; i < steps.size(); i++) {
                StepEntry e = steps.get(i);
                System.out.printf("  %2d. [%s] %s%n", i + 1, e.time, format(e.action, e.elementLabel, e.detail));
            }
        }
        System.out.println(line + "\n");
    }

    // -------------------------------------------------------------------------
    // Helpers internos
    // -------------------------------------------------------------------------

    private static String format(String action, String elementLabel, String detail) {
        String act  = padRight(action, 10);
        String elem = padRight(elementLabel, 32);
        String det  = (detail != null && !detail.isEmpty()) ? "= " + detail : "";
        return act + "→ " + elem + det;
    }

    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s + " ";
        return s + " ".repeat(width - s.length());
    }

    // -------------------------------------------------------------------------
    // Entrada de log inmutable
    // -------------------------------------------------------------------------

    /** Entrada inmutable del log de pasos. */
    public static final class StepEntry {
        public final String time;
        public final String action;
        public final String elementLabel;
        public final String detail;

        StepEntry(String time, String action, String elementLabel, String detail) {
            this.time         = time;
            this.action       = action;
            this.elementLabel = elementLabel;
            this.detail       = detail;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s → %s%s", time, action, elementLabel,
                detail != null ? " = " + detail : "");
        }
    }
}
