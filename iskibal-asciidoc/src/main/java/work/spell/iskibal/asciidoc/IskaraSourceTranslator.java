package work.spell.iskibal.asciidoc;

import module java.base;

import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.DescriptionList;
import org.asciidoctor.ast.DescriptionListEntry;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.Row;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;

import work.spell.iskibal.parser.diagnostic.Diagnostic;
import work.spell.iskibal.parser.diagnostic.SourceLocation;

/// Translates an AsciiDoc document containing Iskara rule definitions into
/// Iskara source text.
///
/// Traverses the AsciiDoc DOM and produces a complete Iskara source string
/// that can be parsed by the Iskara parser in one shot. This eliminates
/// intermediate parsing and round-trip serialization.
public class IskaraSourceTranslator {

    private static final Pattern CAPTION_PATTERN = Pattern.compile("^([A-Z0-9_-]+):\\s*(.*)$");

    /// Known roles that trigger special processing.
    private static final Set<String> KNOWN_ROLES = Set.of("rule", "imports", "facts", "globals", "outputs",
            "data-table", "decision-table", "aliases");

    /// Result of translating an AsciiDoc document to Iskara source.
    ///
    /// @param source
    ///            the generated Iskara source text
    /// @param diagnostics
    ///            any warnings or errors encountered during translation
    public record TranslationResult(String source, List<Diagnostic> diagnostics) {
        /// Returns true if there are any errors.
        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        }
    }

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    /// Translates an AsciiDoc document into Iskara source text.
    ///
    /// @param document
    ///            the parsed AsciiDoc document
    /// @return the translation result containing Iskara source and diagnostics
    public TranslationResult translate(Document document) {
        diagnostics.clear();
        StringBuilder sb = new StringBuilder();

        translateBlocks(document, document, sb);

        return new TranslationResult(sb.toString(), List.copyOf(diagnostics));
    }

    /// Recursively processes blocks in the document, appending Iskara source.
    private void translateBlocks(Document document, StructuralNode node, StringBuilder sb) {
        for (StructuralNode child : node.getBlocks()) {
            String role = findKnownRole(child);

            if (role != null) {
                switch (role) {
                    case "imports" -> translateImports(child, sb);
                    case "facts" -> {
                        if (child instanceof Table table) {
                            translateFacts(table, sb);
                        }
                    }
                    case "globals" -> {
                        if (child instanceof Table table) {
                            translateGlobals(table, sb);
                        }
                    }
                    case "outputs" -> {
                        if (child instanceof Table table) {
                            translateOutputs(table, sb);
                        }
                    }
                    case "data-table" -> {
                        if (child instanceof Table table) {
                            translateDataTable(table, child.getId(), sb);
                        }
                    }
                    case "decision-table" -> {
                        if (child instanceof Table table) {
                            translateDecisionTable(document, table, sb);
                        }
                    }
                    case "rule" -> translateRule(child, sb);
                    case "aliases" -> {
                        // Processed when building decision tables
                    }
                    default -> translateBlocks(document, child, sb);
                }
            } else if (isIskaraSourceBlock(child)) {
                translateRule(child, sb);
            } else {
                translateBlocks(document, child, sb);
            }
        }
    }

    /// Translates imports from a definition list or example block.
    private void translateImports(StructuralNode block, StringBuilder sb) {
        List<String[]> entries = new ArrayList<>();

        if (block instanceof DescriptionList descList) {
            collectImportEntries(descList, entries);
        } else {
            for (StructuralNode child : block.getBlocks()) {
                if (child instanceof DescriptionList descList) {
                    collectImportEntries(descList, entries);
                }
            }
            // Fallback: parse from block content
            if (entries.isEmpty()) {
                String content = getBlockContent(block);
                if (content != null) {
                    for (String line : content.split("\n")) {
                        if (line.contains("::")) {
                            String[] parts = line.split("::", 2);
                            if (parts.length == 2) {
                                String alias = parts[0].trim();
                                String type = parts[1].trim();
                                if (!alias.isBlank() && !type.isBlank()) {
                                    entries.add(new String[] { alias, type });
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!entries.isEmpty()) {
            sb.append("\nimports {\n");
            for (String[] entry : entries) {
                sb.append("  ").append(entry[0]).append(" := ").append(entry[1]).append("\n");
            }
            sb.append("}\n");
        }
    }

    private void collectImportEntries(DescriptionList descList, List<String[]> entries) {
        for (DescriptionListEntry entry : descList.getItems()) {
            String alias = extractTermText(entry);
            String type = extractDescriptionText(entry);
            if (alias != null && type != null && !alias.isBlank() && !type.isBlank()) {
                entries.add(new String[] { alias.trim(), type.trim() });
            }
        }
    }

    /// Translates a facts table to Iskara source.
    private void translateFacts(Table table, StringBuilder sb) {
        translateNameTypeSection("facts", table, sb);
    }

    /// Translates a globals table to Iskara source.
    private void translateGlobals(Table table, StringBuilder sb) {
        translateNameTypeSection("globals", table, sb);
    }

    /// Translates a `name: type` declaration table (facts or globals) to Iskara source.
    private void translateNameTypeSection(String keyword, Table table, StringBuilder sb) {
        List<Row> bodyRows = table.getBody();
        if (bodyRows == null || bodyRows.isEmpty()) return;

        sb.append("\n").append(keyword).append(" {\n");
        for (Row row : bodyRows) {
            List<Cell> cells = row.getCells();
            if (cells.size() >= 2) {
                String name = getCellText(cells.get(0)).trim();
                String type = getCellText(cells.get(1)).trim();
                if (name.isBlank() || type.isBlank()) continue;

                sb.append("  ").append(name).append(": ").append(type);
                if (cells.size() > 2) {
                    String desc = getCellText(cells.get(2)).trim();
                    if (!desc.isBlank()) {
                        sb.append(" \"").append(escapeString(desc)).append("\"");
                    }
                }
                sb.append("\n");
            }
        }
        sb.append("}\n");
    }

    /// Translates an outputs table to Iskara source.
    private void translateOutputs(Table table, StringBuilder sb) {
        List<Row> bodyRows = table.getBody();
        if (bodyRows == null || bodyRows.isEmpty()) return;

        sb.append("\noutputs {\n");
        for (Row row : bodyRows) {
            List<Cell> cells = row.getCells();
            if (cells.size() >= 3) {
                String name = getCellText(cells.get(0)).trim();
                String type = getCellText(cells.get(1)).trim();
                String initialValue = getCellText(cells.get(2)).trim();
                if (name.isBlank() || type.isBlank()) continue;

                sb.append("  ").append(name).append(": ").append(type);
                if (!initialValue.isBlank()) {
                    sb.append(" := ").append(initialValue);
                }
                if (cells.size() > 3) {
                    String desc = getCellText(cells.get(3)).trim();
                    if (!desc.isBlank()) {
                        sb.append(" \"").append(escapeString(desc)).append("\"");
                    }
                }
                sb.append("\n");
            }
        }
        sb.append("}\n");
    }

    /// Translates a data table to Iskara source.
    private void translateDataTable(Table table, String id, StringBuilder sb) {
        String tableId = id != null ? id : "unnamed";

        sb.append("\ndata table ").append(quoteIdIfNeeded(tableId)).append(" {\n");

        // Header row
        List<Row> headerRows = table.getHeader();
        if (!headerRows.isEmpty()) {
            sb.append(formatRow(extractCellTexts(headerRows.getFirst())));
        }

        // Separator
        List<Row> bodyRows = table.getBody();
        int numCols = headerRows.isEmpty() ? (bodyRows.isEmpty() ? 0 : bodyRows.getFirst().getCells().size())
                : headerRows.getFirst().getCells().size();
        if (numCols > 0) {
            sb.append("| ");
            for (int i = 0; i < numCols; i++) {
                if (i > 0) sb.append(" | ");
                sb.append("-".repeat(12));
            }
            sb.append(" |\n");
        }

        // Body rows
        if (bodyRows != null) {
            for (Row row : bodyRows) {
                sb.append(formatRow(extractCellTexts(row)));
            }
        }

        sb.append("}\n");
    }

    /// Translates a rule source block to Iskara source.
    private void translateRule(StructuralNode block, StringBuilder sb) {
        if (!(block instanceof Block listing)) return;

        String source = listing.getSource();
        if (source == null || source.isBlank()) return;

        // Extract rule ID and description from caption/title
        String title = listing.getTitle();
        String caption = listing.getCaption();
        String captionText = title != null ? title : caption;

        sb.append("\n");
        if (captionText != null) {
            Matcher m = CAPTION_PATTERN.matcher(captionText.trim());
            if (m.matches()) {
                String id = m.group(1);
                String description = m.group(2);
                sb.append("rule ").append(quoteIdIfNeeded(id));
                if (description != null && !description.isBlank()) {
                    sb.append(" \"").append(escapeString(description)).append("\"");
                }
                sb.append("\n");
            }
        }

        sb.append(source);
        if (!source.trim().endsWith("end")) {
            sb.append("\nend");
        }
        sb.append("\n");
    }

    /// Translates a decision table with optional aliases to Iskara source.
    private void translateDecisionTable(Document document, Table table, StringBuilder sb) {
        String id = table.getId();
        String title = table.getTitle();
        String caption = table.getCaption();
        String description = title != null ? title : caption;

        // Check for language attribute (e.g. [.decision-table#ID,language=drools])
        Object languageAttr = table.getAttribute("language");
        boolean drlNative = languageAttr != null && "drools".equalsIgnoreCase(languageAttr.toString());

        sb.append("\ndecision table ");
        if (drlNative) {
            sb.append("[drools] ");
        }
        sb.append(quoteIdIfNeeded(id));
        if (description != null && !description.isBlank()) {
            sb.append(" \"").append(escapeString(description)).append("\"");
        }
        sb.append(" {\n");

        // Get rows
        List<Row> headerRows = table.getHeader();
        List<Row> bodyRows = table.getBody();

        Row structureRow;
        Row expressionRow;
        List<Row> dataRows;

        if (headerRows.size() >= 2) {
            structureRow = headerRows.get(0);
            expressionRow = headerRows.get(1);
            dataRows = bodyRows;
        } else if (headerRows.size() == 1 && !bodyRows.isEmpty()) {
            structureRow = headerRows.get(0);
            expressionRow = bodyRows.get(0);
            dataRows = bodyRows.subList(1, bodyRows.size());
        } else {
            diagnostics.add(Diagnostic.error(
                    "Decision table '%s' requires a structure row and an expression row".formatted(id),
                    SourceLocation.at("<asciidoc>", 0, 0)));
            sb.append("}\n");
            return;
        }

        // Structure row with colspans expanded
        List<String> structureCells = expandColspans(structureRow);
        sb.append(formatRow(structureCells));

        // Expression row
        sb.append(formatRow(extractCellTexts(expressionRow)));

        // Separator
        sb.append("| ").append("-".repeat(15));
        for (int i = 1; i < structureCells.size(); i++) {
            sb.append(" | ").append("-".repeat(12));
        }
        sb.append(" |\n");

        // Data rows with rowspan expansion
        for (List<String> dataCells : expandRowspans(dataRows, structureCells.size())) {
            sb.append(formatRow(dataCells));
        }

        sb.append("}");

        // Collect aliases for this decision table
        Map<String, String> aliases = collectAliasTexts(document, id);
        if (!aliases.isEmpty()) {
            sb.append(" where ");
            boolean first = true;
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                if (!first) {
                    sb.append(",\n  ");
                }
                first = false;
                sb.append(quoteIdIfNeeded(entry.getKey()));
                sb.append(" := ");
                String aliasSource = entry.getValue().trim();
                if (!aliasSource.startsWith("[")) {
                    aliasSource = "[" + aliasSource + "]";
                }
                sb.append(aliasSource);
            }
        }

        sb.append("\n");
    }

    /// Collects raw Iskara source texts for aliases targeting the given table.
    private Map<String, String> collectAliasTexts(Document document, String tableId) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (StructuralNode block : findAllBlocks(document)) {
            if (isAliasBlockFor(block, tableId) && block instanceof DescriptionList descList) {
                for (DescriptionListEntry entry : descList.getItems()) {
                    String aliasName = extractTermText(entry);
                    if (aliasName == null || aliasName.isBlank()) continue;

                    String iskaraSource = extractIskaraSource(entry);
                    if (iskaraSource != null && !iskaraSource.isBlank()) {
                        aliases.put(aliasName, iskaraSource);
                    }
                }
            }
        }
        return aliases;
    }

    /// Checks if a block is an alias definition list for the given table ID.
    private boolean isAliasBlockFor(StructuralNode block, String tableId) {
        if (!(block instanceof DescriptionList)) return false;
        List<String> roles = block.getRoles();
        if (roles == null || !roles.contains("aliases")) return false;
        Object forAttr = block.getAttribute("for");
        return forAttr != null && forAttr.toString().equals(tableId);
    }

    /// Extracts Iskara source from a description list entry (alias definition).
    private String extractIskaraSource(DescriptionListEntry entry) {
        ListItem description = entry.getDescription();
        if (description == null) return null;

        for (StructuralNode child : description.getBlocks()) {
            if (child instanceof Block listing && "listing".equals(listing.getContext())) {
                String style = listing.getStyle();
                Object language = listing.getAttribute("language");
                if ("source".equals(style) && "iskara".equals(language)) {
                    return listing.getSource();
                }
            }
        }

        // Fallback: use description text directly
        String text = description.getText();
        return (text != null && !text.isBlank()) ? text : null;
    }

    /// Recursively finds all blocks in a document.
    private List<StructuralNode> findAllBlocks(StructuralNode node) {
        List<StructuralNode> blocks = new ArrayList<>();
        blocks.add(node);
        for (StructuralNode child : node.getBlocks()) {
            blocks.addAll(findAllBlocks(child));
        }
        return blocks;
    }

    /// Finds the single known Iskara role from a block's roles list.
    private String findKnownRole(StructuralNode block) {
        List<String> roles = block.getRoles();
        if (roles == null || roles.isEmpty()) return null;

        List<String> expandedRoles = roles.stream()
                .flatMap(r -> Arrays.stream(r.split("\\s+")))
                .filter(r -> !r.isBlank())
                .toList();
        List<String> knownRolesFound = expandedRoles.stream()
                .filter(KNOWN_ROLES::contains)
                .toList();
        if (knownRolesFound.size() > 1) {
            String roleList = knownRolesFound.stream()
                    .map(r -> "\"" + r + "\"")
                    .collect(java.util.stream.Collectors.joining(", "));
            diagnostics.add(Diagnostic.error(
                    "Duplicate role assignment " + roleList,
                    SourceLocation.at("<asciidoc>", block.getSourceLocation() != null
                            ? block.getSourceLocation().getLineNumber() : 0, 0)));
            return null;
        }
        return knownRolesFound.isEmpty() ? null : knownRolesFound.getFirst();
    }

    /// Checks if a block is an Iskara source block.
    private boolean isIskaraSourceBlock(StructuralNode block) {
        if (!(block instanceof Block listing)) return false;
        String style = listing.getStyle();
        Object language = listing.getAttribute("language");
        return "source".equals(style) && "iskara".equals(language);
    }

    /// Expands colspans in a row to individual cells.
    private List<String> expandColspans(Row row) {
        List<String> cells = new ArrayList<>();
        for (Cell cell : row.getCells()) {
            String text = getCellText(cell);
            int colspan = cell.getColspan();
            if (colspan <= 0) colspan = 1;
            for (int i = 0; i < colspan; i++) {
                cells.add(text);
            }
        }
        return cells;
    }

    /// Expands rowspans across a list of rows, duplicating the spanned cell's
    /// value into every row it covers.
    private List<List<String>> expandRowspans(List<Row> rows, int numCols) {
        String[] spanText = new String[numCols];
        int[] spanRemaining = new int[numCols];

        List<List<String>> result = new ArrayList<>();
        for (Row row : rows) {
            String[] rowCells = new String[numCols];
            int col = 0;

            for (Cell cell : row.getCells()) {
                while (col < numCols && spanRemaining[col] > 0) {
                    rowCells[col] = spanText[col];
                    spanRemaining[col]--;
                    col++;
                }
                if (col >= numCols) break;

                String text = getCellText(cell);
                int colspan = Math.max(1, cell.getColspan());
                int rowspan = Math.max(1, cell.getRowspan());
                for (int c = 0; c < colspan && col + c < numCols; c++) {
                    rowCells[col + c] = text;
                    if (rowspan > 1) {
                        spanText[col + c] = text;
                        spanRemaining[col + c] = rowspan - 1;
                    }
                }
                col += colspan;
            }
            while (col < numCols) {
                if (spanRemaining[col] > 0) {
                    rowCells[col] = spanText[col];
                    spanRemaining[col]--;
                }
                col++;
            }

            result.add(Arrays.asList(rowCells));
        }
        return result;
    }

    /// Extracts cell texts from a row.
    private List<String> extractCellTexts(Row row) {
        List<String> cells = new ArrayList<>();
        for (Cell cell : row.getCells()) {
            cells.add(getCellText(cell));
        }
        return cells;
    }

    /// Formats a row as Iskara table syntax.
    private String formatRow(List<String> cells) {
        StringBuilder sb = new StringBuilder("| ");
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(cells.get(i));
        }
        sb.append(" |\n");
        return sb.toString();
    }

    /// Gets the text content of a cell, decoding HTML entities.
    private String getCellText(Cell cell) {
        String text = cell.getText();
        if (text == null) return "";
        return decodeHtmlEntities(text);
    }

    /// Decodes HTML entities that AsciiDoc may produce in table cells.
    static String decodeHtmlEntities(String text) {
        return text
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#8656;", "<=")
                .replace("&#8658;", ">=")
                .replace("\u21D0", "<=")
                .replace("\u21D2", ">=");
    }

    /// Quotes an identifier if it contains non-identifier characters.
    static String quoteIdIfNeeded(String id) {
        if (id != null && id.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return id;
        }
        return "`" + id + "`";
    }

    /// Escapes a string for use in Iskara string literals.
    private static String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractTermText(DescriptionListEntry entry) {
        List<ListItem> terms = entry.getTerms();
        if (terms == null || terms.isEmpty()) return null;
        return terms.getFirst().getText();
    }

    private String extractDescriptionText(DescriptionListEntry entry) {
        ListItem description = entry.getDescription();
        if (description == null) return null;
        return description.getText();
    }

    private String getBlockContent(StructuralNode block) {
        if (block instanceof Block listing) {
            String source = listing.getSource();
            if (source != null) return source;
            Object content = listing.getContent();
            return content != null ? content.toString() : null;
        }
        return null;
    }
}
