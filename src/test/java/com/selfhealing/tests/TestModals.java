package com.selfhealing.tests;

import com.selfhealing.base.BaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Modales")
class TestModals extends BaseTest {

    @Test
    @DisplayName("Abrir modal de confirmación hace visible el overlay")
    void testAbreModalDeConfirmacion() {
        driver.findElement(By.xpath("//button[@id='btn-open-confirm']")).click();

        WebElement overlay = wait(5).until(
                ExpectedConditions.visibilityOfElementLocated(By.id("modal-overlay")));

        assertTrue(overlay.isDisplayed(), "El overlay del modal debe estar visible");
        assertFalse(overlay.getAttribute("class").contains("hidden"),
                "El overlay no debe tener clase 'hidden'");
    }

    @Test
    @DisplayName("Cerrar modal con btn-modal-close oculta el overlay")
    void testCierraModalConBoton() {
        // Abrir
        driver.findElement(By.xpath("//button[@id='btn-open-confirm']")).click();
        wait(5).until(ExpectedConditions.visibilityOfElementLocated(By.id("modal-overlay")));

        // Cerrar
        driver.findElement(By.xpath("//button[@id='btn-modal-close']")).click();

        wait(5).until(ExpectedConditions.invisibilityOfElementLocated(By.id("modal-overlay")));

        WebElement overlay = driver.findElement(By.id("modal-overlay"));
        assertTrue(
                overlay.getAttribute("class").contains("hidden") || !overlay.isDisplayed(),
                "El overlay debe ocultarse tras cerrar el modal");
    }

    @Test
    @DisplayName("Self-Healing abre modal tras cambio de clases CSS")
    void testSelfHealingAbreModalTrasCambioDeClases() {
        // Registrar baseline mientras el selector funciona
        registerBaseline("//button[@id='btn-open-confirm']", "baseline_modal_confirm");

        // Cambiar clases CSS (mutateClasses puede afectar selectores basados en clase)
        mutate("mutateClasses");

        // Adicionalmente eliminamos el id del botón para forzar que el XPath por id falle
        // y así demostrar el healing; el servicio lo encuentra por texto/contexto
        ((JavascriptExecutor) driver).executeScript(
                "var btn = document.getElementById('btn-open-confirm');" +
                "if (btn) btn.removeAttribute('id');");

        // healAndFind detecta el fallo y pide reparación al servicio
        WebElement confirmBtn = healAndFind("//button[@id='btn-open-confirm']");
        confirmBtn.click();

        WebElement overlay = wait(5).until(
                ExpectedConditions.visibilityOfElementLocated(By.id("modal-overlay")));

        assertTrue(overlay.isDisplayed(), "El modal debe abrirse incluso tras mutación de clases/IDs");
    }
}