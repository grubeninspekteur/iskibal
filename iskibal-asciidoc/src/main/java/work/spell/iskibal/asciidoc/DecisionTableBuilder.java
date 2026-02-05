package work.spell.iskibal.asciidoc;

import java.util.ArrayList;
import java.util.Map;

import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.Row;
import org.asciidoctor.ast.Table;

import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.model.Rule.DecisionTableRule;
import work.spell.iskibal.parser.api.ParseResult;

/**
 * Builds {@link DecisionTableRule} instances from AsciiDoc tables.
 * <p>
 * Converts AsciiDoc table structure to Iskara decision table syntax and parses
 * it using the Iskara parser.
 */
public class DecisionTableBuilder {

    private final ExpressionParser expressionParser;

    /**
     * Creates a DecisionTableBuilder with the given expression parser.
     *
     * @param expressionParser
     *            the parser for cell expressions
     */
    public DecisionTableBuilder(ExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
    }

    /**
     * Builds a DecisionTableRule from an AsciiDoc table.
     *
     * @param table
     *            the AsciiDoc table
     * @param id
     *            the rule ID
     * @param description
     *            the rule description (from caption)
     * @param aliases
     *            resolved aliases for this table
     * @return the built DecisionTableRule
     */
    public DecisionTableRule build(Table table, String id, String description,
            Map<String, Expression.Block> aliases) {

        // Build Iskara decision table source
        String iskaraSource = buildIskaraSource(table, id, description, aliases);

        // Parse with Iskara parser
        ParseResult<RuleModule> result = expressionParser.parseModule(iskaraSource);

        if (result.isSuccess() && result.getValue().isPresent()) {
            RuleModule module = result.getValue().get();
            if (!module.rules().isEmpty() && module.rules().getFirst() instanceof DecisionTableRule dtRule) {
                return dtRule;
            }
        }

        // If parsing failed, throw with diagnostics
        throw new IllegalArgumentException(
                "Failed to parse decision table '%s': %s".formatted(id, result.getDiagnostics()));
    }

    /**
     * Builds Iskara decision table source from AsciiDoc table structure.
     */
    private String buildIskaraSource(Table table, String id, String description,
            Map<String, Expression.Block> aliases) {
        StringBuilder sb = new StringBuilder();

        // Decision table header
        sb.append("decision table ");
        sb.append(quoteIdIfNeeded(id));
        if (description != null && !description.isBlank()) {
            sb.append(" \"").append(description.replace("\"", "\\\"")).append("\"");
        }
        sb.append(" {\n");

        // Get rows
        java.util.List<Row> headerRows = table.getHeader();
        java.util.List<Row> bodyRows = table.getBody();

        // Structure row (first header or implied from body)
        Row structureRow;
        Row expressionRow;
        java.util.List<Row> dataRows;

        if (headerRows.size() >= 2) {
            structureRow = headerRows.get(0);
            expressionRow = headerRows.get(1);
            dataRows = bodyRows;
        } else if (headerRows.size() == 1 && !bodyRows.isEmpty()) {
            structureRow = headerRows.get(0);
            expressionRow = bodyRows.get(0);
            dataRows = bodyRows.subList(1, bodyRows.size());
        } else {
            throw new IllegalArgumentException(
                    "Decision table '%s' requires a structure row and an expression row".formatted(id));
        }

        // Build structure row with colspans expanded
        java.util.List<String> structureCells = expandColspans(structureRow);
        sb.append(formatRow(structureCells));

        // Build expression row
        java.util.List<String> exprCells = extractCellTexts(expressionRow);
        sb.append(formatRow(exprCells));

        // Separator row
        sb.append("| ").append("-".repeat(15));
        for (int i = 1; i < structureCells.size(); i++) {
            sb.append(" | ").append("-".repeat(12));
        }
        sb.append(" |\n");

        // Data rows
        for (Row dataRow : dataRows) {
            java.util.List<String> dataCells = extractCellTexts(dataRow);
            sb.append(formatRow(dataCells));
        }

        sb.append("}");

        // Add where clause for aliases
        if (!aliases.isEmpty()) {
            sb.append(" where ");
            boolean first = true;
            for (Map.Entry<String, Expression.Block> entry : aliases.entrySet()) {
                if (!first) {
                    sb.append(",\n  ");
                }
                first = false;
                sb.append(quoteIdIfNeeded(entry.getKey()));
                sb.append(" := ");
                sb.append(blockToSource(entry.getValue()));
            }
        }

        sb.append("\n");
        return sb.toString();
    }

    /**
     * Expands colspans in a row to individual cells.
     */
    private java.util.List<String> expandColspans(Row row) {
        java.util.List<String> cells = new ArrayList<>();
        for (Cell cell : row.getCells()) {
            String text = getCellText(cell);
            int colspan = cell.getColspan();
            if (colspan <= 0) {
                colspan = 1;
            }
            for (int i = 0; i < colspan; i++) {
                cells.add(text);
            }
        }
        return cells;
    }

    /**
     * Extracts cell texts from a row.
     */
    private java.util.List<String> extractCellTexts(Row row) {
        java.util.List<String> cells = new ArrayList<>();
        for (Cell cell : row.getCells()) {
            cells.add(getCellText(cell));
        }
        return cells;
    }

    /**
     * Formats a row as Iskara table syntax.
     */
    private String formatRow(java.util.List<String> cells) {
        StringBuilder sb = new StringBuilder("| ");
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(cells.get(i));
        }
        sb.append(" |\n");
        return sb.toString();
    }

    /**
     * Gets the text content of a cell, decoding HTML entities.
     */
    private String getCellText(Cell cell) {
        String text = cell.getText();
        if (text == null) {
            return "";
        }
        // Decode HTML entities
        text = text
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");

        // Decode numeric HTML entities (e.g., &#8656; for ⇐ which AsciiDoc uses for <=)
        // We need to restore the original <= instead of the arrow
        text = text.replace("&#8656;", "<=");
        text = text.replace("&#8658;", ">="); // Right double arrow for >=
        text = text.replace("\u21D0", "<="); // ⇐ character
        text = text.replace("\u21D2", ">="); // ⇒ character

        return text;
    }

    /**
     * Quotes an identifier if needed.
     */
    private String quoteIdIfNeeded(String id) {
        if (id.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return id;
        }
        return "`" + id + "`";
    }

    /**
     * Converts a Block expression back to source code.
     */
    private String blockToSource(Expression.Block block) {
        // Simple representation - for more complex blocks we'd need a proper
        // printer
        StringBuilder sb = new StringBuilder("[");
        if (!block.parameters().isEmpty()) {
            sb.append(":");
            for (int i = 0; i < block.parameters().size(); i++) {
                if (i > 0) {
                    sb.append(" :");
                }
                sb.append(block.parameters().get(i));
            }
            sb.append(" | ");
        }
        // For now, just mark that there's content
        sb.append("/* alias content */");
        sb.append("]");
        return sb.toString();
    }
}
