package com.selfhealing.framework.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un test para que sea reintentado automáticamente cuando falla.
 *
 * <p>Se procesa mediante {@link com.selfhealing.base.RetryExtension}, que debe
 * estar registrada en la clase de test (o en {@code BaseTest}).</p>
 *
 * <p>Entre reintentos el browser se resetea: se cierran el driver y el cliente
 * de healing y se crean nuevos (equivale a ejecutar {@code @AfterEach} +
 * {@code @BeforeEach} de nuevo). Esto garantiza un estado limpio en cada intento.</p>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * // Reintentar hasta 2 veces (3 intentos en total)
 * @Test
 * @RetryOnFailure(times = 2)
 * void testOperacionFlakey() {
 *     // ...
 * }
 *
 * // Con valor por defecto: 1 reintento (2 intentos en total)
 * @Test
 * @RetryOnFailure
 * void testInestable() {
 *     // ...
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RetryOnFailure {

    /**
     * Número de <em>reintentos</em> adicionales después del primer intento.
     * El número total de ejecuciones = {@code times + 1}.
     *
     * <p>Valor por defecto: {@code 1} (2 intentos totales).</p>
     */
    int times() default 1;
}
