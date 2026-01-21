package work.spell.iskibal.parser.integration;

import org.junit.jupiter.api.Test;
import work.spell.iskibal.model.*;
import work.spell.iskibal.parser.api.ParseOptions;
import work.spell.iskibal.parser.api.ParseResult;
import work.spell.iskibal.parser.internal.IskaraParserImpl;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the full parsing pipeline.
 */
class FullParseTest {

    private final IskaraParserImpl parser = new IskaraParserImpl();

    @Test
    void parsesEmptyModule() {
        ParseResult<RuleModule> result = parser.parse("");

        assertTrue(result.isSuccess());
        RuleModule module = result.getValue().orElseThrow();
        assertTrue(module.imports().isEmpty());
        assertTrue(module.facts().isEmpty());
        assertTrue(module.globals().isEmpty());
        assertTrue(module.outputs().isEmpty());
        assertTrue(module.dataTables().isEmpty());
        assertTrue(module.rules().isEmpty());
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

        assertTrue(result.isSuccess());
        RuleModule module = result.getValue().orElseThrow();
        assertEquals(2, module.imports().size());

        Import carImport = module.imports().get(0);
        assertEquals("Car", carImport.alias());
        assertEquals("org.acme.Car", carImport.type());

        Import passengerImport = module.imports().get(1);
        assertEquals("Passenger", passengerImport.alias());
        assertEquals("org.acme.PersonImpl", passengerImport.type());
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

        assertTrue(result.isSuccess());
        RuleModule module = result.getValue().orElseThrow();
        assertEquals(2, module.facts().size());

        Fact itemFact = module.facts().get(0);
        assertEquals("Item", itemFact.name());
        assertEquals("Item[]", itemFact.type());

        Fact customerFact = module.facts().get(1);
        assertEquals("Customer", customerFact.name());
        assertEquals("Customer", customerFact.type());
        assertEquals("The current customer", customerFact.description());
    }

    @Test
    void parsesGlobals() {
        String input = """
            globals {
                Clock: java.time.Clock
            }
            """;

        ParseResult<RuleModule> result = parser.parse(input);

        assertTrue(result.isSuccess());
        RuleModule module = result.getValue().orElseThrow();
        assertEquals(1, module.globals().size());

        Global clock = module.globals().get(0);
        assertEquals("Clock", clock.name());
        assertEquals("java.time.Clock", clock.type());
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

        assertTrue(result.isSuccess());
        RuleModule module = result.getValue().orElseThrow();
        assertEquals(2, module.outputs().size());

        Output errors = module.outputs().get(0);
        assertEquals("Errors", errors.name());
        assertEquals("String[]", errors.type());
        assertNotNull(errors.initialValue());

        Output valid = module.outputs().get(1);
        assertEquals("Valid", valid.name());
        assertEquals("Boolean", valid.type());
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

        assertTrue(result.isSuccess());
        RuleModule module = result.getValue().orElseThrow();
        assertEquals(1, module.rules().size());

        Rule rule = module.rules().get(0);
        assertInstanceOf(Rule.SimpleRule.class, rule);

        Rule.SimpleRule simpleRule = (Rule.SimpleRule) rule;
        assertEquals("WIG1", simpleRule.id());
        assertEquals("Wiggly dolls are exempt", simpleRule.description());
        assertFalse(simpleRule.when().isEmpty());
        assertFalse(simpleRule.then().isEmpty());
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

        assertTrue(result.isSuccess());
        RuleModule module = result.getValue().orElseThrow();
        Rule.SimpleRule rule = (Rule.SimpleRule) module.rules().get(0);

        assertEquals(2, rule.when().size());
        assertInstanceOf(Statement.LetStatement.class, rule.when().get(0));

        Statement.LetStatement letStmt = (Statement.LetStatement) rule.when().get(0);
        assertEquals("underage", letStmt.name());
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

        assertTrue(result.isSuccess());
        RuleModule module = result.getValue().orElseThrow();
        Rule.SimpleRule rule = (Rule.SimpleRule) module.rules().get(0);

        Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.when().get(0);
        assertInstanceOf(Expression.Binary.class, stmt.expression());

        Expression.Binary binary = (Expression.Binary) stmt.expression();
        assertInstanceOf(Expression.Navigation.class, binary.left());

        Expression.Navigation nav = (Expression.Navigation) binary.left();
        assertEquals(2, nav.names().size());
        assertEquals("address", nav.names().get(0));
        assertEquals("city", nav.names().get(1));
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

        assertTrue(result.isSuccess());
        RuleModule module = result.getValue().orElseThrow();
        Rule.SimpleRule rule = (Rule.SimpleRule) module.rules().get(0);

        Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.then().get(0);
        assertInstanceOf(Expression.MessageSend.class, stmt.expression());

        Expression.MessageSend msg = (Expression.MessageSend) stmt.expression();
        assertEquals(1, msg.parts().size());
        assertEquals("add", msg.parts().get(0).name());
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

        assertTrue(result.isSuccess());
        RuleModule module = result.getValue().orElseThrow();

        Rule rule = module.rules().get(0);
        assertInstanceOf(Rule.TemplateRule.class, rule);

        Rule.TemplateRule templateRule = (Rule.TemplateRule) rule;
        assertEquals("BIRTHDAY", templateRule.id());
        assertNotNull(templateRule.dataTable());
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

        assertTrue(result.isSuccess());
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

        assertTrue(result.isSuccess());
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

        assertFalse(result.isSuccess());
        assertFalse(result.getDiagnostics().isEmpty());
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

        assertTrue(result.isSuccess());
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

        assertTrue(result.isSuccess());
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

        assertTrue(result.isSuccess());
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
        if (!result1.isSuccess()) {
            System.err.println("Test 1 errors:");
            result1.getDiagnostics().forEach(d -> System.err.println("  " + d));
        }
        assertTrue(result1.isSuccess(), "Simple keyword message failed: " + result1.getDiagnostics());

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

        if (!result2.isSuccess()) {
            System.err.println("Test 2 errors:");
            result2.getDiagnostics().forEach(d -> System.err.println("  " + d));
        }
        assertTrue(result2.isSuccess(), "Block expression failed: " + result2.getDiagnostics());
    }
}
