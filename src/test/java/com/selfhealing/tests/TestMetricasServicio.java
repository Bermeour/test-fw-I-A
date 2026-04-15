package com.selfhealing.tests;

import com.selfhealing.base.BaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Métricas del servicio de healing")
class TestMetricasServicio extends BaseTest {

    @Test
    @DisplayName("/health responde con status: ok")
    void testHealthDelServicio() throws Exception {
        Map<String, Object> health = web.healingClient.getHealth();

        assertNotNull(health, "La respuesta de /health no debe ser nula");
        assertEquals("ok", health.get("status"),
                "El campo 'status' debe ser 'ok'");

        System.out.println("[Health] " + health);
    }

    @Test
    @DisplayName("/metrics acumula sanaciones y contiene estrategia 'DOM'")
    @SuppressWarnings("unchecked")
    void testMetricasAcumulanSanaciones() throws Exception {
        // Provocar al menos una sanación para que by_strategy tenga datos
        registerBaseline("//button[@id='btn-login']", "baseline_login_for_metrics");
        mutate("mutateIds");

        try {
            healAndFind("//button[@id='btn-login']");
        } catch (Exception ignored) {
            // El healing puede no encontrar con suficiente confianza;
            // lo importante es que el intento quede registrado en métricas
        }

        // Pequeña pausa para que el servicio persista la sanación
        Thread.sleep(500);

        Map<String, Object> metrics = web.healingClient.getMetrics(PROJECT);

        assertNotNull(metrics, "La respuesta de /metrics no debe ser nula");
        System.out.println("[Metrics] " + metrics);

        // Verificar que by_strategy existe y contiene la clave "DOM"
        Object byStrategy = metrics.get("by_strategy");
        assertNotNull(byStrategy, "El campo 'by_strategy' debe existir en las métricas");

        if (byStrategy instanceof Map) {
            Map<String, Object> strategyMap = (Map<String, Object>) byStrategy;
            assertTrue(strategyMap.containsKey("DOM"),
                    "La estrategia 'DOM' debe estar presente en by_strategy. Encontrado: " + strategyMap.keySet());
            System.out.println("[Metrics] by_strategy: " + strategyMap);
        }
    }
}