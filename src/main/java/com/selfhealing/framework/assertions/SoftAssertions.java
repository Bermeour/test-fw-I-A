package com.selfhealing.framework.assertions;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;

/**
 * Assertions suaves — acumula todos los fallos y los lanza juntos al final.
 *
 * <p>A diferencia de las assertions estrictas (que paran el test al primer fallo),
 * {@code SoftAssertions} sigue verificando todas las condiciones y luego reporta
 * todos los problemas en un único mensaje. Esto acelera el diagnóstico porque
 * muestra todos los fallos en una sola ejecución.</p>
 *
 * <h3>Uso recomendado via {@code web.softAssert(...)}:</h3>
 * <pre>{@code
 * web.softAssert(sa -> {
 *     sa.check(titulo)   .hasText("Dashboard");
 *     sa.check(menu)     .isVisible();
 *     sa.check(usuario)  .hasValue("admin");
 *     sa.check(botonExit).isEnabled();
 * });
 * // → Si 2 de las 4 verificaciones fallan, el test reporta AMBOS problemas.
 * }</pre>
 *
 * <h3>Uso directo (menos habitual):</h3>
 * <pre>{@code
 * SoftAssertions sa = new SoftAssertions(driver, waits);
 * sa.check(titulo).hasText("Dashboard");
 * sa.check(menu).isVisible();
 * sa.assertAll(); // lanza si hay fallos
 * }</pre>
 */
public class SoftAssertions {

    private final WebDriver    driver;
    private final Waits        waits;
    private final List<String> failures = new ArrayList<>();

    /**
     * Constructor invocado por {@link com.selfhealing.framework.Web}.
     * Los usuarios normalmente no instancian esta clase directamente.
     *
     * @param driver sesión activa de WebDriver
     * @param waits  operaciones de espera compartidas
     */
    public SoftAssertions(WebDriver driver, Waits waits) {
        this.driver = driver;
        this.waits  = waits;
    }

    // -------------------------------------------------------------------------
    // Punto de entrada
    // -------------------------------------------------------------------------

    /**
     * Devuelve un {@link WebAssert} en modo soft para el elemento indicado.
     * Los fallos se acumulan en la lista interna en lugar de lanzar excepción.
     *
     * @param element elemento a verificar
     * @return WebAssert en modo soft asociado a este collector
     */
    public WebAssert check(Element element) {
        return new WebAssert(driver, waits, element, failures);
    }

    // -------------------------------------------------------------------------
    // Evaluación
    // -------------------------------------------------------------------------

    /**
     * Indica si se acumuló algún fallo hasta ahora.
     *
     * @return {@code true} si hay al menos un fallo
     */
    public boolean hasFailed() {
        return !failures.isEmpty();
    }

    /**
     * Devuelve los mensajes de fallo acumulados (solo lectura).
     *
     * @return lista inmutable de mensajes de fallo
     */
    public List<String> getFailures() {
        return java.util.Collections.unmodifiableList(failures);
    }

    /**
     * Lanza {@link AssertionError} si hay algún fallo, mostrando todos los mensajes.
     *
     * <p>Llamar siempre al final del bloque de verificaciones (o dejar que
     * {@code web.softAssert(consumer)} lo haga automáticamente).</p>
     *
     * @throws AssertionError con todos los fallos si hay al menos uno
     */
    public void assertAll() {
        if (failures.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Soft assertions: %d fallo(s) encontrados:%n", failures.size()));
        for (int i = 0; i < failures.size(); i++) {
            sb.append(String.format("  %d) %s%n", i + 1, failures.get(i)));
        }
        throw new AssertionError(sb.toString());
    }
}
