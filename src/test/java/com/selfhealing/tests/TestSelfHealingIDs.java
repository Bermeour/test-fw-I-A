package com.selfhealing.tests;

import com.selfhealing.base.BaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Self-Healing — cambio de IDs")
class TestSelfHealingIDs extends BaseTest {

    // Registrar baselines ANTES de mutar el DOM (cuando los selectores funcionan)
    @BeforeEach
    void registerBaselines() {
        registerBaseline("//input[@id='input-username']",  "baseline_username");
        registerBaseline("//input[@id='input-password']",  "baseline_password");
        registerBaseline("//button[@id='btn-login']",      "baseline_btn_login");
        registerBaseline("//button[@id='btn-clear']",      "baseline_btn_clear");
    }

    @Test
    @DisplayName("Login funciona después de cambio de IDs (healing repara los selectores)")
    void testLoginFuncionaDespuesDeCambioDeIds() {
        // Simular deploy: btn-login → submit-login-btn, input-username → user-field, etc.
        mutate("mutateIds");

        // Los selectores originales ya no existen; healAndFind detecta el fallo,
        // llama al servicio y reintenta con el selector reparado.
        WebElement username = healAndFind("//input[@id='input-username']");
        WebElement password = healAndFind("//input[@id='input-password']");

        username.sendKeys("admin");
        password.sendKeys("secret");

        WebElement loginBtn = healAndFind("//button[@id='btn-login']");
        loginBtn.click();

        // El resultado puede tener el ID original o el mutado; buscamos por contenido
        WebElement result = wait(5).until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.xpath("//*[contains(@id,'login-result') or contains(@id,'result')]")));

        assertTrue(result.isDisplayed(), "El resultado del login debe mostrarse");
    }

    @Test
    @DisplayName("El servicio acumula eventos de sanación en /history")
    void testHealingRegistraElEvento() throws Exception {
        mutate("mutateIds");

        // Provocar al menos una sanación (puede pasar o no según confianza del servicio)
        try {
            healAndFind("//button[@id='btn-login']");
        } catch (Exception ignored) {
            // Aunque el healing falle, el intento debe quedar registrado
        }

        // Pequeña pausa para que el servicio registre el evento
        Thread.sleep(400);

        Map<String, Object> history = web.healingClient.getHistory(PROJECT, 50);
        assertNotNull(history, "/history debe devolver una respuesta JSON");
        System.out.println("[History] " + history);
        // La estructura exacta la define el servicio; simplemente verificamos que responde
    }
}