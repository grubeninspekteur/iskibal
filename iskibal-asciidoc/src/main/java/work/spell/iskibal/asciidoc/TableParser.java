package work.spell.iskibal.asciidoc;

import module java.base;

import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.Row;
import org.asciidoctor.ast.Table;

import module iskibal.rule.model;

/// Parses AsciiDoc tables into Iskara model objects.
///
/// Supports parsing of:
/// - `.facts` tables into [Fact] objects
/// - `.globals` tables into [Global] objects
/// - `.outputs` tables into [Output] objects
/// - `.data-table` tables into [DataTable] objects
public class TableParser {

    private final ExpressionParser expressionParser;

    /// Creates a TableParser with the given expression parser.
    ///
    /// @param expressionParser
    ///            the parser for expressions in table cells
    public TableParser(ExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
    }

    /// Parses a .facts table into a list of Fact objects.
    ///
    /// Expected columns: Fact name | Type | Description (optional)
    ///
    /// @param table
    ///            the AsciiDoc table
    /// @return list of parsed facts
    public java.util.List<Fact> parseFacts(Table table) {
        java.util.List<Fact> facts = new ArrayList<>();
        java.util.List<Row> bodyRows = getBodyRows(table);

        for (Row row : bodyRows) {
            java.util.List<Cell> cells = row.getCells();
            if (cells.size() >= 2) {
                String name = getCellText(cells.get(0));
                String type = getCellText(cells.get(1));
                String description = cells.size() > 2 ? getCellText(cells.get(2)) : null;

                if (!name.isBlank() && !type.isBlank()) {
                    facts.add(new Fact.Definition(name.trim(), type.trim(),
                            description != null ? description.trim() : null));
                }
            }
        }
        return facts;
    }

    /// Parses a .globals table into a list of Global objects.
    ///
    /// Expected columns: Global name | Type | Description (optional)
    ///
    /// @param table
    ///            the AsciiDoc table
    /// @return list of parsed globals
    public java.util.List<Global> parseGlobals(Table table) {
        java.util.List<Global> globals = new ArrayList<>();
        java.util.List<Row> bodyRows = getBodyRows(table);

        for (Row row : bodyRows) {
            java.util.List<Cell> cells = row.getCells();
            if (cells.size() >= 2) {
                String name = getCellText(cells.get(0));
                String type = getCellText(cells.get(1));
                String description = cells.size() > 2 ? getCellText(cells.get(2)) : null;

                if (!name.isBlank() && !type.isBlank()) {
                    globals.add(new Global.Definition(name.trim(), type.trim(),
                            description != null ? description.trim() : null));
                }
            }
        }
        return globals;
    }

    /// Parses a .outputs table into a list of Output objects.
    ///
    /// Expected columns: Output name | Type | Initial value | Description
    /// (optional)
    ///
    /// @param table
    ///            the AsciiDoc table
    /// @return list of parsed outputs
    public java.util.List<Output> parseOutputs(Table table) {
        java.util.List<Output> outputs = new ArrayList<>();
        java.util.List<Row> bodyRows = getBodyRows(table);

        for (Row row : bodyRows) {
            java.util.List<Cell> cells = row.getCells();
            if (cells.size() >= 3) {
                String name = getCellText(cells.get(0));
                String type = getCellText(cells.get(1));
                String initialStr = getCellText(cells.get(2));
                String description = cells.size() > 3 ? getCellText(cells.get(3)) : null;

                if (!name.isBlank() && !type.isBlank()) {
                    Expression initialValue = null;
                    if (!initialStr.isBlank()) {
                        initialValue = expressionParser.parseExpression(initialStr.trim());
                    }
                    outputs.add(new Output.Definition(name.trim(), type.trim(), initialValue,
                            description != null ? description.trim() : null));
                }
            }
        }
        return outputs;
    }

    /// Parses a .data-table into a DataTable object.
    ///
    /// The first row is treated as column headers. Subsequent rows contain data
    /// values.
    ///
    /// @param table
    ///            the AsciiDoc table
    /// @param id
    ///            the table ID
    /// @return the parsed DataTable
    public DataTable parseDataTable(Table table, String id) {
        java.util.List<String> headers = new ArrayList<>();
        java.util.List<DataTable.Row> rows = new ArrayList<>();

        // Get headers from first header row or first body row
        java.util.List<Row> headerRows = table.getHeader();
        if (!headerRows.isEmpty()) {
            for (Cell cell : headerRows.getFirst().getCells()) {
                headers.add(getCellText(cell).trim());
            }
        }

        // Parse body rows
        java.util.List<Row> bodyRows = getBodyRows(table);
        if (headers.isEmpty() && !bodyRows.isEmpty()) {
            // Use first body row as headers
            for (Cell cell : bodyRows.getFirst().getCells()) {
                headers.add(getCellText(cell).trim());
            }
            bodyRows = bodyRows.subList(1, bodyRows.size());
        }

        for (Row row : bodyRows) {
            Map<String, Expression> values = new LinkedHashMap<>();
            java.util.List<Cell> cells = row.getCells();
            for (int i = 0; i < Math.min(headers.size(), cells.size()); i++) {
                String cellText = getCellText(cells.get(i)).trim();
                if (!cellText.isBlank()) {
                    Expression expr = expressionParser.parseExpression(cellText);
                    if (expr != null) {
                        values.put(headers.get(i), expr);
                    }
                }
            }
            if (!values.isEmpty()) {
                rows.add(new DataTable.Row(values));
            }
        }

        return new DataTable.Default(id != null ? id : "unnamed", rows);
    }

    /// Gets the body rows of a table, skipping header rows.
    private java.util.List<Row> getBodyRows(Table table) {
        java.util.List<Row> body = table.getBody();
        if (body != null && !body.isEmpty()) {
            return body;
        }
        return java.util.List.of();
    }

    /// Gets the text content of a cell.
    private String getCellText(Cell cell) {
        String text = cell.getText();
        return text != null ? text : "";
    }
}
