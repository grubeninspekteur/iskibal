package work.spell.iskibal.parser.integration;

import org.junit.jupiter.api.Test;
import work.spell.iskibal.model.*;
import work.spell.iskibal.parser.api.ParseOptions;
import work.spell.iskibal.parser.api.ParseResult;
import work.spell.iskibal.parser.internal.IskaraParserImpl;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the full parsing pipeline.
 */
class FullParseTest {

    private final IskaraParserImpl parser = new IskaraParserImpl();

    @Test
    void parsesEmptyModule() {
        ParseResult<RuleModule> result = parser.parse("");

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.getValue().orElseThrow();
        assertThat(module.imports()).isEmpty();
        assertThat(module.facts()).isEmpty();
        assertThat(module.globals()).isEmpty();
        assertThat(module.outputs()).isEmpty();
        assertThat(module.dataTables()).isEmpty();
        assertThat(module.rules()).isEmpty();
    }

    @Test
    void parsesImports() {
        String input = """
            imports {
                Car := org.acme.Car
                Passenger := org.acme.PersonImpl
            }
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.getValue().orElseThrow();
        assertThat(module.imports()).hasSize(2);

        Import carImport = module.imports().get(0);
        assertThat(carImport.alias()).isEqualTo("Car");
        assertThat(carImport.type()).isEqualTo("org.acme.Car");

        Import passengerImport = module.imports().get(1);
        assertThat(passengerImport.alias()).isEqualTo("Passenger");
        assertThat(passengerImport.type()).isEqualTo("org.acme.PersonImpl");
    }

    @Test
    void parsesFacts() {
        String input = """
            facts {
                Item: Item[]
                Customer: Customer "The current customer"
            }
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.getValue().orElseThrow();
        assertThat(module.facts()).hasSize(2);

        Fact itemFact = module.facts().get(0);
        assertThat(itemFact.name()).isEqualTo("Item");
        assertThat(itemFact.type()).isEqualTo("Item[]");

        Fact customerFact = module.facts().get(1);
        assertThat(customerFact.name()).isEqualTo("Customer");
        assertThat(customerFact.type()).isEqualTo("Customer");
        assertThat(customerFact.description()).isEqualTo("The current customer");
    }

    @Test
    void parsesGlobals() {
        String input = """
            globals {
                Clock: java.time.Clock
            }
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.getValue().orElseThrow();
        assertThat(module.globals()).hasSize(1);

        Global clock = module.globals().get(0);
        assertThat(clock.name()).isEqualTo("Clock");
        assertThat(clock.type()).isEqualTo("java.time.Clock");
    }

    @Test
    void parsesOutputs() {
        String input = """
            outputs {
                Errors: String[] := []
                Valid: Boolean
            }
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.getValue().orElseThrow();
        assertThat(module.outputs()).hasSize(2);

        Output errors = module.outputs().get(0);
        assertThat(errors.name()).isEqualTo("Errors");
        assertThat(errors.type()).isEqualTo("String[]");
        assertThat(errors.initialValue()).isNotNull();

        Output valid = module.outputs().get(1);
        assertThat(valid.name()).isEqualTo("Valid");
        assertThat(valid.type()).isEqualTo("Boolean");
    }

    @Test
    void parsesSimpleRule() {
        String input = """
            rule WIG1 "Wiggly dolls are exempt"
            when
                Item.type = WigglyDoll
            then
                Discount := 0
            end
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.getValue().orElseThrow();
        assertThat(module.rules()).hasSize(1);

        Rule rule = module.rules().get(0);
        assertThat(rule).isInstanceOf(Rule.SimpleRule.class);

        Rule.SimpleRule simpleRule = (Rule.SimpleRule) rule;
        assertThat(simpleRule.id()).isEqualTo("WIG1");
        assertThat(simpleRule.description()).isEqualTo("Wiggly dolls are exempt");
        assertThat(simpleRule.when()).isNotEmpty();
        assertThat(simpleRule.then()).isNotEmpty();
    }

    @Test
    void parsesLetStatement() {
        String input = """
            rule TEST
            when
                let underage := person.age < 18
                underage
            then
                Class := "minor"
            end
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.getValue().orElseThrow();
        Rule.SimpleRule rule = (Rule.SimpleRule) module.rules().get(0);

        assertThat(rule.when()).hasSize(2);
        assertThat(rule.when().get(0)).isInstanceOf(Statement.LetStatement.class);

        Statement.LetStatement letStmt = (Statement.LetStatement) rule.when().get(0);
        assertThat(letStmt.name()).isEqualTo("underage");
    }

    @Test
    void parsesNavigationExpression() {
        String input = """
            rule TEST
            when
                person.address.city = "Paris"
            then
                Valid := true
            end
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.getValue().orElseThrow();
        Rule.SimpleRule rule = (Rule.SimpleRule) module.rules().get(0);

        Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.when().get(0);
        assertThat(stmt.expression()).isInstanceOf(Expression.Binary.class);

        Expression.Binary binary = (Expression.Binary) stmt.expression();
        assertThat(binary.left()).isInstanceOf(Expression.Navigation.class);

        Expression.Navigation nav = (Expression.Navigation) binary.left();
        assertThat(nav.names()).hasSize(2);
        assertThat(nav.names().get(0)).isEqualTo("address");
        assertThat(nav.names().get(1)).isEqualTo("city");
    }

    @Test
    void parsesMessageSend() {
        String input = """
            rule TEST
            when
                true
            then
                errors add: "error message"
            end
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.getValue().orElseThrow();
        Rule.SimpleRule rule = (Rule.SimpleRule) module.rules().get(0);

        Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.then().get(0);
        assertThat(stmt.expression()).isInstanceOf(Expression.MessageSend.class);

        Expression.MessageSend msg = (Expression.MessageSend) stmt.expression();
        assertThat(msg.parts()).hasSize(1);
        assertThat(msg.parts().get(0).name()).isEqualTo("add");
    }

    @Test
    void parsesTemplateRule() {
        String input = """
            template rule BIRTHDAY
            data table {
                | Name    |
                | ------- |
                | "Emma"  |
                | "Paul"  |
            }
            when
                Customer.name = Name
            then
                Discount := 15
            end
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        RuleModule module = result.getValue().orElseThrow();

        Rule rule = module.rules().get(0);
        assertThat(rule).isInstanceOf(Rule.TemplateRule.class);

        Rule.TemplateRule templateRule = (Rule.TemplateRule) rule;
        assertThat(templateRule.id()).isEqualTo("BIRTHDAY");
        assertThat(templateRule.dataTable()).isNotNull();
    }

    @Test
    void parsesLiterals() {
        String input = """
            rule TEST
            when
                a = 42,
                b = 3.14,
                c = "hello",
                d = true,
                e = false,
                f = null
            then
                Valid := true
            end
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void parsesCollectionLiterals() {
        String input = """
            rule TEST
            when
                list = [1, 2, 3],
                map = ["a": 1, "b": 2],
                set = {1, 2, 3}
            then
                Valid := true
            end
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void reportsParseErrors() {
        String input = """
            rule INVALID
            when
                $$$invalid syntax$$$
            then
                x := 1
            end
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDiagnostics()).isNotEmpty();
    }

    @Test
    void parsesWithLocale() {
        String input = """
            rule TEST
            when
                price > 10.50
            then
                Valid := true
            end
            """;

        ParseOptions options = ParseOptions.defaults().withLocale(Locale.US);
        ParseResult<RuleModule> result = parser.parse(input, options);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void parsesGlobalReference() {
        String input = """
            rule TEST
            when
                @Clock now isBefore: deadline
            then
                Valid := true
            end
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void parsesQuotedIdentifier() {
        String input = """
            rule TEST
            when
                `Given Name` = "Emma"
            then
                Valid := true
            end
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void parsesBlockExpression() {
        // First test: simple keyword message (should work)
        String input1 = """
            rule TEST
            when
                true
            then
                items add: "test"
            end
            """;

        ParseResult<RuleModule> result1 = parser.parse(input1);
        assertThat(result1.isSuccess())
                .as("Simple keyword message failed: %s", result1.getDiagnostics())
                .isTrue();

        // Second test: keyword message with block
        // Note: 'where' is a keyword, so we use 'filter' instead
        String input2 = """
            rule TEST
            when
                items filter: [| valid] exists
            then
                Valid := true
            end
            """;

        ParseResult<RuleModule> result2 = parser.parse(input2);
        assertThat(result2.isSuccess())
                .as("Block expression failed: %s", result2.getDiagnostics())
                .isTrue();
    }
}
