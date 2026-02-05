package work.spell.iskibal.asciidoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.model.*;
import work.spell.iskibal.parser.api.Parser;

class AsciiDocParserTest {

    private AsciiDocParser parser;

    @BeforeEach
    void setup() {
        // Use ServiceLoader via the Parser interface
        parser = new AsciiDocParser(Parser.load(), Locale.getDefault());
    }

    @AfterEach
    void teardown() {
        if (parser != null) {
            parser.close();
        }
    }

    @Test
    void parsesSimpleRule() {
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

        AsciiDocParser.ParseResult result = parser.parse(adoc);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.module().rules()).hasSize(1);

        Rule rule = result.module().rules().getFirst();
        assertThat(rule.id()).isEqualTo("RULE-001");
        assertThat(rule.description()).isEqualTo("Check balance");
    }

    @Test
    void parsesImports() {
        String adoc = """
                [.imports]
                ====
                Vehicle:: org.acme.Vehicle
                Person:: org.acme.Person
                ====
                """;

        AsciiDocParser.ParseResult result = parser.parse(adoc);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.module().imports()).hasSize(2);
        assertThat(result.module().imports().get(0).alias()).isEqualTo("Vehicle");
        assertThat(result.module().imports().get(0).type()).isEqualTo("org.acme.Vehicle");
    }

    @Test
    void parsesFactsTable() {
        String adoc = """
                [.facts]
                |===
                | Fact name | Type | Description

                | vehicle | Vehicle | The vehicle
                |===
                """;

        AsciiDocParser.ParseResult result = parser.parse(adoc);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.module().facts()).hasSize(1);
        assertThat(result.module().facts().get(0).name()).isEqualTo("vehicle");
    }

    @Test
    void parsesOutputsTable() {
        String adoc = """
                [.outputs]
                |===
                | Output name | Type | Initial value | Description

                | errors | String[] | [] | Error list
                |===
                """;

        AsciiDocParser.ParseResult result = parser.parse(adoc);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.module().outputs()).hasSize(1);
        assertThat(result.module().outputs().get(0).name()).isEqualTo("errors");
    }

    @Test
    void parsesDataTable() {
        String adoc = """
                [.data-table#vehicle-limits]
                |===
                | Type | Max Weight

                | Car | 1500
                | Truck | 5000
                |===
                """;

        AsciiDocParser.ParseResult result = parser.parse(adoc);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.module().dataTables()).hasSize(1);
        assertThat(result.module().dataTables().get(0).id()).isEqualTo("vehicle-limits");
    }

    @Test
    void parsesCompleteDocument() {
        String adoc = """
                = Rule Document

                [.imports]
                ====
                Vehicle:: org.acme.Vehicle
                ====

                [.facts]
                |===
                | Fact name | Type | Description

                | vehicle | Vehicle | The vehicle to check
                |===

                .RULE-001: Speed check
                [source,iskara,.rule]
                ----
                when:
                    vehicle.speed > 120
                then:
                    errors add: "Speeding"
                ----

                .RULE-002: Weight check
                [source,iskara,.rule]
                ----
                when:
                    vehicle.weight > 3500
                then:
                    errors add: "Overweight"
                ----
                """;

        AsciiDocParser.ParseResult result = parser.parse(adoc);

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.module();
        assertThat(module.imports()).hasSize(1);
        assertThat(module.facts()).hasSize(1);
        assertThat(module.rules()).hasSize(2);
    }

    @Test
    void handlesEmptyDocument() {
        String adoc = "";

        AsciiDocParser.ParseResult result = parser.parse(adoc);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.module().rules()).isEmpty();
    }

    @Test
    void handlesDocumentWithoutRules() {
        String adoc = """
                = Documentation

                This is just documentation without any rules.

                == Section

                Some more text.
                """;

        AsciiDocParser.ParseResult result = parser.parse(adoc);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.module().rules()).isEmpty();
    }

    @Test
    void parsesDecisionTableWithExternalizedConditions() {
        String adoc = """
                = Discount Rules

                [.decision-table#DISCOUNTS]
                .Customer discounts
                |===
                | ID 2+| WHEN | THEN

                | h| #hasBirthday h| age h| discount

                | BIRTHDAY | * | | 10
                | SENIOR | | >= 65 | 15
                |===

                [.aliases,for="DISCOUNTS"]
                hasBirthday::
                +
                [source,iskara]
                ----
                Customer.dateOfBirth = Today
                ----
                """;

        AsciiDocParser.ParseResult result = parser.parse(adoc);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.module().rules()).hasSize(1);

        Rule rule = result.module().rules().getFirst();
        assertThat(rule).isInstanceOf(Rule.DecisionTableRule.class);

        Rule.DecisionTableRule dtRule = (Rule.DecisionTableRule) rule;
        assertThat(dtRule.id()).isEqualTo("DISCOUNTS");
        assertThat(dtRule.description()).isEqualTo("Customer discounts");
        assertThat(dtRule.rows()).hasSize(2);
        assertThat(dtRule.aliases()).containsKey("hasBirthday");
    }
}
