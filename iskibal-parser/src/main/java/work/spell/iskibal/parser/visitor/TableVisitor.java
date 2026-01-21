package work.spell.iskibal.parser.visitor;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import work.spell.iskibal.model.DataTable;
import work.spell.iskibal.model.Expression;
import work.spell.iskibal.parser.IskaraLexer;
import work.spell.iskibal.parser.IskaraParser;
import work.spell.iskibal.parser.diagnostic.IskaraDiagnosticListener;

import java.util.*;

/**
 * Visitor that handles data table parsing and builds DataTable AST nodes.
 */
public class TableVisitor {

    private final ExpressionVisitor expressionVisitor;
    private final IskaraDiagnosticListener diagnostics;

    public TableVisitor(ExpressionVisitor expressionVisitor, IskaraDiagnosticListener diagnostics) {
        this.expressionVisitor = expressionVisitor;
        this.diagnostics = diagnostics;
    }

    /**
     * Parses a data table definition.
     */
    public DataTable parseDataTable(String id, IskaraParser.TableContentContext ctx) {
        List<List<String>> rawRows = parseTableRows(ctx);

        if (rawRows.isEmpty()) {
            return new DataTable.Default(id, List.of());
        }

        // First non-separator row is the header
        List<String> headers = null;
        List<DataTable.Row> dataRows = new ArrayList<>();

        for (List<String> row : rawRows) {
            if (isSeparatorRow(row)) {
                continue;
            }
            if (headers == null) {
                headers = row;
            } else {
                Map<String, Expression> values = new LinkedHashMap<>();
                for (int i = 0; i < Math.min(headers.size(), row.size()); i++) {
                    String cellText = row.get(i).trim();
                    Expression expr = parseCellExpression(cellText);
                    values.put(headers.get(i).trim(), expr);
                }
                dataRows.add(new DataTable.Row(values));
            }
        }

        return new DataTable.Default(id, dataRows);
    }

    /**
     * Parses table content into a list of rows, where each row is a list of cell strings.
     */
    private List<List<String>> parseTableRows(IskaraParser.TableContentContext ctx) {
        List<List<String>> rows = new ArrayList<>();

        for (var rowCtx : ctx.tableRow()) {
            List<String> cells = new ArrayList<>();
            for (var cellCtx : rowCtx.tableCell()) {
                StringBuilder sb = new StringBuilder();
                for (var content : cellCtx.tableCellContent()) {
                    sb.append(content.getText());
                }
                cells.add(sb.toString().trim());
            }
            // Remove trailing empty cells
            while (!cells.isEmpty() && cells.getLast().isEmpty()) {
                cells.removeLast();
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }

        return rows;
    }

    /**
     * Checks if a row is a separator row (contains only dashes).
     */
    private boolean isSeparatorRow(List<String> row) {
        for (String cell : row) {
            String trimmed = cell.trim();
            if (!trimmed.isEmpty() && !trimmed.matches("-+")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a cell's text content as an expression.
     */
    private Expression parseCellExpression(String cellText) {
        if (cellText.isEmpty()) {
            return new Expression.Literal.NullLiteral();
        }

        // Try to parse as an expression
        try {
            IskaraLexer lexer = new IskaraLexer(CharStreams.fromString(cellText));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            IskaraParser parser = new IskaraParser(tokens);

            // Suppress errors for cell parsing
            lexer.removeErrorListeners();
            parser.removeErrorListeners();

            IskaraParser.ExpressionContext exprCtx = parser.expression();
            if (parser.getNumberOfSyntaxErrors() == 0) {
                return expressionVisitor.visit(exprCtx);
            }
        } catch (Exception e) {
            // Fall through to string literal
        }

        // Default: treat as string literal
        return new Expression.Literal.StringLiteral(cellText);
    }

    /**
     * Parses decision table structure from table content.
     * Returns a map of column headers to their types (WHEN or THEN).
     */
    public DecisionTableStructure parseDecisionTableStructure(IskaraParser.TableContentContext ctx) {
        List<List<String>> rawRows = parseTableRows(ctx);

        if (rawRows.size() < 2) {
            return new DecisionTableStructure(List.of(), List.of(), List.of());
        }

        // First row: ID, WHEN..., THEN...
        List<String> headerRow = rawRows.get(0);
        // Second row: column expressions/aliases
        List<String> exprRow = rawRows.size() > 1 ? rawRows.get(1) : List.of();

        List<ColumnDef> whenColumns = new ArrayList<>();
        List<ColumnDef> thenColumns = new ArrayList<>();
        int idColumnIndex = -1;

        boolean inWhen = false;
        boolean inThen = false;

        for (int i = 0; i < headerRow.size(); i++) {
            String header = headerRow.get(i).trim().toUpperCase();
            String expr = i < exprRow.size() ? exprRow.get(i).trim() : "";

            if (header.equals("ID") || header.isEmpty() && idColumnIndex < 0) {
                idColumnIndex = i;
            } else if (header.equals("WHEN")) {
                inWhen = true;
                inThen = false;
                whenColumns.add(new ColumnDef(i, expr));
            } else if (header.equals("THEN")) {
                inWhen = false;
                inThen = true;
                thenColumns.add(new ColumnDef(i, expr));
            } else if (inWhen) {
                whenColumns.add(new ColumnDef(i, expr));
            } else if (inThen) {
                thenColumns.add(new ColumnDef(i, expr));
            }
        }

        // Parse rule rows (skip header and expression rows, and any separator rows)
        List<DecisionRow> ruleRows = new ArrayList<>();
        for (int rowIdx = 2; rowIdx < rawRows.size(); rowIdx++) {
            List<String> row = rawRows.get(rowIdx);
            if (isSeparatorRow(row)) {
                continue;
            }

            String ruleId = idColumnIndex >= 0 && idColumnIndex < row.size()
                    ? row.get(idColumnIndex).trim()
                    : "RULE_" + (ruleRows.size() + 1);

            List<String> whenCells = whenColumns.stream()
                    .map(col -> col.index < row.size() ? row.get(col.index).trim() : "")
                    .toList();

            List<String> thenCells = thenColumns.stream()
                    .map(col -> col.index < row.size() ? row.get(col.index).trim() : "")
                    .toList();

            ruleRows.add(new DecisionRow(ruleId, whenCells, thenCells));
        }

        return new DecisionTableStructure(whenColumns, thenColumns, ruleRows);
    }

    /**
     * Column definition for decision tables.
     */
    public record ColumnDef(int index, String expression) {}

    /**
     * A single row from a decision table.
     */
    public record DecisionRow(String id, List<String> whenCells, List<String> thenCells) {}

    /**
     * Parsed decision table structure.
     */
    public record DecisionTableStructure(
            List<ColumnDef> whenColumns,
            List<ColumnDef> thenColumns,
            List<DecisionRow> rows
    ) {}
}
