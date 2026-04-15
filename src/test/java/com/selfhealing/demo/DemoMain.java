package com.selfhealing.demo;

import com.selfhealing.config.ConfigLoader;
import com.selfhealing.framework.client.HealingClient;
import com.selfhealing.framework.Web;
import com.selfhealing.framework.config.WebConfig;
import com.selfhealing.framework.element.Element;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;

import java.util.Map;

/**
 * Demo ejecutable que ejerce los dos grandes módulos del proyecto:
 *
 *   Módulo A — Servicio (HealingClient)
 *     · /health   → verificar que el microservicio está operativo
 *     · /metrics  → consultar estadísticas de sanaciones del proyecto
 *     · /history  → últimas N operaciones de healing
 *
 *   Módulo B — Framework (Web)
 *     · web.actions   → API fluida encadenable: type().border(), click().blink(), etc.
 *     · web.waits     → untilVisible, untilPageReady
 *     · web.healing   → register (baseline) + heal (sanación de selectores rotos)
 *
 * ─────────────────────────────────────────────────────────────────
 * Cómo ejecutar:
 *
 *   mvn test-compile exec:java \
 *       -Dexec.mainClass="com.selfhealing.demo.DemoMain" \
 *       -Dexec.classpathScope=test
 *
 * Requisitos previos:
 *   · Self-Healing Service corriendo en http://localhost:8765
 *   · Demo app corriendo en http://localhost:9000
 * ─────────────────────────────────────────────────────────────────
 */
public class DemoMain {

    private static final String PROJECT = ConfigLoader.get("app.project");

    public static void main(String[] args) throws Exception {

        banner("Self-Healing Framework — Demo principal");

        // ──────────────────────────────────────────────────────────
        // MÓDULO A — Servicio directo (HealingClient)
        // ──────────────────────────────────────────────────────────

        paso("A1", "Verificando salud del microservicio de healing");
        HealingClient client = new HealingClient(ConfigLoader.get("healing.url"));
        try {
            Map<String, Object> health = client.getHealth();
            log("  /health → %s", health);
        } catch (Exception e) {
            log("  WARN: servicio no alcanzable (%s) — continuando de todos modos", e.getMessage());
        }

        // ──────────────────────────────────────────────────────────
        // MÓDULO B — Framework Web
        // ──────────────────────────────────────────────────────────

        WebConfig config = ConfigLoader.webConfig();
        paso("B1", "Inicializando Web framework → " + config.getUrl());
        Web web = Web.init(config);

        try {

            // ── Definición de elementos de la página de login ──────
            Element username  = Element.id("input-username").label("Campo usuario");
            Element password  = Element.id("input-password").label("Campo contraseña");
            Element loginBtn  = Element.id("btn-login").label("Botón login");
            Element clearBtn  = Element.id("btn-clear").label("Botón limpiar");
            Element resultado = Element.id("login-result").label("Resultado login");

            // ──────────────────────────────────────────────────────
            // B2 — Registrar baselines (mientras los selectores funcionan)
            // ──────────────────────────────────────────────────────
            paso("B2", "Registrando baselines via web.healing.register()");
            web.healing.register(username);
            web.healing.register(password);
            web.healing.register(loginBtn,  "demo_btn_login");
            web.healing.register(clearBtn);

            // ──────────────────────────────────────────────────────
            // B3 — API fluida encadenable (type / click / visual)
            // ──────────────────────────────────────────────────────
            paso("B3", "Ejerciendo API fluida encadenable");

            // Escribir credenciales y resaltar el campo tras escribir
            log("  type → border (resaltar campo tras escribir)");
            web.actions.type(username, "admin").border();
            web.actions.type(password, "secret").highlightSuccess();

            // Scroll + highlight antes del click
            log("  scroll → highlight → click (sobre el mismo botón)");
            web.actions.click(loginBtn)
                       .scroll()
                       .blink(2);

            // Esperar resultado y leerlo al final de la cadena
            web.waits.untilVisible(resultado);
            log("  click → scroll → read (resultado del login)");
            String textoResultado = web.actions.click(resultado)
                                               .scroll()
                                               .read();
            log("  Resultado leído: \"%s\"", textoResultado);

            // Limpiar el formulario con highlight de error en el botón
            log("  click → highlightInfo (botón limpiar)");
            web.actions.click(clearBtn).highlightInfo();
            web.waits.untilPageReady();

            // ──────────────────────────────────────────────────────
            // B4 — Self-healing: simular selector roto y sanar
            // ──────────────────────────────────────────────────────
            paso("B4", "Simulando selector roto y aplicando web.healing.heal()");

            log("  Mutando IDs en el DOM via JS...");
            JavascriptExecutor js = (JavascriptExecutor) web.driver;
            js.executeScript(
                "document.getElementById('input-username').id = 'input-username-v2';" +
                "document.getElementById('btn-login').id      = 'btn-login-v2';"
            );
            log("  DOM mutado: input-username → v2 | btn-login → v2");

            // Sanar el campo usuario y escribir en él (selector original roto)
            log("  [healing.heal] Sanando campo usuario...");
            try {
                WebElement healed = web.healing.heal(username, "demo_campo_usuario");
                log("  Elemento sanado: <%s> id='%s'", healed.getTagName(), healed.getAttribute("id"));

                // Tras sanar podemos volver a usar la API fluida con el Element original
                web.actions.type(username, "admin_healed").border();
            } catch (Exception e) {
                log("  INFO: Healing no pudo resolver '%s': %s", username.getDisplayLabel(), e.getMessage());
            }

            // Sanar y hacer click en el botón (selector roto)
            log("  [healing.healAndClick] Sanando y haciendo click en botón login...");
            try {
                web.healing.healAndClick(loginBtn);
                log("  Click ejecutado sobre el elemento sanado.");
            } catch (Exception e) {
                log("  INFO: Healing no pudo resolver '%s': %s", loginBtn.getDisplayLabel(), e.getMessage());
            }

            // ──────────────────────────────────────────────────────
            // A2 — Métricas y historial del servicio tras las operaciones
            // ──────────────────────────────────────────────────────
            paso("A2", "Consultando métricas y historial del servicio");
            try {
                Map<String, Object> metrics = client.getMetrics(PROJECT);
                log("  /metrics/%s → %s", PROJECT, metrics);

                Map<String, Object> history = client.getHistory(PROJECT, 5);
                log("  /history/%s (últimas 5) → %s", PROJECT, history);
            } catch (Exception e) {
                log("  WARN: No se pudo consultar métricas: %s", e.getMessage());
            }

        } finally {
            paso("FIN", "Cerrando sesión y liberando recursos");
            web.close();
            try { client.close(); } catch (Exception ignored) {}
            log("");
            banner("Demo finalizado");
        }
    }

    // ── Helpers de salida ──────────────────────────────────────────

    private static void banner(String titulo) {
        String linea = "=".repeat(55);
        System.out.println("\n" + linea);
        System.out.println("  " + titulo);
        System.out.println(linea + "\n");
    }

    private static void paso(String num, String descripcion) {
        System.out.printf("%n[Paso %s] %s%n", num, descripcion);
    }

    private static void log(String msg, Object... args) {
        System.out.println(args.length == 0 ? msg : String.format(msg, args));
    }
}