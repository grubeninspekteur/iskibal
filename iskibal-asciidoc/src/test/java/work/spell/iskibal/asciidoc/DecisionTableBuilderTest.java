package work.spell.iskibal.asciidoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.Map;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Rule.DecisionTableRule;
import work.spell.iskibal.parser.api.Parser;

class DecisionTableBuilderTest {

    private static Asciidoctor asciidoctor;
    private static DecisionTableBuilder builder;
    private static ExpressionParser expressionParser;

    @BeforeAll
    static void setup() {
        asciidoctor = Asciidoctor.Factory.create();
        expressionParser = new ExpressionParser(Parser.load(), Locale.getDefault());
        builder = new DecisionTableBuilder(expressionParser);
    }

    @AfterAll
    static void teardown() {
        asciidoctor.close();
    }

    @Test
    void parsesSimpleDecisionTable() {
        String adoc = """
                [.decision-table#SIMPLE_DT]
                .Simple decision table
                |===
                | ID | WHEN | THEN

                | h| age h| category

                | MINOR | < 18 | "minor"
                | ADULT | >= 18 | "adult"
                |===
                """;

        DecisionTableRule rule = buildDecisionTable(adoc, "SIMPLE_DT");

        assertThat(rule.id()).isEqualTo("SIMPLE_DT");
        assertThat(rule.description()).isEqualTo("Simple decision table");
        assertThat(rule.rows()).hasSize(2);

        assertThat(rule.rows().get(0).id()).isEqualTo("MINOR");
        assertThat(rule.rows().get(1).id()).isEqualTo("ADULT");
    }

    @Test
    void parsesDecisionTableWithColspans() {
        String adoc = """
                [.decision-table#MULTI_COL]
                |===
                | ID 2+| WHEN 2+| THEN

                | h| age h| status h| discount h| message

                | YOUNG_ACTIVE | < 30 | "active" | 10 | "Youth discount"
                | SENIOR | >= 65 | * | 15 | "Senior discount"
                |===
                """;

        DecisionTableRule rule = buildDecisionTable(adoc, "MULTI_COL");

        assertThat(rule.id()).isEqualTo("MULTI_COL");
        assertThat(rule.rows()).hasSize(2);

        // First row should have 2 WHEN conditions and 2 THEN actions
        DecisionTableRule.Row row1 = rule.rows().get(0);
        assertThat(row1.id()).isEqualTo("YOUNG_ACTIVE");
        assertThat(row1.when()).hasSize(2);
        assertThat(row1.then()).hasSize(2);

        // Second row has wildcard for status, so only 1 WHEN condition
        DecisionTableRule.Row row2 = rule.rows().get(1);
        assertThat(row2.id()).isEqualTo("SENIOR");
        assertThat(row2.when()).hasSize(1); // status is wildcard
        assertThat(row2.then()).hasSize(2);
    }

    @Test
    void handlesEmptyCellsAsWildcards() {
        String adoc = """
                [.decision-table#WILDCARDS]
                |===
                | ID | WHEN | THEN

                | h| condition h| action

                | ALWAYS | | "default"
                | SPECIFIC | true | "matched"
                |===
                """;

        DecisionTableRule rule = buildDecisionTable(adoc, "WILDCARDS");

        assertThat(rule.rows()).hasSize(2);

        // Empty WHEN cell should result in no condition
        DecisionTableRule.Row alwaysRow = rule.rows().get(0);
        assertThat(alwaysRow.id()).isEqualTo("ALWAYS");
        assertThat(alwaysRow.when()).isEmpty();

        // Non-empty WHEN cell should have condition
        DecisionTableRule.Row specificRow = rule.rows().get(1);
        assertThat(specificRow.id()).isEqualTo("SPECIFIC");
        assertThat(specificRow.when()).hasSize(1);
    }

    @Test
    void handlesAsteriskAsWildcard() {
        String adoc = """
                [.decision-table#ASTERISK]
                |===
                | ID | WHEN | THEN

                | h| flag h| result

                | MATCH_ALL | * | "always"
                |===
                """;

        DecisionTableRule rule = buildDecisionTable(adoc, "ASTERISK");

        assertThat(rule.rows()).hasSize(1);
        DecisionTableRule.Row row = rule.rows().get(0);
        assertThat(row.when()).isEmpty(); // asterisk means "always matches"
    }

    @Test
    void requiresStructureAndExpressionRows() {
        // Table with only header row and no body rows is invalid
        String adoc = """
                [.decision-table#INVALID,cols="3"]
                |===
                | ID | WHEN | THEN
                |===
                """;

        assertThatThrownBy(() -> buildDecisionTable(adoc, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("structure row and an expression row");
    }

    @Test
    void parsesComparisonOperatorsInCells() {
        String adoc = """
                [.decision-table#COMPARISONS]
                |===
                | ID | WHEN | THEN

                | h| value h| result

                | GT | > 100 | "high"
                | LTE | <= 50 | "low"
                | NEQ | != 0 | "nonzero"
                |===
                """;

        DecisionTableRule rule = buildDecisionTable(adoc, "COMPARISONS");

        assertThat(rule.rows()).hasSize(3);
        // Each row should have a WHEN condition with the comparison
        for (DecisionTableRule.Row row : rule.rows()) {
            assertThat(row.when()).hasSize(1);
        }
    }

    @Test
    void parsesDecisionTableWithAliasReferences() {
        String adoc = """
                [.decision-table#BEST_OFFERS]
                .Show best offers
                |===
                | ID 2+| WHEN 2+| THEN

                | h| #hasBirthday h| Customer.age h| Discount add: "birthday" withPercent: h| #offerCar

                | BIRTHDAY_DISCOUNT | * | | 15 |
                | BD_CAR_OFFER | * | >= 18 | | "blue"
                |===

                [.aliases,for="BEST_OFFERS"]
                hasBirthday::
                +
                [source,iskara]
                ----
                Customer.dateOfBirth = Today
                ----
                offerCar::
                +
                [source,iskara]
                ----
                messages add: "Special offer!"
                ----
                """;

        Document doc = asciidoctor.load(adoc, Options.builder().build());

        // Resolve aliases first
        AliasResolver aliasResolver = new AliasResolver(expressionParser);
        Map<String, Expression.Block> aliases = aliasResolver.resolveAliases(doc, "BEST_OFFERS");

        // Build decision table with aliases
        Table table = findTable(doc);
        DecisionTableRule rule = builder.build(table, "BEST_OFFERS", table.getTitle(), aliases);

        assertThat(rule.id()).isEqualTo("BEST_OFFERS");
        assertThat(rule.description()).isEqualTo("Show best offers");
        assertThat(rule.rows()).hasSize(2);

        // Verify aliases were passed through
        assertThat(rule.aliases()).containsKeys("hasBirthday", "offerCar");
    }

    @Test
    void parsesDecisionTableWithQuotedAliasReferences() {
        // Tests alias names that need backtick quoting (contains spaces)
        String adoc = """
                [.decision-table#QUOTED_ALIASES]
                |===
                | ID | WHEN | THEN

                | h| #`has birthday` h| action

                | ROW1 | * | "done"
                |===

                [.aliases,for="QUOTED_ALIASES"]
                has birthday::
                +
                [source,iskara]
                ----
                person.birthday = today
                ----
                """;

        Document doc = asciidoctor.load(adoc, Options.builder().build());

        AliasResolver aliasResolver = new AliasResolver(expressionParser);
        Map<String, Expression.Block> aliases = aliasResolver.resolveAliases(doc, "QUOTED_ALIASES");

        Table table = findTable(doc);
        DecisionTableRule rule = builder.build(table, "QUOTED_ALIASES", null, aliases);

        assertThat(rule.rows()).hasSize(1);
        assertThat(rule.aliases()).containsKey("has birthday");
    }

    private DecisionTableRule buildDecisionTable(String adoc, String id) {
        Document doc = asciidoctor.load(adoc, Options.builder().build());
        Table table = findTable(doc);
        assertThat(table).isNotNull();
        return builder.build(table, id, table.getTitle(), Map.of());
    }

    private Table findTable(StructuralNode node) {
        for (StructuralNode child : node.getBlocks()) {
            if (child instanceof Table table) {
                return table;
            }
            Table found = findTable(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
