package com.selfhealing.tests;

import com.selfhealing.base.BaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tablas dinámicas")
class TestTablasDinamicas extends BaseTest {

    private static final By ROWS = By.xpath("//tbody[@id='table-body']/tr");

    @Test
    @DisplayName("Agregar fila incrementa el conteo en 1")
    void testAgregarFila() {
        int before = driver.findElements(ROWS).size();

        driver.findElement(By.xpath("//button[@id='btn-add-row']")).click();

        wait(5).until(d -> d.findElements(ROWS).size() > before);

        assertEquals(before + 1, driver.findElements(ROWS).size(),
                "Debe haber exactamente una fila más");
    }

    @Test
    @DisplayName("Agregar 3 filas incrementa el conteo en 3")
    void testAgregarMultiplesFilas() {
        int before = driver.findElements(ROWS).size();

        WebElement addBtn = driver.findElement(By.xpath("//button[@id='btn-add-row']"));
        addBtn.click();
        addBtn.click();
        addBtn.click();

        wait(5).until(d -> d.findElements(ROWS).size() >= before + 3);

        assertEquals(before + 3, driver.findElements(ROWS).size(),
                "Deben haberse agregado exactamente 3 filas");
    }

    @Test
    @DisplayName("Self-Healing resuelve selector de botón roto tras reordenar filas")
    void testSelfHealingBotonTrasReordenarFilas() {
        // Registrar baseline para btn-add-row con su selector original
        registerBaseline("//button[@id='btn-add-row']", "baseline_add_row");

        int before = driver.findElements(ROWS).size();

        // Ordenar tabla (reordena las filas del tbody)
        driver.findElement(By.xpath("//button[@id='btn-sort-rows']")).click();

        // Simular que el "deploy" también cambió el ID del botón de agregar
        // (análogo a mutateIds() pero manual para este escenario específico)
        ((JavascriptExecutor) driver).executeScript(
                "var btn = document.getElementById('btn-add-row');" +
                "if (btn) btn.id = 'add-new-row-btn';");

        // El XPath original ya no encuentra nada → healing interviene
        WebElement addBtn = healAndFind("//button[@id='btn-add-row']");

        addBtn.click();

        wait(5).until(d -> d.findElements(ROWS).size() > before);

        assertEquals(before + 1, driver.findElements(ROWS).size(),
                "El botón reparado debe seguir siendo funcional");
    }
}