package com.selfhealing.framework.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Filtros de contexto opcionales para afinar la búsqueda del motor DOM
 * durante el self-healing. Todos los filtros son opcionales e independientes.
 *
 * <h3>Cuándo usar cada filtro:</h3>
 * <ul>
 *   <li><b>anchors</b> — cuando la página tiene varios elementos similares y
 *       quieres que el motor priorice el que esté cerca de un elemento de referencia
 *       conocido (ej: el botón "Aceptar" que está junto al label "Monto").</li>
 *   <li><b>inContainer</b> — cuando el elemento pertenece a una sección concreta
 *       de la página (ej: solo buscar dentro del panel de "Datos personales").</li>
 *   <li><b>inForm</b> — equivalente a inContainer pero semántico: limita la búsqueda
 *       a un formulario específico cuando hay varios formularios en la página.</li>
 *   <li><b>excludeId</b> — cuando hay IDs que el motor no debe considerar aunque
 *       coincidan (ej: el botón del header que tiene texto igual al del body).</li>
 * </ul>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * HealContext ctx = HealContext.create()
 *     .anchor("id",   "campo-monto",    40)   // buscar cerca del campo "monto"
 *     .anchor("text", "Datos de pago",  25)   // y cerca del título "Datos de pago"
 *     .inContainer("panel-pago")              // solo dentro del panel
 *     .excludeId("btn-cancelar-header");      // ignorar este ID aunque coincida
 *
 * web.healing.heal(Element.id("btn-pagar"), "test_pago", ctx);
 * }</pre>
 */
public class HealContext {

    @JsonProperty("anchors")
    private final List<Anchor> anchors = new ArrayList<>();

    @JsonProperty("exclude_ids")
    private final List<String> excludeIds = new ArrayList<>();

    @JsonProperty("container_id")
    private String containerId;

    @JsonProperty("container_class")
    private String containerClass;

    @JsonProperty("form_id")
    private String formId;

    private HealContext() {}

    /** Crea un contexto vacío listo para configurar. */
    public static HealContext create() {
        return new HealContext();
    }

    // ── Anchors ──────────────────────────────────────────────────────────────

    /**
     * Añade un elemento de referencia (anchor) para que el motor priorice
     * candidatos que estén cerca de él en el DOM.
     *
     * <p>El servicio sumará un bonus de proximidad (hasta +30 pts) a los
     * candidatos que se encuentren a ≤3 nodos de distancia del anchor.</p>
     *
     * @param type   tipo del identificador del anchor: {@code "id"}, {@code "text"},
     *               {@code "name"}, {@code "class"}, {@code "aria_label"}
     * @param value  valor que identifica el elemento de referencia
     * @param weight importancia relativa de este anchor (1–100).
     *               Recomendado: usar 40 para el anchor principal, 20–30 para secundarios.
     */
    public HealContext anchor(String type, String value, int weight) {
        anchors.add(new Anchor(type, value, weight));
        return this;
    }

    /** Atajo: anchor por id con peso 40 (anchor principal típico). */
    public HealContext anchorById(String id) {
        return anchor("id", id, 40);
    }

    /** Atajo: anchor por texto visible con peso 30. */
    public HealContext anchorByText(String text) {
        return anchor("text", text, 30);
    }

    /** Atajo: anchor por name con peso 35. */
    public HealContext anchorByName(String name) {
        return anchor("name", name, 35);
    }

    /** Atajo: anchor por aria-label con peso 25. */
    public HealContext anchorByAriaLabel(String ariaLabel) {
        return anchor("aria_label", ariaLabel, 25);
    }

    // ── Container ────────────────────────────────────────────────────────────

    /**
     * Restringe la búsqueda a los elementos que estén dentro del contenedor
     * con el id indicado.
     *
     * <p>Útil cuando la misma aplicación repite el mismo tipo de elemento
     * en varias secciones (header, sidebar, main) y solo nos interesa uno.</p>
     *
     * @param containerId id del elemento contenedor (sin el prefijo {@code #})
     */
    public HealContext inContainer(String containerId) {
        this.containerId = containerId;
        return this;
    }

    /**
     * Restringe la búsqueda a los elementos que estén dentro de un contenedor
     * que tenga al menos una de las clases CSS indicadas (lógica OR).
     *
     * <p>Si se pasan varias clases, el servicio acepta el contenedor si tiene
     * cualquiera de ellas. Ej: {@code "form-section,main-content"}</p>
     *
     * @param cssClass una o varias clases CSS separadas por coma
     */
    public HealContext inContainerClass(String cssClass) {
        this.containerClass = cssClass;
        return this;
    }

    /**
     * Restringe la búsqueda a los elementos que estén dentro del formulario
     * con el id indicado.
     *
     * @param formId id del elemento {@code <form>}
     */
    public HealContext inForm(String formId) {
        this.formId = formId;
        return this;
    }

    // ── Exclusiones ──────────────────────────────────────────────────────────

    /**
     * Excluye uno o más ids de la búsqueda.
     *
     * <p>El motor DOM ignorará cualquier candidato cuyos id aparezca en esta lista,
     * aunque su score sea el más alto. Útil para evitar falsos positivos conocidos.</p>
     *
     * @param ids ids a excluir (sin el prefijo {@code #})
     */
    public HealContext excludeId(String... ids) {
        excludeIds.addAll(Arrays.asList(ids));
        return this;
    }

    // ── Getters para serialización ────────────────────────────────────────────

    public List<Anchor>  getAnchors()        { return Collections.unmodifiableList(anchors); }
    public List<String>  getExcludeIds()     { return Collections.unmodifiableList(excludeIds); }
    public String        getContainerId()    { return containerId; }
    public String        getContainerClass() { return containerClass; }
    public String        getFormId()         { return formId; }

    public boolean isEmpty() {
        return anchors.isEmpty()
                && excludeIds.isEmpty()
                && containerId == null
                && containerClass == null
                && formId == null;
    }

    // ── Anchor (clase interna) ────────────────────────────────────────────────

    /**
     * Elemento de referencia para el motor de proximidad DOM.
     */
    public static class Anchor {

        private final String type;
        private final String value;
        private final int    weight;

        public Anchor(String type, String value, int weight) {
            this.type   = type;
            this.value  = value;
            this.weight = Math.max(1, Math.min(100, weight));
        }

        public String getType()   { return type; }
        public String getValue()  { return value; }
        public int    getWeight() { return weight; }
    }
}
