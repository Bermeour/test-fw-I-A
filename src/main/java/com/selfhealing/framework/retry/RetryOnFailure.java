package com.selfhealing.framework.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un test para que sea reintentado automáticamente cuando falla.
 *
 * <p>Anotación de marcado puro — no depende de ningún runner. El runner del proyecto
 * consumidor es responsable de detectarla y aplicar la lógica de reintento.</p>
 *
 * <h3>Ejemplo de uso con JUnit 5:</h3>
 * <pre>{@code
 * // El proyecto consumidor registra su propia extensión de retry:
 * // @ExtendWith(MiRetryExtension.class)
 *
 * @Test
 * @RetryOnFailure(times = 2)   // 3 intentos en total
 * void testOperacionFlakey() { ... }
 *
 * @Test
 * @RetryOnFailure               // 2 intentos en total (valor por defecto)
 * void testInestable() { ... }
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
