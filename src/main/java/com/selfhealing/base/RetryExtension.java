package com.selfhealing.base;

import com.selfhealing.framework.retry.RetryOnFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extension de JUnit 5 que reintenta tests marcados con {@link RetryOnFailure}.
 *
 * <p>Entre reintentos resetea el estado del browser invocando los métodos
 * {@code @AfterEach} (teardown) y {@code @BeforeEach} (setup) de la clase de test.
 * Esto garantiza un browser limpio en cada intento.</p>
 *
 * <h3>Registro:</h3>
 * <p>Ya está registrada en {@link BaseTest} via {@code @ExtendWith}. No necesitas
 * añadirla manualmente en tus clases de test.</p>
 *
 * <h3>Comportamiento:</h3>
 * <ul>
 *   <li>1er intento: ejecución normal a través de JUnit.</li>
 *   <li>Siguientes intentos: teardown → setup → re-ejecución del método.</li>
 *   <li>Si todos los intentos fallan: se relanza el Throwable del último intento.</li>
 *   <li>Si el test pasa en cualquier intento: se considera exitoso.</li>
 * </ul>
 */
public class RetryExtension implements InvocationInterceptor {

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                     ReflectiveInvocationContext<Method> invocationContext,
                                     ExtensionContext extensionContext) throws Throwable {

        RetryOnFailure retry = invocationContext.getExecutable()
            .getAnnotation(RetryOnFailure.class);

        // Sin anotación → ejecución normal
        if (retry == null) {
            invocation.proceed();
            return;
        }

        int maxAttempts = retry.times() + 1;
        Throwable lastFailure = null;
        Object testInstance = extensionContext.getRequiredTestInstance();
        Method testMethod   = invocationContext.getExecutable();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (attempt == 1) {
                    // Primer intento via el mecanismo estándar de JUnit
                    invocation.proceed();
                } else {
                    // Reintentos: resetear el browser y re-ejecutar el método
                    System.out.printf("%n[RetryExtension] ══ Reintento %d/%d — %s ══%n",
                        attempt, maxAttempts, testMethod.getName());

                    resetBrowserState(testInstance);
                    testMethod.invoke(testInstance);
                }

                // Llegamos aquí → el intento fue exitoso
                if (attempt > 1) {
                    System.out.printf("[RetryExtension] Test superado en el intento %d/%d — %s%n",
                        attempt, maxAttempts, testMethod.getName());
                }
                return;

            } catch (InvocationTargetException e) {
                lastFailure = e.getCause() != null ? e.getCause() : e;
                System.out.printf("[RetryExtension] Intento %d/%d fallido: %s%n",
                    attempt, maxAttempts, lastFailure.getMessage());

            } catch (Throwable t) {
                lastFailure = t;
                System.out.printf("[RetryExtension] Intento %d/%d fallido: %s%n",
                    attempt, maxAttempts, t.getMessage());
            }
        }

        System.out.printf("[RetryExtension] Test fallido tras %d intento(s) — %s%n",
            maxAttempts, testMethod.getName());
        throw lastFailure;
    }

    // -------------------------------------------------------------------------
    // Reset del estado del browser entre reintentos
    // -------------------------------------------------------------------------

    /**
     * Simula el ciclo {@code @AfterEach} → {@code @BeforeEach} para cerrar
     * el browser actual y arrancar uno nuevo.
     *
     * <p>Recorre la jerarquía de clases del test buscando métodos anotados con
     * {@code @AfterEach} y {@code @BeforeEach} y los invoca en el orden correcto
     * que JUnit usaría normalmente:</p>
     * <ul>
     *   <li>{@code @AfterEach}: subclase primero, superclase después</li>
     *   <li>{@code @BeforeEach}: superclase primero, subclase después</li>
     * </ul>
     */
    private void resetBrowserState(Object testInstance) {
        // Teardown — subclase primero (orden inverso de la jerarquía)
        List<Method> afterMethods = findAnnotatedMethods(testInstance.getClass(), AfterEach.class);
        Collections.reverse(afterMethods);
        invokeAll(afterMethods, testInstance, "@AfterEach");

        // Setup — superclase primero (orden natural de la jerarquía)
        List<Method> beforeMethods = findAnnotatedMethods(testInstance.getClass(), BeforeEach.class);
        invokeAll(beforeMethods, testInstance, "@BeforeEach");
    }

    /**
     * Recorre la jerarquía de clases desde la hoja hasta {@link Object} buscando
     * métodos anotados con la anotación dada.
     *
     * <p>El orden de retorno es: subclase primero → superclase después.</p>
     */
    private List<Method> findAnnotatedMethods(Class<?> clazz,
                                               Class<? extends Annotation> annotation) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.isAnnotationPresent(annotation)) {
                    m.setAccessible(true);
                    methods.add(m);
                }
            }
            current = current.getSuperclass();
        }
        return methods;
    }

    /** Invoca todos los métodos de la lista, logueando errores pero sin interrumpir. */
    private void invokeAll(List<Method> methods, Object instance, String phase) {
        for (Method m : methods) {
            try {
                m.invoke(instance);
            } catch (InvocationTargetException e) {
                System.out.printf("[RetryExtension] WARN: Error en %s %s.%s: %s%n",
                    phase, m.getDeclaringClass().getSimpleName(), m.getName(),
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            } catch (Exception e) {
                System.out.printf("[RetryExtension] WARN: No se pudo invocar %s %s.%s: %s%n",
                    phase, m.getDeclaringClass().getSimpleName(), m.getName(), e.getMessage());
            }
        }
    }
}
