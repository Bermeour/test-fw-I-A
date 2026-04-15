package com.selfhealing.tests;

import com.selfhealing.base.BaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Elementos con delay")
class TestElementosConDelay extends BaseTest {

    @Test
    @DisplayName("Elemento aparece tras 2s y es clickable")
    void testElementoApareceYEsClickable() {
        driver.findElement(By.xpath("//button[@id='btn-trigger-slow']")).click();

        // El elemento tarda 2 s; damos margen de 5 s
        WebElement delayedBtn = wait(5).until(
                ExpectedConditions.elementToBeClickable(By.id("btn-delayed-action")));

        assertTrue(delayedBtn.isDisplayed(), "El botón delayed debe estar visible");

        // Verificar que se puede hacer click sin excepción
        assertDoesNotThrow(delayedBtn::click, "El click no debe lanzar excepción");
    }

    @Test
    @DisplayName("Self-Healing recupera elemento dinámico cuyo ID cambió tras aparecer")
    void testSelfHealingElementoDinamicoConIdCambiado() {
        // 1. Hacer aparecer el elemento rápido (0.5 s)
        driver.findElement(By.xpath("//button[@id='btn-trigger-fast']")).click();

        wait(3).until(
                ExpectedConditions.visibilityOfElementLocated(By.id("btn-delayed-action")));

        // 2. Registrar baseline mientras el selector es válido
        registerBaseline("//button[@id='btn-delayed-action']", "baseline_delayed_action");

        // 3. Resetear la página para un estado limpio
        driver.get(APP_URL);

        // 4. Volver a disparar el elemento
        driver.findElement(By.xpath("//button[@id='btn-trigger-fast']")).click();

        wait(3).until(
                ExpectedConditions.visibilityOfElementLocated(By.id("btn-delayed-action")));

        // 5. Simular que tras aparecer el elemento su ID fue cambiado (deploy en caliente)
        ((JavascriptExecutor) driver).executeScript(
                "var el = document.getElementById('btn-delayed-action');" +
                "if (el) el.id = 'delayed-action-btn';");

        // 6. El selector original ya no existe → healing lo recupera
        WebElement healed = healAndFind("//button[@id='btn-delayed-action']");

        assertNotNull(healed, "El elemento debe haberse encontrado tras la sanación");
        assertTrue(healed.isDisplayed(), "El elemento reparado debe estar visible");

        // 7. Verificar que sigue siendo funcional
        assertDoesNotThrow(healed::click, "El elemento reparado debe ser clickable");
    }
}