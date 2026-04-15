package com.selfhealing.base;

import com.selfhealing.framework.WebContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Extension de JUnit 5 que captura un screenshot automáticamente cuando un test falla.
 *
 * <p>El screenshot se guarda en {@code screenshots/failures/} con el nombre del test
 * y un timestamp. No hace nada si el test pasa.</p>
 *
 * <h3>Estrategia de acceso al driver (segura en paralelo):</h3>
 * <ol>
 *   <li>Primero intenta {@link WebContext#driver()} — acceso ThreadLocal directo,
 *       garantiza el driver correcto en ejecución paralela.</li>
 *   <li>Como fallback usa reflection sobre el test instance — compatibilidad
 *       con clases que no usen WebContext.</li>
 * </ol>
 *
 * <h3>Registro:</h3>
 * <p>Ya está registrada en {@link BaseTest} via {@code @ExtendWith}. No necesitas
 * añadirla manualmente en tus clases de test.</p>
 */
public class ScreenshotExtension implements TestWatcher {

    private static final String SCREENSHOT_DIR = "screenshots/failures";
    private static final DateTimeFormatter TIMESTAMP_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        String testClass  = context.getRequiredTestClass().getSimpleName();
        String testMethod = context.getRequiredTestMethod().getName();

        System.out.printf("%n[Screenshot] Test fallido: %s#%s%n", testClass, testMethod);

        WebDriver driver = resolveDriver(context);
        if (driver == null) {
            System.out.println("[Screenshot] WARN: no se encontró WebDriver para capturar");
            return;
        }
        saveScreenshot(driver, testClass, testMethod);
    }

    @Override public void testAborted(ExtensionContext context, Throwable cause) {}
    @Override public void testSuccessful(ExtensionContext context) {}
    @Override public void testDisabled(ExtensionContext context, Optional<String> reason) {}

    // -------------------------------------------------------------------------
    // Resolución del driver — ThreadLocal primero, reflection como fallback
    // -------------------------------------------------------------------------

    /**
     * Obtiene el {@link WebDriver} para el hilo actual.
     *
     * <p>Prioridad:</p>
     * <ol>
     *   <li>{@link WebContext#driver()} — correcto en ejecución paralela.</li>
     *   <li>Reflection sobre el test instance — fallback para clases legacy.</li>
     * </ol>
     */
    private WebDriver resolveDriver(ExtensionContext context) {
        // 1. ThreadLocal — el camino rápido y seguro en paralelo
        WebDriver driver = WebContext.driver();
        if (driver != null) return driver;

        // 2. Reflection — fallback para clases que no usen BaseTest/WebContext
        return context.getTestInstance()
            .map(this::extractDriverByReflection)
            .orElse(null);
    }

    private WebDriver extractDriverByReflection(Object instance) {
        Class<?> clazz = instance.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField("driver");
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value instanceof WebDriver) return (WebDriver) value;
            } catch (NoSuchFieldException ignored) {
                // continuar en superclase
            } catch (IllegalAccessException e) {
                System.out.printf("[Screenshot] WARN: no se pudo acceder al campo 'driver': %s%n",
                    e.getMessage());
                return null;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Guardar el screenshot en disco
    // -------------------------------------------------------------------------

    private void saveScreenshot(WebDriver driver, String testClass, String testMethod) {
        try {
            // El timestamp incluye milisegundos para evitar colisiones en paralelo
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
            String fileName  = testClass + "_" + testMethod + "_" + timestamp + ".png";
            String filePath  = SCREENSHOT_DIR + "/" + fileName;

            Files.createDirectories(Paths.get(SCREENSHOT_DIR));
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(Paths.get(filePath), bytes);

            System.out.printf("[Screenshot] Guardado en: %s%n", filePath);

        } catch (IOException e) {
            System.out.printf("[Screenshot] WARN: no se pudo guardar el screenshot: %s%n",
                e.getMessage());
        } catch (Exception e) {
            System.out.printf("[Screenshot] WARN: error al tomar screenshot: %s%n",
                e.getMessage());
        }
    }
}
