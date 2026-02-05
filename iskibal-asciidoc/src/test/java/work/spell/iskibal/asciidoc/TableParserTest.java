package work.spell.iskibal.asciidoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.model.*;
import work.spell.iskibal.parser.api.Parser;

class TableParserTest {

    private static Asciidoctor asciidoctor;
    private static TableParser parser;

    @BeforeAll
    static void setup() {
        asciidoctor = Asciidoctor.Factory.create();
        // Use ServiceLoader via the Parser interface
        parser = new TableParser(new ExpressionParser(Parser.load(), Locale.getDefault()));
    }

    @AfterAll
    static void teardown() {
        asciidoctor.close();
    }

    @Test
    void parsesFactsTable() {
        String adoc = """
                [.facts]
                |===
                | Fact name | Type | Description

                | vehicle | Vehicle | The vehicle to check
                | driver | Person | The driver
                |===
                """;

        List<Fact> facts = parser.parseFacts(findTable(adoc));

        assertThat(facts).hasSize(2);
        assertThat(facts.get(0).name()).isEqualTo("vehicle");
        assertThat(facts.get(0).type()).isEqualTo("Vehicle");
        assertThat(facts.get(0).description()).isEqualTo("The vehicle to check");
        assertThat(facts.get(1).name()).isEqualTo("driver");
        assertThat(facts.get(1).type()).isEqualTo("Person");
    }

    @Test
    void parsesGlobalsTable() {
        String adoc = """
                [.globals]
                |===
                | Name | Type | Description

                | config | Config | Global configuration
                |===
                """;

        List<Global> globals = parser.parseGlobals(findTable(adoc));

        assertThat(globals).hasSize(1);
        assertThat(globals.get(0).name()).isEqualTo("config");
        assertThat(globals.get(0).type()).isEqualTo("Config");
        assertThat(globals.get(0).description()).isEqualTo("Global configuration");
    }

    @Test
    void parsesOutputsTable() {
        String adoc = """
                [.outputs]
                |===
                | Output name | Type | Initial value | Description

                | errors | String[] | [] | Validation errors
                | score | Integer | 0 | Calculated score
                |===
                """;

        List<Output> outputs = parser.parseOutputs(findTable(adoc));

        assertThat(outputs).hasSize(2);
        assertThat(outputs.get(0).name()).isEqualTo("errors");
        assertThat(outputs.get(0).type()).isEqualTo("String[]");
        assertThat(outputs.get(0).initialValue()).isNotNull();
        assertThat(outputs.get(1).name()).isEqualTo("score");
        assertThat(outputs.get(1).type()).isEqualTo("Integer");
    }

    @Test
    void parsesDataTable() {
        String adoc = """
                [.data-table#limits]
                |===
                | Type | Maximum

                | Car | 1500
                | Truck | 5000
                |===
                """;

        DataTable table = parser.parseDataTable(findTable(adoc), "limits");

        assertThat(table.id()).isEqualTo("limits");
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(0).values()).containsKey("Type");
        assertThat(table.rows().get(0).values()).containsKey("Maximum");
    }

    private Table findTable(String adoc) {
        Document doc = asciidoctor.load(adoc, org.asciidoctor.Options.builder().build());
        return findFirstTable(doc);
    }

    private Table findFirstTable(StructuralNode node) {
        for (StructuralNode child : node.getBlocks()) {
            if (child instanceof Table table) {
                return table;
            }
            Table found = findFirstTable(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
