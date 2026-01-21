package work.spell.iskibal.parser.grammar;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import work.spell.iskibal.parser.IskaraLexer;
import work.spell.iskibal.parser.IskaraParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Iskara parser grammar.
 */
class ParserTest {

    @Test
    void parsesEmptyModule() {
        IskaraParser parser = createParser("");
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertNull(ctx.moduleHeader());
        assertTrue(ctx.preamble().isEmpty());
        assertTrue(ctx.ruleDefinition().isEmpty());
    }

    @Test
    void parsesModuleHeader() {
        IskaraParser parser = createParser("module MyRules");
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertNotNull(ctx.moduleHeader());
        assertEquals("MyRules", ctx.moduleHeader().IDENTIFIER().getText());
    }

    @Test
    void parsesModuleHeaderWithString() {
        IskaraParser parser = createParser("module \"Black Friday Discounts\"");
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertNotNull(ctx.moduleHeader());
        assertEquals("\"Black Friday Discounts\"", ctx.moduleHeader().STRING().getText());
    }

    @Test
    void parsesImportSection() {
        String input = """
            imports {
                Car := org.acme.Car
                Passenger := org.acme.PersonImpl
            }
            """;
        IskaraParser parser = createParser(input);
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertEquals(1, ctx.preamble().size());
        assertNotNull(ctx.preamble(0).importSection());
        assertEquals(2, ctx.preamble(0).importSection().importDecl().size());
    }

    @Test
    void parsesFactSection() {
        String input = """
            facts {
                Item: Item[]
                Customer: Customer "The current customer"
            }
            """;
        IskaraParser parser = createParser(input);
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertEquals(1, ctx.preamble().size());
        assertNotNull(ctx.preamble(0).factSection());
        assertEquals(2, ctx.preamble(0).factSection().factDecl().size());
    }

    @Test
    void parsesGlobalSection() {
        String input = """
            globals {
                Clock: java.time.Clock
            }
            """;
        IskaraParser parser = createParser(input);
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertNotNull(ctx.preamble(0).globalSection());
    }

    @Test
    void parsesOutputSection() {
        String input = """
            outputs {
                Errors: String[] := []
                Result: Boolean
            }
            """;
        IskaraParser parser = createParser(input);
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertNotNull(ctx.preamble(0).outputSection());
        assertEquals(2, ctx.preamble(0).outputSection().outputDecl().size());
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
        IskaraParser parser = createParser(input);
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertEquals(1, ctx.ruleDefinition().size());
        assertNotNull(ctx.ruleDefinition(0).simpleRule());

        var rule = ctx.ruleDefinition(0).simpleRule();
        assertEquals("WIG1", rule.identifier().getText());
        assertEquals("\"Wiggly dolls are exempt\"", rule.STRING().getText());
    }

    @Test
    void parsesRuleWithElse() {
        String input = """
            rule UNDER
            when
                Person.age < 18
            then
                Class := "underage"
            else
                Class := "adult"
            end
            """;
        IskaraParser parser = createParser(input);
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        var rule = ctx.ruleDefinition(0).simpleRule();
        assertNotNull(rule.elseSection());
    }

    @Test
    void parsesTemplateRule() {
        String input = """
            template rule HAPPY_BIRTHDAY
            data table {
                | Given Name | Family Name |
                | ---------- | ----------- |
                | "Emma"     | "Perkins"   |
            }
            when
                Customer.givenName = `Given Name`
            then
                Discounts add: "Birthday"
            end
            """;
        IskaraParser parser = createParser(input);
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertNotNull(ctx.ruleDefinition(0).templateRule());
    }

    @Test
    void parsesDataTable() {
        String input = """
            data table WeightLimits {
                | Vehicle type | Weight limit |
                | ------------ | ------------ |
                | "bicycle"    | 10           |
                | "car"        | 1000         |
            }
            """;
        IskaraParser parser = createParser(input);
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertNotNull(ctx.preamble(0).dataTableDef());
    }

    @Test
    void parsesExpressions() {
        // Test various expression forms
        assertExpressionParses("42");
        assertExpressionParses("3.14");
        assertExpressionParses("true");
        assertExpressionParses("false");
        assertExpressionParses("null");
        assertExpressionParses("\"hello\"");
        assertExpressionParses("foo");
        assertExpressionParses("foo.bar");
        assertExpressionParses("foo.bar.baz");
        assertExpressionParses("1 + 2");
        assertExpressionParses("1 - 2");
        assertExpressionParses("1 * 2");
        assertExpressionParses("1 / 2");
        assertExpressionParses("a = b");
        assertExpressionParses("a ~= b");
        assertExpressionParses("a > b");
        assertExpressionParses("a < b");
        assertExpressionParses("a >= b");
        assertExpressionParses("a <= b");
        assertExpressionParses("a := b");
        assertExpressionParses("-x");
        assertExpressionParses("(a + b)");
        assertExpressionParses("[]");
        assertExpressionParses("[1, 2, 3]");
        assertExpressionParses("[:]");
        assertExpressionParses("[\"a\": 1]");
        assertExpressionParses("{}");
        assertExpressionParses("{1, 2, 3}");
        assertExpressionParses("{1..10}");
    }

    @Test
    void parsesMessageSendExpressions() {
        assertExpressionParses("list size");
        assertExpressionParses("list add: item");
        assertExpressionParses("list find: 5 cardsOfType: MAGICIAN");
        assertExpressionParses("randomString! startsWith: \"A\"");
    }

    @Test
    void parsesBlockExpressions() {
        assertExpressionParses("[:item | item discard]");
        assertExpressionParses("[| discard]");
        assertExpressionParses("{x + 1}");
    }

    @Test
    void parsesGlobalReferences() {
        assertExpressionParses("@clock");
        assertExpressionParses("@Clock now");
    }

    @Test
    void parsesCommaExpression() {
        String input = """
            rule TEST
            when
                a = 1,
                b = 2
            then
                c := 3
            end
            """;
        IskaraParser parser = createParser(input);
        IskaraParser.RuleModuleContext ctx = parser.ruleModule();

        assertEquals(0, parser.getNumberOfSyntaxErrors());
    }

    private void assertExpressionParses(String expr) {
        IskaraParser parser = createParser(expr);
        parser.expression();
        assertEquals(0, parser.getNumberOfSyntaxErrors(),
                "Expression should parse without errors: " + expr);
    }

    private IskaraParser createParser(String input) {
        IskaraLexer lexer = new IskaraLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        return new IskaraParser(tokens);
    }
}
