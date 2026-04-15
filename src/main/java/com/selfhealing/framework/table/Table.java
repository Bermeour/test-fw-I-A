package com.selfhealing.framework.table;

import com.selfhealing.framework.element.Element;
import com.selfhealing.framework.waits.Waits;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper para interactuar con tablas HTML ({@code <table>}).
 *
 * <p>Gestiona automáticamente tablas con o sin {@code <thead>} / {@code <tbody>}.
 * Los índices de filas y columnas son <strong>0-based</strong>.</p>
 *
 * <h3>Ejemplo de uso:</h3>
 * <pre>{@code
 * Table tabla = web.table(Element.id("tabla-usuarios"));
 *
 * // Leer una celda por posición (fila 0, columna 1)
 * String nombre = tabla.cell(0, 1);
 *
 * // Leer una celda por nombre de columna
 * String email = tabla.cell(0, "Email");
 *
 * // Obtener todos los valores de una columna
 * List<String> estados = tabla.columnValues("Estado");
 *
 * // Encontrar y clickar la fila donde Estado = "Activo"
 * tabla.rowWhere("Estado", "Activo").click();
 *
 * // Verificar que existe una fila con ese valor
 * assertTrue(tabla.containsRow("Email", "admin@empresa.com"));
 *
 * // Contar filas (sin contar el encabezado)
 * assertEquals(5, tabla.rowCount());
 * }</pre>
 */
public class Table {

    private final WebDriver driver;
    private final Waits     waits;
    private final Element   element;

    /**
     * Constructor de uso interno — creado por {@link com.selfhealing.framework.Web#table}.
     *
     * @param driver  sesión activa de WebDriver
     * @param waits   operaciones de espera compartidas
     * @param element localizador del elemento {@code <table>}
     */
    public Table(WebDriver driver, Waits waits, Element element) {
        this.driver  = driver;
        this.waits   = waits;
        this.element = element;
    }

    // -------------------------------------------------------------------------
    // Dimensiones
    // -------------------------------------------------------------------------

    /**
     * Número de filas de datos (sin contar el encabezado).
     *
     * @return número de {@code <tr>} en el cuerpo de la tabla
     */
    public int rowCount() {
        return getBodyRows().size();
    }

    /**
     * Número de columnas (basado en las celdas del encabezado).
     *
     * @return número de columnas
     */
    public int columnCount() {
        return getHeaders().size();
    }

    /**
     * Devuelve los nombres de las columnas tal como aparecen en el encabezado.
     *
     * @return lista inmutable de nombres de columna
     */
    public List<String> headers() {
        return Collections.unmodifiableList(getHeaders());
    }

    // -------------------------------------------------------------------------
    // Acceso a celdas por posición
    // -------------------------------------------------------------------------

    /**
     * Texto de la celda en la posición indicada.
     *
     * @param rowIndex índice de la fila (0-based, sin contar encabezado)
     * @param colIndex índice de la columna (0-based)
     * @return texto visible de la celda
     */
    public String cell(int rowIndex, int colIndex) {
        List<WebElement> rows = getBodyRows();
        checkRowIndex(rowIndex, rows.size());
        List<WebElement> cells = getCells(rows.get(rowIndex));
        checkColIndex(colIndex, cells.size());
        return cells.get(colIndex).getText().trim();
    }

    /**
     * Texto de la celda en la fila indicada y la columna con el nombre dado.
     *
     * @param rowIndex     índice de la fila (0-based, sin contar encabezado)
     * @param columnHeader nombre exacto de la columna
     * @return texto visible de la celda
     */
    public String cell(int rowIndex, String columnHeader) {
        return cell(rowIndex, columnIndex(columnHeader));
    }

    // -------------------------------------------------------------------------
    // Acceso a filas y columnas completas
    // -------------------------------------------------------------------------

    /**
     * Todos los textos de una fila como lista.
     *
     * @param rowIndex índice de la fila (0-based)
     * @return lista con el texto de cada celda de la fila
     */
    public List<String> rowValues(int rowIndex) {
        List<WebElement> rows = getBodyRows();
        checkRowIndex(rowIndex, rows.size());
        return getCells(rows.get(rowIndex)).stream()
            .map(el -> el.getText().trim())
            .collect(Collectors.toList());
    }

    /**
     * Todos los textos de una columna como lista.
     *
     * @param columnHeader nombre exacto de la columna
     * @return lista con el texto de cada celda de esa columna (una por fila)
     */
    public List<String> columnValues(String columnHeader) {
        int colIdx = columnIndex(columnHeader);
        List<WebElement> rows = getBodyRows();
        List<String> values = new ArrayList<>();
        for (WebElement row : rows) {
            List<WebElement> cells = getCells(row);
            if (colIdx < cells.size()) {
                values.add(cells.get(colIdx).getText().trim());
            }
        }
        return values;
    }

    // -------------------------------------------------------------------------
    // Búsqueda de filas
    // -------------------------------------------------------------------------

    /**
     * Encuentra la primera fila donde la columna indicada tiene el valor exacto dado.
     *
     * <pre>{@code
     * TableRow fila = tabla.rowWhere("Estado", "Activo");
     * fila.click();
     * String email = fila.cell("Email");
     * }</pre>
     *
     * @param columnHeader nombre de la columna a evaluar
     * @param value        valor exacto a buscar (se compara con trim)
     * @return la primera {@link TableRow} que cumple la condición
     * @throws AssertionError si no se encontró ninguna fila con ese valor
     */
    public TableRow rowWhere(String columnHeader, String value) {
        int colIdx = columnIndex(columnHeader);
        List<String> headers = getHeaders();
        for (WebElement row : getBodyRows()) {
            List<WebElement> cells = getCells(row);
            if (colIdx < cells.size() && cells.get(colIdx).getText().trim().equals(value)) {
                return new TableRow(row, headers);
            }
        }
        throw new AssertionError(
            String.format("No se encontró ninguna fila donde '%s' = '%s'", columnHeader, value));
    }

    /**
     * Devuelve todas las filas donde la columna indicada tiene el valor exacto dado.
     *
     * @param columnHeader nombre de la columna a evaluar
     * @param value        valor exacto a buscar
     * @return lista de filas que cumplen la condición (puede estar vacía)
     */
    public List<TableRow> rowsWhere(String columnHeader, String value) {
        int colIdx = columnIndex(columnHeader);
        List<String> headers = getHeaders();
        List<TableRow> result = new ArrayList<>();
        for (WebElement row : getBodyRows()) {
            List<WebElement> cells = getCells(row);
            if (colIdx < cells.size() && cells.get(colIdx).getText().trim().equals(value)) {
                result.add(new TableRow(row, headers));
            }
        }
        return result;
    }

    /**
     * Indica si existe al menos una fila donde la columna tiene el valor dado.
     *
     * @param columnHeader nombre de la columna
     * @param value        valor a buscar
     * @return {@code true} si se encontró al menos una fila
     */
    public boolean containsRow(String columnHeader, String value) {
        int colIdx = columnIndex(columnHeader);
        for (WebElement row : getBodyRows()) {
            List<WebElement> cells = getCells(row);
            if (colIdx < cells.size() && cells.get(colIdx).getText().trim().equals(value)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Acceso directo a filas como TableRow
    // -------------------------------------------------------------------------

    /**
     * Devuelve la fila en el índice indicado como {@link TableRow}.
     *
     * @param rowIndex índice de la fila (0-based)
     * @return la {@link TableRow} en esa posición
     */
    public TableRow row(int rowIndex) {
        List<WebElement> rows = getBodyRows();
        checkRowIndex(rowIndex, rows.size());
        return new TableRow(rows.get(rowIndex), getHeaders());
    }

    /**
     * Devuelve todas las filas de la tabla como lista de {@link TableRow}.
     *
     * @return lista de todas las filas (puede estar vacía si la tabla no tiene datos)
     */
    public List<TableRow> rows() {
        List<String> headers = getHeaders();
        return getBodyRows().stream()
            .map(row -> new TableRow(row, headers))
            .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Helpers internos
    // -------------------------------------------------------------------------

    /**
     * Extrae los nombres de columna del encabezado de la tabla.
     * Busca en {@code <thead>} primero; si no existe, usa la primera fila.
     */
    private List<String> getHeaders() {
        WebElement table = waits.untilVisible(element);

        List<WebElement> headerCells = table.findElements(
            By.cssSelector("thead th, thead td"));

        if (headerCells.isEmpty()) {
            // Sin <thead>: usar la primera <tr> como encabezado
            List<WebElement> firstRow = table.findElements(By.tagName("tr"));
            if (!firstRow.isEmpty()) {
                headerCells = getCells(firstRow.get(0));
            }
        }

        List<String> headers = new ArrayList<>();
        for (WebElement cell : headerCells) {
            headers.add(cell.getText().trim());
        }
        return headers;
    }

    /**
     * Devuelve las filas de datos (sin el encabezado).
     * Busca en {@code <tbody>} primero; si no existe, usa todas las filas excepto la primera.
     */
    private List<WebElement> getBodyRows() {
        WebElement table = waits.untilVisible(element);

        List<WebElement> rows = table.findElements(By.cssSelector("tbody tr"));
        if (!rows.isEmpty()) return rows;

        // Sin <tbody>: usar todas las <tr> excepto la primera (que es el encabezado)
        List<WebElement> allRows = table.findElements(By.tagName("tr"));
        if (allRows.size() <= 1) return Collections.emptyList();
        return allRows.subList(1, allRows.size());
    }

    /** Devuelve las celdas (td o th) de una fila. */
    private List<WebElement> getCells(WebElement row) {
        List<WebElement> cells = row.findElements(By.tagName("td"));
        if (cells.isEmpty()) cells = row.findElements(By.tagName("th"));
        return cells;
    }

    /** Resuelve el índice numérico de una columna por su nombre. */
    private int columnIndex(String columnHeader) {
        List<String> headers = getHeaders();
        int idx = headers.indexOf(columnHeader);
        if (idx < 0) {
            throw new IllegalArgumentException(
                String.format("Columna '%s' no encontrada. Columnas disponibles: %s",
                    columnHeader, headers));
        }
        return idx;
    }

    private void checkRowIndex(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(
                String.format("Índice de fila %d fuera de rango (la tabla tiene %d filas de datos)",
                    index, size));
        }
    }

    private void checkColIndex(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(
                String.format("Índice de columna %d fuera de rango (la fila tiene %d celdas)",
                    index, size));
        }
    }
}
