package work.spell.iskibal.parser.asciidoc;

import module java.base;

/// Extracts Iskara code blocks from AsciiDoc documents.
///
/// Supports extraction of:
/// - Rule blocks: [source,iskara,.rule]
/// - Import definition lists: [.imports]
/// - Fact tables: [.facts]
/// - Output tables: [.outputs]
/// - Global tables: [.globals]
/// - Data tables: [.data-table]
/// - Decision tables: [.decision-table]
///
/// @deprecated Use `work.spell.iskibal.asciidoc.AsciiDocParser` from the
///             `iskibal-asciidoc` module instead. This regex-based
///             extractor does not handle include directives or complex AsciiDoc
///             structures properly.
@Deprecated(since = "0.2.0", forRemoval = true)
public class AsciiDocExtractor {

    // Pattern for source blocks with iskara
    private static final Pattern SOURCE_BLOCK_START = Pattern.compile("^\\[source,iskara(?:,\\.([a-z-]+))?\\]\\s*$",
            Pattern.MULTILINE);

    // Pattern for role-annotated blocks
    private static final Pattern ROLE_BLOCK = Pattern.compile("^\\[\\.([a-z-]+)(?:,.*)?\\]\\s*$", Pattern.MULTILINE);

    // Pattern for block ID (used for data tables)
    private static final Pattern BLOCK_ID = Pattern.compile("^\\[#([a-zA-Z_][a-zA-Z0-9_-]*),?\\.?([a-z-]*).*\\]\\s*$",
            Pattern.MULTILINE);

    // Pattern for caption line (rule ID: description)
    private static final Pattern CAPTION = Pattern.compile("^\\.([A-Z0-9_-]+):\\s*(.*)\\s*$", Pattern.MULTILINE);

    // Block delimiters
    private static final String LISTING_DELIMITER = "----";
    private static final String EXAMPLE_DELIMITER = "====";
    private static final String TABLE_DELIMITER = "|===";

    /// Extracts all Iskara content from an AsciiDoc document and combines it into a
    /// single Iskara source string.
    public String extractIskara(String asciidoc) {
        StringBuilder result = new StringBuilder();
        List<ExtractedBlock> blocks = new ArrayList<>();

        // Extract rule blocks
        blocks.addAll(extractSourceBlocks(asciidoc));

        // Extract definition lists (imports, facts, globals, outputs)
        blocks.addAll(extractDefinitionLists(asciidoc));

        // Extract tables (facts, outputs, globals, data-table, decision-table)
        blocks.addAll(extractTables(asciidoc));

        // Sort by type priority: imports first, then facts/globals/outputs, then data
        // tables, then rules
        blocks.sort((a, b) -> {
            int priorityA = getTypePriority(a.type());
            int priorityB = getTypePriority(b.type());
            return Integer.compare(priorityA, priorityB);
        });

        // Combine into single source
        for (ExtractedBlock block : blocks) {
            String converted = convertToIskara(block);
            if (!converted.isEmpty()) {
                result.append(converted).append("\n\n");
            }
        }

        return result.toString();
    }

    private int getTypePriority(String type) {
        return switch (type) {
            case "imports" -> 1;
            case "facts" -> 2;
            case "globals" -> 3;
            case "outputs" -> 4;
            case "data-table" -> 5;
            case "rule" -> 6;
            case "decision-table" -> 7;
            default -> 10;
        };
    }

    /// Extracts [source,iskara] blocks from the document.
    private List<ExtractedBlock> extractSourceBlocks(String asciidoc) {
        List<ExtractedBlock> blocks = new ArrayList<>();
        String[] lines = asciidoc.split("\n");

        String pendingCaption = null;
        String pendingDescription = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Check for caption
            Matcher captionMatcher = CAPTION.matcher(line);
            if (captionMatcher.matches()) {
                pendingCaption = captionMatcher.group(1);
                pendingDescription = captionMatcher.group(2);
                continue;
            }

            // Check for source block
            Matcher sourceMatcher = SOURCE_BLOCK_START.matcher(line);
            if (sourceMatcher.matches()) {
                String role = sourceMatcher.group(1);
                if (role == null)
                    role = "rule";

                // Find block content
                i++; // Move past the attribute line
                if (i < lines.length && lines[i].equals(LISTING_DELIMITER)) {
                    i++; // Move past the opening delimiter
                    StringBuilder content = new StringBuilder();
                    while (i < lines.length && !lines[i].equals(LISTING_DELIMITER)) {
                        content.append(lines[i]).append("\n");
                        i++;
                    }

                    blocks.add(new ExtractedBlock(role, pendingCaption, pendingDescription, content.toString().trim()));
                }

                pendingCaption = null;
                pendingDescription = null;
            }
        }

        return blocks;
    }

    /// Extracts definition lists ([.imports], etc.) from the document.
    private List<ExtractedBlock> extractDefinitionLists(String asciidoc) {
        List<ExtractedBlock> blocks = new ArrayList<>();
        String[] lines = asciidoc.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Check for role block
            Matcher roleMatcher = ROLE_BLOCK.matcher(line);
            if (roleMatcher.matches()) {
                String role = roleMatcher.group(1);
                if (role.equals("imports")) {
                    // Find the example block
                    i++;
                    if (i < lines.length && lines[i].equals(EXAMPLE_DELIMITER)) {
                        i++;
                        StringBuilder content = new StringBuilder();
                        while (i < lines.length && !lines[i].equals(EXAMPLE_DELIMITER)) {
                            content.append(lines[i]).append("\n");
                            i++;
                        }
                        blocks.add(new ExtractedBlock(role, null, null, content.toString().trim()));
                    }
                }
            }
        }

        return blocks;
    }

    /// Extracts tables ([.facts], [.outputs], [.globals], [.data-table],
    /// [.decision-table]).
    private List<ExtractedBlock> extractTables(String asciidoc) {
        List<ExtractedBlock> blocks = new ArrayList<>();
        String[] lines = asciidoc.split("\n");

        String pendingId = null;
        String pendingRole = null;
        String pendingCaption = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Check for caption
            if (line.startsWith(".") && !line.startsWith("..")) {
                pendingCaption = line.substring(1).trim();
                continue;
            }

            // Check for block ID with role
            Matcher idMatcher = BLOCK_ID.matcher(line);
            if (idMatcher.matches()) {
                pendingId = idMatcher.group(1);
                pendingRole = idMatcher.group(2);
                if (pendingRole == null || pendingRole.isEmpty()) {
                    pendingRole = null;
                }
                continue;
            }

            // Check for role block
            Matcher roleMatcher = ROLE_BLOCK.matcher(line);
            if (roleMatcher.matches()) {
                pendingRole = roleMatcher.group(1);
                continue;
            }

            // Check for table start
            if (line.equals(TABLE_DELIMITER) && pendingRole != null) {
                i++;
                StringBuilder content = new StringBuilder();
                while (i < lines.length && !lines[i].equals(TABLE_DELIMITER)) {
                    content.append(lines[i]).append("\n");
                    i++;
                }

                blocks.add(new ExtractedBlock(pendingRole, pendingId, pendingCaption, content.toString().trim()));

                pendingId = null;
                pendingRole = null;
                pendingCaption = null;
            }
        }

        return blocks;
    }

    /// Converts an extracted block to Iskara syntax.
    private String convertToIskara(ExtractedBlock block) {
        return switch (block.type()) {
            case "imports" -> convertImports(block);
            case "facts" -> convertFacts(block);
            case "globals" -> convertGlobals(block);
            case "outputs" -> convertOutputs(block);
            case "data-table" -> convertDataTable(block);
            case "decision-table" -> convertDecisionTable(block);
            case "rule" -> convertRule(block);
            default -> "";
        };
    }

    private String convertImports(ExtractedBlock block) {
        StringBuilder sb = new StringBuilder("imports {\n");
        // Parse definition list format: Alias:: type
        for (String line : block.content().split("\n")) {
            if (line.contains("::")) {
                String[] parts = line.split("::", 2);
                String alias = parts[0].trim();
                String type = parts[1].trim();
                sb.append("  ").append(alias).append(" := ").append(type).append("\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String convertFacts(ExtractedBlock block) {
        StringBuilder sb = new StringBuilder("facts {\n");
        // Parse table format: | name | type | description |
        for (String line : block.content().split("\n")) {
            if (line.startsWith("|") && !line.contains("---")) {
                String[] cells = line.split("\\|");
                if (cells.length >= 3) {
                    String name = cells[1].trim();
                    String type = cells[2].trim();
                    String desc = cells.length > 3 ? cells[3].trim() : "";
                    if (!name.isEmpty() && !name.equalsIgnoreCase("fact name")) {
                        sb.append("  ").append(name).append(": ").append(type);
                        if (!desc.isEmpty()) {
                            sb.append(" \"").append(desc).append("\"");
                        }
                        sb.append("\n");
                    }
                }
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String convertGlobals(ExtractedBlock block) {
        StringBuilder sb = new StringBuilder("globals {\n");
        for (String line : block.content().split("\n")) {
            if (line.startsWith("|") && !line.contains("---")) {
                String[] cells = line.split("\\|");
                if (cells.length >= 3) {
                    String name = cells[1].trim();
                    String type = cells[2].trim();
                    String desc = cells.length > 3 ? cells[3].trim() : "";
                    if (!name.isEmpty() && !name.equalsIgnoreCase("global name") && !name.equalsIgnoreCase("name")) {
                        sb.append("  ").append(name).append(": ").append(type);
                        if (!desc.isEmpty()) {
                            sb.append(" \"").append(desc).append("\"");
                        }
                        sb.append("\n");
                    }
                }
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String convertOutputs(ExtractedBlock block) {
        StringBuilder sb = new StringBuilder("outputs {\n");
        for (String line : block.content().split("\n")) {
            if (line.startsWith("|") && !line.contains("---")) {
                String[] cells = line.split("\\|");
                if (cells.length >= 4) {
                    String name = cells[1].trim();
                    String type = cells[2].trim();
                    String initial = cells[3].trim();
                    String desc = cells.length > 4 ? cells[4].trim() : "";
                    if (!name.isEmpty() && !name.equalsIgnoreCase("output name") && !name.equalsIgnoreCase("name")) {
                        sb.append("  ").append(name).append(": ").append(type);
                        if (!initial.isEmpty()) {
                            sb.append(" := ").append(initial);
                        }
                        if (!desc.isEmpty()) {
                            sb.append(" \"").append(desc).append("\"");
                        }
                        sb.append("\n");
                    }
                }
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String convertDataTable(ExtractedBlock block) {
        String id = block.id() != null ? block.id() : "unnamed_table";
        StringBuilder sb = new StringBuilder("data table ");
        sb.append(id).append(" {\n");
        for (String line : block.content().split("\n")) {
            if (line.startsWith("|")) {
                sb.append("  ").append(line).append("\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String convertDecisionTable(ExtractedBlock block) {
        String id = block.id() != null ? block.id() : "unnamed_decision";
        StringBuilder sb = new StringBuilder("decision table ");
        sb.append(id);
        if (block.description() != null && !block.description().isEmpty()) {
            sb.append(" \"").append(block.description()).append("\"");
        }
        sb.append(" {\n");
        for (String line : block.content().split("\n")) {
            if (line.startsWith("|")) {
                sb.append("  ").append(line).append("\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String convertRule(ExtractedBlock block) {
        StringBuilder sb = new StringBuilder();
        if (block.id() != null) {
            sb.append("rule ").append(block.id());
            if (block.description() != null && !block.description().isEmpty()) {
                sb.append(" \"").append(block.description()).append("\"");
            }
            sb.append("\n");
        }
        sb.append(block.content());
        if (!block.content().trim().endsWith("end")) {
            sb.append("\nend");
        }
        return sb.toString();
    }

    /// Represents an extracted block from the AsciiDoc document.
    private record ExtractedBlock(String type, String id, String description, String content) {
    }
}
