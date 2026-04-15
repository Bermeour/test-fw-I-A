package com.selfhealing.framework.table;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * Representa una fila de una tabla HTML — devuelto por {@link Table#rowWhere}.
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * TableRow fila = web.table(Element.id("tabla-usuarios"))
 *                    .rowWhere("Email", "admin@empresa.com");
 *
 * String nombre = fila.cell("Nombre");
 * String estado = fila.cell("Estado");
 * fila.click();
 * }</pre>
 */
public class TableRow {

    private final WebElement   rowElement;
    private final List<String> headers;

    /**
     * Constructor de uso interno — creado por {@link Table}.
     *
     * @param rowElement elemento {@code <tr>} de esta fila
     * @param headers    nombres de las columnas en orden (del encabezado de la tabla)
     */
    TableRow(WebElement rowElement, List<String> headers) {
        this.rowElement = rowElement;
        this.headers    = headers;
    }

    // -------------------------------------------------------------------------
    // Acceso a celdas
    // -------------------------------------------------------------------------

    /**
     * Devuelve el texto de la celda en la columna indicada por nombre.
     *
     * @param columnHeader nombre exacto de la columna (case-sensitive)
     * @return texto visible de la celda
     * @throws IllegalArgumentException si la columna no existe
     */
    public String cell(String columnHeader) {
        int index = headers.indexOf(columnHeader);
        if (index < 0) {
            throw new IllegalArgumentException(
                String.format("Columna '%s' no encontrada. Columnas disponibles: %s",
                    columnHeader, headers));
        }
        return cell(index);
    }

    /**
     * Devuelve el texto de la celda en la posición indicada (índice 0-based).
     *
     * @param colIndex índice de la columna (0 = primera columna)
     * @return texto visible de la celda
     */
    public String cell(int colIndex) {
        List<WebElement> cells = rowElement.findElements(By.tagName("td"));
        if (cells.isEmpty()) {
            cells = rowElement.findElements(By.tagName("th"));
        }
        if (colIndex < 0 || colIndex >= cells.size()) {
            throw new IndexOutOfBoundsException(
                String.format("Índice de columna %d fuera de rango (la fila tiene %d celdas)",
                    colIndex, cells.size()));
        }
        return cells.get(colIndex).getText().trim();
    }

    /**
     * Devuelve el {@link WebElement} de la celda en la columna indicada por nombre.
     * Útil para hacer click, leer atributos o interactuar con controles dentro de la celda.
     *
     * @param columnHeader nombre exacto de la columna
     * @return WebElement de la celda
     */
    public WebElement cellElement(String columnHeader) {
        int index = headers.indexOf(columnHeader);
        if (index < 0) {
            throw new IllegalArgumentException(
                String.format("Columna '%s' no encontrada. Columnas disponibles: %s",
                    columnHeader, headers));
        }
        List<WebElement> cells = rowElement.findElements(By.tagName("td"));
        if (cells.isEmpty()) cells = rowElement.findElements(By.tagName("th"));
        return cells.get(index);
    }

    // -------------------------------------------------------------------------
    // Interacción con la fila
    // -------------------------------------------------------------------------

    /**
     * Hace click en la fila completa.
     * Útil en tablas donde toda la fila es clickable.
     */
    public void click() {
        rowElement.click();
    }

    /**
     * Devuelve el elemento {@code <tr>} subyacente para operaciones avanzadas.
     *
     * @return el WebElement de la fila
     */
    public WebElement getElement() {
        return rowElement;
    }
}
