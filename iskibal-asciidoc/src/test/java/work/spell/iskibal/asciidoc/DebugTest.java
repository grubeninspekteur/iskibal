package work.spell.iskibal.asciidoc;

import java.util.Locale;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.Row;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.model.Expression;
import work.spell.iskibal.parser.api.Parser;

class DebugTest {

    @Test
    void debugDocumentStructure() {
        String adoc = """
                .RULE-001: Check balance
                [source,iskara,.rule]
                ----
                when:
                    balance < 0
                then:
                    errors add: "Insufficient funds"
                ----
                """;

        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            Document doc = asciidoctor.load(adoc, Options.builder().build());
            printStructure(doc, 0);

            // Try to parse as rule
            for (StructuralNode block : doc.getBlocks()) {
                if (block instanceof Block listing) {
                    String source = listing.getSource();
                    System.out.println("Source: [" + source + "]");
                    String title = listing.getTitle();
                    System.out.println("Title: " + title);

                    // Build the full rule source
                    String id = null;
                    String desc = null;
                    if (title != null) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Z0-9_-]+):\\s*(.*)$").matcher(title.trim());
                        if (m.matches()) {
                            id = m.group(1);
                            desc = m.group(2);
                        }
                    }
                    System.out.println("Parsed ID: " + id);
                    System.out.println("Parsed desc: " + desc);

                    StringBuilder sb = new StringBuilder();
                    if (id != null) {
                        sb.append("rule ");
                        // Quote ID with backticks if it contains non-identifier chars
                        if (id.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                            sb.append(id);
                        } else {
                            sb.append("`").append(id).append("`");
                        }
                        if (desc != null && !desc.isBlank()) {
                            sb.append(" \"").append(desc).append("\"");
                        }
                        sb.append("\n");
                    }
                    sb.append(source);
                    if (!source.trim().endsWith("end")) {
                        sb.append("\nend");
                    }
                    System.out.println("Full source:\n" + sb);

                    // Try parsing with the Iskara parser
                    var parser = work.spell.iskibal.parser.api.Parser.load();
                    var result = parser.parse(sb.toString());
                    System.out.println("Parse success: " + result.isSuccess());
                    System.out.println("Diagnostics: " + result.getDiagnostics());
                    if (result.isSuccess() && result.getValue().isPresent()) {
                        var module = result.getValue().get();
                        System.out.println("Rules count: " + module.rules().size());
                        for (var rule : module.rules()) {
                            System.out.println("  Rule: " + rule.id() + " - " + rule.description());
                        }
                    }
                }
            }
        }
    }

    @Test
    void debugImportsStructure() {
        String adoc = """
                [.imports]
                ====
                Vehicle:: org.acme.Vehicle
                Person:: org.acme.Person
                ====
                """;

        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            Document doc = asciidoctor.load(adoc, Options.builder().build());
            printStructure(doc, 0);
        }
    }

    @Test
    void debugTableStructure() {
        String adoc = """
                [#limits,.data-table]
                |===
                | Type | Maximum

                | Car | 1500
                | Truck | 5000
                |===
                """;

        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            Document doc = asciidoctor.load(adoc, Options.builder().build());
            printStructure(doc, 0);

            for (StructuralNode block : doc.getBlocks()) {
                if (block instanceof Table table) {
                    System.out.println("Table ID: " + table.getId());
                    System.out.println("Header rows: " + table.getHeader().size());
                    System.out.println("Body rows: " + table.getBody().size());
                    System.out.println("Footer rows: " + table.getFooter().size());

                    for (Row row : table.getHeader()) {
                        System.out.println("Header row cells: " + row.getCells().size());
                        for (Cell cell : row.getCells()) {
                            System.out.println("  Header cell: [" + cell.getText() + "]");
                        }
                    }

                    for (Row row : table.getBody()) {
                        System.out.println("Body row cells: " + row.getCells().size());
                        for (Cell cell : row.getCells()) {
                            System.out.println("  Body cell: [" + cell.getText() + "]");
                        }
                    }
                }
            }
        }
    }

    @Test
    void debugExpressionParsing() {
        ExpressionParser exprParser = new ExpressionParser(Parser.load(), Locale.getDefault());

        String[] testValues = {"Car", "1500", "[]", "\"hello\"", "0"};

        for (String value : testValues) {
            Expression expr = exprParser.parseExpression(value);
            System.out.println("Value: [" + value + "] -> " + (expr != null ? expr.getClass().getSimpleName() : "null"));
            if (expr == null) {
                var result = exprParser.tryParseExpression(value);
                System.out.println("  Diagnostics: " + result.getDiagnostics());
            }
        }
    }

    @Test
    void debugDecisionTableParsing() {
        String adoc = """
                [.decision-table#COMPARISONS]
                |===
                | ID | WHEN | THEN

                | h| value h| result

                | GT | > 100 | "high"
                | LTE | <= 50 | "low"
                |===
                """;

        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            Document doc = asciidoctor.load(adoc, Options.builder().build());
            for (StructuralNode block : doc.getBlocks()) {
                if (block instanceof Table table) {
                    System.out.println("Header rows: " + table.getHeader().size());
                    System.out.println("Body rows: " + table.getBody().size());

                    for (int r = 0; r < table.getHeader().size(); r++) {
                        Row row = table.getHeader().get(r);
                        System.out.println("Header row " + r + ": " + row.getCells().size() + " cells");
                        for (Cell cell : row.getCells()) {
                            System.out.println("  [" + cell.getText() + "] colspan=" + cell.getColspan());
                        }
                    }

                    for (int r = 0; r < table.getBody().size(); r++) {
                        Row row = table.getBody().get(r);
                        System.out.println("Body row " + r + ":");
                        for (Cell cell : row.getCells()) {
                            System.out.println("  [" + cell.getText() + "]");
                        }
                    }
                }
            }
        }
    }

    @Test
    void debugDecisionTableHeaders() {
        // Test different header row syntaxes
        String[] variants = {
            // Variant 1: Two rows before blank line
            """
            |===
            | ID | WHEN | THEN
            | h| age h| category

            | MINOR | < 18 | "minor"
            |===
            """,
            // Variant 2: Using header option
            """
            [%header,cols="3"]
            |===
            | ID | WHEN | THEN
            | h| age h| category
            | MINOR | < 18 | "minor"
            |===
            """,
            // Variant 3: Specification example style
            """
            |===
            | ID 2+| WHEN 2+| THEN

            | h| #hasBirthday h| age h| discount h| offer
            | ROW1 | * | >= 18 | 10 | "yes"
            |===
            """
        };

        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            for (int i = 0; i < variants.length; i++) {
                System.out.println("=== Variant " + (i+1) + " ===");
                Document doc = asciidoctor.load(variants[i], Options.builder().build());
                for (StructuralNode block : doc.getBlocks()) {
                    if (block instanceof Table table) {
                        System.out.println("Header rows: " + table.getHeader().size());
                        System.out.println("Body rows: " + table.getBody().size());
                        for (int r = 0; r < table.getHeader().size(); r++) {
                            Row row = table.getHeader().get(r);
                            System.out.println("  Header row " + r + ": " + row.getCells().size() + " cells");
                            for (Cell cell : row.getCells()) {
                                System.out.println("    [" + cell.getText() + "]");
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void debugDataTableRoles() {
        // Try different syntax variations
        String[] variants = {
            """
            [#vehicle-limits,.data-table]
            |===
            | Type | Max Weight

            | Car | 1500
            |===
            """,
            """
            [#vehicle-limits,role=data-table]
            |===
            | Type | Max Weight

            | Car | 1500
            |===
            """,
            """
            [.data-table#vehicle-limits]
            |===
            | Type | Max Weight

            | Car | 1500
            |===
            """
        };

        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            for (int i = 0; i < variants.length; i++) {
                System.out.println("=== Variant " + (i+1) + " ===");
                Document doc = asciidoctor.load(variants[i], Options.builder().build());
                for (StructuralNode block : doc.getBlocks()) {
                    System.out.println("Block type: " + block.getClass().getSimpleName());
                    System.out.println("Block roles: " + block.getRoles());
                    System.out.println("Block id: " + block.getId());
                    System.out.println("Block context: " + block.getContext());
                }
            }
        }
    }

    private void printStructure(StructuralNode node, int indent) {
        String prefix = "  ".repeat(indent);
        System.out.println(prefix + node.getClass().getSimpleName() +
                " context=" + node.getContext() +
                " roles=" + node.getRoles() +
                " id=" + node.getId());

        if (node instanceof Block block) {
            System.out.println(prefix + "  style=" + block.getStyle());
            System.out.println(prefix + "  language=" + block.getAttribute("language"));
            System.out.println(prefix + "  title=" + block.getTitle());
            System.out.println(prefix + "  caption=" + block.getCaption());
        }

        for (StructuralNode child : node.getBlocks()) {
            printStructure(child, indent + 1);
        }
    }
}
