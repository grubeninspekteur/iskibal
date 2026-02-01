package work.spell.iskibal.parser.grammar;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import work.spell.iskibal.parser.IskaraLexer;
import work.spell.iskibal.parser.IskaraParser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Iskara parser grammar. Verifies that the parser correctly
 * constructs parse trees from source code.
 */
class ParserTest {

    @Nested
    class ModuleStructure {
        @Test
        void parsesEmptyModule() {
            var ctx = parseModule("");

            assertThat(ctx.moduleHeader()).isNull();
            assertThat(ctx.preamble()).isEmpty();
            assertThat(ctx.ruleDefinition()).isEmpty();
        }

        @Test
        void parsesModuleHeaderWithIdentifier() {
            var ctx = parseModule("module MyRules");

            assertThat(ctx.moduleHeader()).isNotNull();
            assertThat(ctx.moduleHeader().IDENTIFIER().getText()).isEqualTo("MyRules");
            assertThat(ctx.moduleHeader().STRING()).isNull();
        }

        @Test
        void parsesModuleHeaderWithString() {
            var ctx = parseModule("module \"Black Friday Discounts\"");

            assertThat(ctx.moduleHeader()).isNotNull();
            assertThat(ctx.moduleHeader().STRING().getText()).isEqualTo("\"Black Friday Discounts\"");
            assertThat(ctx.moduleHeader().IDENTIFIER()).isNull();
        }
    }

    @Nested
    class ImportSection {
        @Test
        void parsesSingleImport() {
            String input = """
                    imports {
                        Car := org.acme.Car
                    }
                    """;
            var ctx = parseModule(input);
            var imports = ctx.preamble(0).importSection();

            assertThat(imports.importDecl()).hasSize(1);

            var decl = imports.importDecl(0);
            assertThat(decl.identifier().getText()).isEqualTo("Car");
            assertThat(decl.qualifiedName().getText()).isEqualTo("org.acme.Car");
        }

        @Test
        void parsesMultipleImports() {
            String input = """
                    imports {
                        Car := org.acme.Car
                        Passenger := org.acme.PersonImpl
                    }
                    """;
            var ctx = parseModule(input);
            var imports = ctx.preamble(0).importSection();

            assertThat(imports.importDecl()).hasSize(2);

            assertThat(imports.importDecl(0).identifier().getText()).isEqualTo("Car");
            assertThat(imports.importDecl(0).qualifiedName().getText()).isEqualTo("org.acme.Car");

            assertThat(imports.importDecl(1).identifier().getText()).isEqualTo("Passenger");
            assertThat(imports.importDecl(1).qualifiedName().getText()).isEqualTo("org.acme.PersonImpl");
        }
    }

    @Nested
    class FactSection {
        @Test
        void parsesFactWithArrayType() {
            String input = """
                    facts {
                        Item: Item[]
                    }
                    """;
            var ctx = parseModule(input);
            var facts = ctx.preamble(0).factSection();

            assertThat(facts.factDecl()).hasSize(1);

            var decl = facts.factDecl(0);
            assertThat(decl.identifier().getText()).isEqualTo("Item");
            assertThat(decl.typeRef().getText()).isEqualTo("Item[]");
            assertThat(decl.STRING()).isNull();
        }

        @Test
        void parsesFactWithDescription() {
            String input = """
                    facts {
                        Customer: Customer "The current customer"
                    }
                    """;
            var ctx = parseModule(input);
            var decl = ctx.preamble(0).factSection().factDecl(0);

            assertThat(decl.identifier().getText()).isEqualTo("Customer");
            assertThat(decl.typeRef().getText()).isEqualTo("Customer");
            assertThat(decl.STRING().getText()).isEqualTo("\"The current customer\"");
        }
    }

    @Nested
    class GlobalSection {
        @Test
        void parsesGlobalWithQualifiedType() {
            String input = """
                    globals {
                        Clock: java.time.Clock
                    }
                    """;
            var ctx = parseModule(input);
            var globals = ctx.preamble(0).globalSection();

            assertThat(globals.globalDecl()).hasSize(1);

            var decl = globals.globalDecl(0);
            assertThat(decl.identifier().getText()).isEqualTo("Clock");
            assertThat(decl.typeRef().getText()).isEqualTo("java.time.Clock");
        }
    }

    @Nested
    class OutputSection {
        @Test
        void parsesOutputWithInitialValue() {
            String input = """
                    outputs {
                        Errors: String[] := []
                    }
                    """;
            var ctx = parseModule(input);
            var decl = ctx.preamble(0).outputSection().outputDecl(0);

            assertThat(decl.identifier().getText()).isEqualTo("Errors");
            assertThat(decl.typeRef().getText()).isEqualTo("String[]");
            assertThat(decl.expression()).isNotNull();
        }

        @Test
        void parsesOutputWithoutInitialValue() {
            String input = """
                    outputs {
                        Result: Boolean
                    }
                    """;
            var ctx = parseModule(input);
            var decl = ctx.preamble(0).outputSection().outputDecl(0);

            assertThat(decl.identifier().getText()).isEqualTo("Result");
            assertThat(decl.typeRef().getText()).isEqualTo("Boolean");
            assertThat(decl.expression()).isNull();
        }
    }

    @Nested
    class SimpleRules {
        @Test
        void parsesRuleWithIdAndDescription() {
            String input = """
                    rule WIG1 "Wiggly dolls are exempt"
                    when
                        Item.type = WigglyDoll
                    then
                        Discount := 0
                    end
                    """;
            var ctx = parseModule(input);
            var rule = ctx.ruleDefinition(0).simpleRule();

            assertThat(rule.identifier().getText()).isEqualTo("WIG1");
            assertThat(rule.STRING().getText()).isEqualTo("\"Wiggly dolls are exempt\"");
            assertThat(rule.whenSection()).isNotNull();
            assertThat(rule.thenSection()).isNotNull();
            assertThat(rule.elseSection()).isNull();
        }

        @Test
        void parsesRuleWithElseSection() {
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
            var ctx = parseModule(input);
            var rule = ctx.ruleDefinition(0).simpleRule();

            assertThat(rule.identifier().getText()).isEqualTo("UNDER");
            assertThat(rule.STRING()).isNull();
            assertThat(rule.elseSection()).isNotNull();
            assertThat(rule.elseSection().statementList()).isNotNull();
        }

        @Test
        void parsesRuleWithLocalDataTable() {
            String input = """
                    rule CALC
                    data table Rates {
                        | Rate |
                        | ---- |
                        | 10   |
                    }
                    when
                        total > 100
                    then
                        Discount := Rate
                    end
                    """;
            var ctx = parseModule(input);
            var rule = ctx.ruleDefinition(0).simpleRule();

            assertThat(rule.localDataTable()).isNotNull();
            assertThat(rule.localDataTable().identifier().getText()).isEqualTo("Rates");
        }
    }

    @Nested
    class TemplateRules {
        @Test
        void parsesTemplateRuleWithDataTable() {
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
            var ctx = parseModule(input);
            var rule = ctx.ruleDefinition(0).templateRule();

            assertThat(rule.identifier().getText()).isEqualTo("HAPPY_BIRTHDAY");
            assertThat(rule.anonymousDataTable()).isNotNull();
            assertThat(rule.anonymousDataTable().tableContent().tableRow()).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    class DataTables {
        @Test
        void parsesNamedDataTable() {
            String input = """
                    data table WeightLimits {
                        | Vehicle type | Weight limit |
                        | ------------ | ------------ |
                        | "bicycle"    | 10           |
                        | "car"        | 1000         |
                    }
                    """;
            var ctx = parseModule(input);
            var table = ctx.preamble(0).dataTableDef();

            assertThat(table.identifier().getText()).isEqualTo("WeightLimits");
            assertThat(table.tableContent().tableRow()).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    class Expressions {
        @Test
        void parsesLiterals() {
            assertExpressionParses("42");
            assertExpressionParses("3.14");
            assertExpressionParses("true");
            assertExpressionParses("false");
            assertExpressionParses("null");
            assertExpressionParses("\"hello\"");
        }

        @Test
        void parsesNavigationExpression() {
            assertExpressionParses("foo.bar");
            assertExpressionParses("foo.bar.baz");
        }

        @Test
        void parsesBinaryOperators() {
            assertExpressionParses("1 + 2");
            assertExpressionParses("1 - 2");
            assertExpressionParses("1 * 2");
            assertExpressionParses("1 / 2");
        }

        @Test
        void parsesComparisonOperators() {
            assertExpressionParses("a = b");
            assertExpressionParses("a ~= b");
            assertExpressionParses("a > b");
            assertExpressionParses("a < b");
            assertExpressionParses("a >= b");
            assertExpressionParses("a <= b");
        }

        @Test
        void parsesAssignment() {
            assertExpressionParses("a := b");
        }

        @Test
        void parsesUnaryMinus() {
            assertExpressionParses("-x");
        }

        @Test
        void parsesParenthesizedExpression() {
            assertExpressionParses("(a + b)");
        }

        @Test
        void parsesCollectionLiterals() {
            assertExpressionParses("[]");
            assertExpressionParses("[1, 2, 3]");
            assertExpressionParses("[:]");
            assertExpressionParses("[\"a\": 1]");
            assertExpressionParses("{}");
            assertExpressionParses("{1, 2, 3}");
            assertExpressionParses("{1..10}");
        }

        @Test
        void parsesUnaryMessageSend() {
            assertExpressionParses("list size");
        }

        @Test
        void parsesKeywordMessageSend() {
            assertExpressionParses("list add: item");
            assertExpressionParses("list find: 5 cardsOfType: MAGICIAN");
        }

        @Test
        void parsesMessageSendWithBang() {
            assertExpressionParses("randomString! startsWith: \"A\"");
        }

        @Test
        void parsesBlockWithParameter() {
            assertExpressionParses("[:item | item discard]");
        }

        @Test
        void parsesBlockWithoutParameter() {
            assertExpressionParses("[| discard]");
        }

        @Test
        void parsesSetBuilder() {
            assertExpressionParses("{x + 1}");
        }

        @Test
        void parsesGlobalReference() {
            assertExpressionParses("@clock");
            assertExpressionParses("@Clock now");
        }
    }

    @Nested
    class CommaExpressions {
        @Test
        void parsesCommaExpressionInWhenClause() {
            String input = """
                    rule TEST
                    when
                        a = 1,
                        b = 2
                    then
                        c := 3
                    end
                    """;
            var ctx = parseModule(input);
            var rule = ctx.ruleDefinition(0).simpleRule();

            assertThat(rule.whenSection().statementList().statement()).isNotEmpty();
        }
    }

    private IskaraParser.RuleModuleContext parseModule(String input) {
        IskaraParser parser = createParser(input);
        var ctx = parser.ruleModule();
        assertThat(parser.getNumberOfSyntaxErrors()).as("Parse errors in: %s", input).isZero();
        return ctx;
    }

    private void assertExpressionParses(String expr) {
        IskaraParser parser = createParser(expr);
        parser.expression();
        assertThat(parser.getNumberOfSyntaxErrors()).as("Expression should parse without errors: %s", expr).isZero();
    }

    private IskaraParser createParser(String input) {
        IskaraLexer lexer = new IskaraLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        return new IskaraParser(tokens);
    }
}
