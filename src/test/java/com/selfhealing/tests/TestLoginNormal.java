package com.selfhealing.tests;

import com.selfhealing.base.BaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Login — selectores originales")
class TestLoginNormal extends BaseTest {

    @Test
    @DisplayName("Login exitoso con credenciales válidas")
    void testLoginExitoso() {
        driver.findElement(By.xpath("//input[@id='input-username']")).sendKeys("admin");
        driver.findElement(By.xpath("//input[@id='input-password']")).sendKeys("secret");
        driver.findElement(By.xpath("//button[@id='btn-login']")).click();

        WebElement result = wait(5).until(
                ExpectedConditions.visibilityOfElementLocated(By.id("login-result")));

        assertTrue(result.isDisplayed(), "El resultado del login debe ser visible");
        assertFalse(result.getAttribute("class").contains("hidden"),
                "El resultado no debe tener la clase 'hidden'");
    }

    @Test
    @DisplayName("Login sin datos muestra mensaje de error")
    void testLoginSinDatosMuestraError() {
        driver.findElement(By.xpath("//button[@id='btn-login']")).click();

        WebElement result = wait(5).until(
                ExpectedConditions.visibilityOfElementLocated(By.id("login-result")));

        assertTrue(result.isDisplayed(), "Debe aparecer un mensaje de error o advertencia");
        assertFalse(result.getText().isEmpty(), "El mensaje no debe estar vacío");
    }

    @Test
    @DisplayName("Botón limpiar vacía ambos campos")
    void testClearLimpiaLosCampos() {
        WebElement username = driver.findElement(By.xpath("//input[@id='input-username']"));
        WebElement password = driver.findElement(By.xpath("//input[@id='input-password']"));

        username.sendKeys("admin");
        password.sendKeys("secret");

        assertEquals("admin",  username.getAttribute("value"), "Precondición: username tiene valor");
        assertEquals("secret", password.getAttribute("value"), "Precondición: password tiene valor");

        driver.findElement(By.xpath("//button[@id='btn-clear']")).click();

        assertEquals("", username.getAttribute("value"), "Username debe quedar vacío tras limpiar");
        assertEquals("", password.getAttribute("value"), "Password debe quedar vacío tras limpiar");
    }
}
