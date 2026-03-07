package work.spell.iskibal.asciidoc;

import static org.assertj.core.api.Assertions.assertThat;

import module java.base;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IskaraSourceTranslatorTest {

    private static Asciidoctor asciidoctor;
    private static IskaraSourceTranslator translator;

    @BeforeAll
    static void setup() {
        asciidoctor = Asciidoctor.Factory.create();
        translator = new IskaraSourceTranslator();
    }

    @AfterAll
    static void teardown() {
        asciidoctor.close();
    }

    private IskaraSourceTranslator.TranslationResult translate(String adoc) {
        Document doc = asciidoctor.load(adoc, Options.builder().build());
        return translator.translate(doc);
    }

    @Test
    void translatesImportsFromDefinitionList() {
        String adoc = """
                [.imports]
                ====
                Vehicle:: org.acme.Vehicle
                Person:: org.acme.Person
                ====
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).contains("imports {");
        assertThat(result.source()).contains("Vehicle := org.acme.Vehicle");
        assertThat(result.source()).contains("Person := org.acme.Person");
    }

    @Test
    void translatesFactsTable() {
        String adoc = """
                [.facts]
                |===
                | Fact name | Type | Description

                | vehicle | Vehicle | The vehicle to check
                | driver | Person | The driver
                |===
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).contains("facts {");
        assertThat(result.source()).contains("vehicle: Vehicle \"The vehicle to check\"");
        assertThat(result.source()).contains("driver: Person \"The driver\"");
    }

    @Test
    void translatesGlobalsTable() {
        String adoc = """
                [.globals]
                |===
                | Name | Type | Description

                | config | Config | Global configuration
                |===
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).contains("globals {");
        assertThat(result.source()).contains("config: Config \"Global configuration\"");
    }

    @Test
    void translatesOutputsTable() {
        String adoc = """
                [.outputs]
                |===
                | Output name | Type | Initial value | Description

                | errors | String[] | [] | Validation errors
                | score | Integer | 0 | Calculated score
                |===
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).contains("outputs {");
        assertThat(result.source()).contains("errors: String[] := [] \"Validation errors\"");
        assertThat(result.source()).contains("score: Integer := 0 \"Calculated score\"");
    }

    @Test
    void translatesDataTable() {
        String adoc = """
                [.data-table#vehicle-limits]
                |===
                | Type | Max Weight

                | Car | 1500
                | Truck | 5000
                |===
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).contains("data table `vehicle-limits`");
        assertThat(result.source()).contains("| Type | Max Weight |");
        assertThat(result.source()).contains("| Car | 1500 |");
        assertThat(result.source()).contains("| Truck | 5000 |");
    }

    @Test
    void translatesRuleWithIdAndDescription() {
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

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).contains("rule `RULE-001` \"Check balance\"");
        assertThat(result.source()).contains("balance < 0");
        assertThat(result.source()).contains("errors add: \"Insufficient funds\"");
    }

    @Test
    void translatesRuleWithoutCaption() {
        String adoc = """
                [source,iskara,.rule]
                ----
                rule MY_RULE
                when:
                    x > 0
                then:
                    y := 1
                end
                ----
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).contains("rule MY_RULE");
    }

    @Test
    void translatesDecisionTableWithColspans() {
        String adoc = """
                [.decision-table#MULTI_COL]
                |===
                | ID 2+| WHEN 2+| THEN

                | h| age h| status h| discount h| message

                | YOUNG_ACTIVE | < 30 | "active" | 10 | "Youth discount"
                | SENIOR | >= 65 | * | 15 | "Senior discount"
                |===
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).contains("decision table MULTI_COL");
        assertThat(result.source()).contains("| ID | WHEN | WHEN | THEN | THEN |");
    }

    @Test
    void translatesDecisionTableWithRowspan() {
        String adoc = """
                [.decision-table#CUST_CAT]
                .Categorize customer
                |===
                | ID 2+| WHEN | THEN

                | h| customer.age h| customer.loyaltyPoints h| #setCategory
                | SENIOR_VIP .2+.^| >= 60 | >= 500  | "Senior VIP"
                | SENIOR                  | < 500   | "Senior"
                | LOYAL      .2+.^| < 60  | >= 1000 | "Loyal"
                | REGULAR                 | < 1000  | "Regular"
                |===

                [.aliases,for="CUST_CAT"]
                setCategory::
                +
                [source,iskara]
                ----
                [:param | customerCategory := param]
                ----
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        String source = result.source();
        assertThat(source).contains("decision table CUST_CAT \"Categorize customer\"");
        // Rowspan should duplicate the cell value
        assertThat(source).contains("| SENIOR_VIP | >= 60 | >= 500 | \"Senior VIP\" |");
        assertThat(source).contains("| SENIOR | >= 60 | < 500 | \"Senior\" |");
        assertThat(source).contains("| LOYAL | < 60 | >= 1000 | \"Loyal\" |");
        assertThat(source).contains("| REGULAR | < 60 | < 1000 | \"Regular\" |");
        // Alias where clause
        assertThat(source).contains("where setCategory := [:param | customerCategory := param]");
    }

    @Test
    void translatesDecisionTableWithWildcards() {
        String adoc = """
                [.decision-table#WILDCARDS]
                |===
                | ID | WHEN | THEN

                | h| condition h| action

                | ALWAYS | | "default"
                | MATCH_ALL | * | "always"
                |===
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).contains("| ALWAYS |  | \"default\" |");
        assertThat(result.source()).contains("| MATCH_ALL | * | \"always\" |");
    }

    @Test
    void decodesHtmlEntitiesInTableCells() {
        String adoc = """
                [.decision-table#COMPARISONS]
                |===
                | ID | WHEN | THEN

                | h| value h| result

                | LTE | <= 50 | "low"
                |===
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        // <= gets converted to ⇐ by AsciiDoc, must be decoded back
        assertThat(result.source()).contains("<= 50");
    }

    @Test
    void detectsDuplicateRoleAssignment() {
        String adoc = """
                [.facts.outputs,cols="1,1,3"]
                |===
                | Fact name | Type | Description

                | vehicle | Vehicle | The vehicle
                |===
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.message()).contains("Duplicate role assignment \"facts\", \"outputs\"");
        });
    }

    @Test
    void translatesEmptyDocument() {
        var result = translate("");

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).isBlank();
    }

    @Test
    void escapesQuotesInDescriptions() {
        String adoc = """
                [.facts]
                |===
                | Fact name | Type | Description

                | item | Item | The "special" item
                |===
                """;

        var result = translate(adoc);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.source()).contains("item: Item \"The \\\"special\\\" item\"");
    }
}
