package work.spell.iskibal.parser.integration;

import module java.base;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import module iskibal.rule.model;
import work.spell.iskibal.model.Expression.Binary;
import work.spell.iskibal.model.Expression.Binary.Operator;
import work.spell.iskibal.model.Expression.Identifier;
import work.spell.iskibal.model.Expression.Literal.NumberLiteral;
import work.spell.iskibal.model.Expression.Literal.StringLiteral;
import work.spell.iskibal.model.Expression.MessageSend;
import work.spell.iskibal.model.Expression.MessageSend.KeywordMessage;
import work.spell.iskibal.model.Expression.MessageSend.KeywordMessage.KeywordPart;
import work.spell.iskibal.model.Expression.MessageSend.UnaryMessage;
import work.spell.iskibal.model.Expression.Navigation;
import work.spell.iskibal.model.Rule.SimpleRule;
import work.spell.iskibal.model.Statement.ExpressionStatement;
import work.spell.iskibal.parser.api.ParseResult;
import work.spell.iskibal.parser.api.SourceType;
import work.spell.iskibal.parser.internal.IskaraParserImpl;

import static org.assertj.core.api.Assertions.assertThat;

/// Integration tests for the full parsing pipeline. Verifies that source code is
/// correctly transformed into the AST model.
class FullParseTest {

    private final IskaraParserImpl parser = new IskaraParserImpl();

    @Nested
    class EmptyModule {
        @Test
        void parsesEmptySourceAsEmptyModule() {
            RuleModule module = parseSuccessfully("");

            assertThat(module.imports()).isEmpty();
            assertThat(module.facts()).isEmpty();
            assertThat(module.globals()).isEmpty();
            assertThat(module.outputs()).isEmpty();
            assertThat(module.dataTables()).isEmpty();
            assertThat(module.rules()).isEmpty();
        }
    }

    @Nested
    class Imports {
        @Test
        void parsesImportWithAliasAndType() {
            String input = """
                    imports {
                        Car := org.acme.Car
                    }
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.imports()).hasSize(1);
            assertThat(module.imports().get(0)).isEqualTo(new Import.Definition("Car", "org.acme.Car"));
        }

        @Test
        void parsesMultipleImports() {
            String input = """
                    imports {
                        Car := org.acme.Car
                        Passenger := org.acme.PersonImpl
                    }
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.imports()).containsExactly(new Import.Definition("Car", "org.acme.Car"),
                    new Import.Definition("Passenger", "org.acme.PersonImpl"));
        }
    }

    @Nested
    class Facts {
        @Test
        void parsesFactWithArrayType() {
            String input = """
                    facts {
                        Item: Item[]
                    }
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.facts()).hasSize(1);
            assertThat(module.facts().get(0)).isEqualTo(new Fact.Definition("Item", "Item[]", null));
        }

        @Test
        void parsesFactWithDescription() {
            String input = """
                    facts {
                        Customer: Customer "The current customer"
                    }
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.facts()).hasSize(1);
            assertThat(module.facts().get(0))
                    .isEqualTo(new Fact.Definition("Customer", "Customer", "The current customer"));
        }
    }

    @Nested
    class Globals {
        @Test
        void parsesGlobalWithQualifiedType() {
            String input = """
                    globals {
                        Clock: java.time.Clock
                    }
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.globals()).hasSize(1);
            assertThat(module.globals().get(0)).isEqualTo(new Global.Definition("Clock", "java.time.Clock", null));
        }
    }

    @Nested
    class Outputs {
        @Test
        void parsesOutputWithEmptyListInitialValue() {
            String input = """
                    outputs {
                        Errors: String[] := #()
                    }
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.outputs()).hasSize(1);
            Output output = module.outputs().get(0);
            assertThat(output.name()).isEqualTo("Errors");
            assertThat(output.type()).isEqualTo("String[]");
            assertThat(output.initialValue()).isInstanceOf(Expression.Literal.ListLiteral.class);
            assertThat(((Expression.Literal.ListLiteral) output.initialValue()).elements()).isEmpty();
        }

        @Test
        void parsesOutputWithoutInitialValue() {
            String input = """
                    outputs {
                        Valid: Boolean
                    }
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.outputs()).hasSize(1);
            assertThat(module.outputs().get(0)).isEqualTo(new Output.Definition("Valid", "Boolean", null, null));
        }
    }

    @Nested
    class SimpleRules {
        @Test
        void parsesRuleWithIdDescriptionWhenAndThen() {
            String input = """
                    rule WIG1 "Wiggly dolls are exempt"
                    when
                        Item.type = WigglyDoll
                    then
                        Discount := 0
                    end
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.rules()).hasSize(1);
            assertThat(module.rules().get(0)).isInstanceOf(SimpleRule.class);

            SimpleRule rule = (SimpleRule) module.rules().get(0);
            assertThat(rule.id()).isEqualTo("WIG1");
            assertThat(rule.description()).isEqualTo("Wiggly dolls are exempt");

            // Verify when clause contains binary comparison
            assertThat(rule.when()).hasSize(1);
            Statement.ExpressionStatement whenStmt = (Statement.ExpressionStatement) rule.when().get(0);
            assertThat(whenStmt.expression()).isInstanceOf(Expression.Binary.class);
            Expression.Binary whenExpr = (Expression.Binary) whenStmt.expression();
            assertThat(whenExpr.operator()).isEqualTo(Expression.Binary.Operator.EQUALS);

            // Verify then clause contains assignment
            assertThat(rule.then()).hasSize(1);
            Statement.ExpressionStatement thenStmt = (Statement.ExpressionStatement) rule.then().get(0);
            assertThat(thenStmt.expression()).isInstanceOf(Expression.Assignment.class);
            Expression.Assignment assignment = (Expression.Assignment) thenStmt.expression();
            assertThat(assignment.target()).isEqualTo(new Identifier("Discount"));
            assertThat(assignment.value()).isEqualTo(new Expression.Literal.NumberLiteral(BigDecimal.ZERO));
        }

        @Test
        void parsesConcatenatedAndInWhen() {
            String input = """
                    rule COMMA
                    when
                        Car.maker = 'ACME',
                        Car.color = 'blue'
                    then
                        Success := true
                    end
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.rules()).singleElement().asInstanceOf(InstanceOfAssertFactories.type(SimpleRule.class))
                    .extracting(SimpleRule::when)
                    .isEqualTo(List.of(new ExpressionStatement(new KeywordMessage(
                            new Expression.Binary(new Navigation(new Identifier("Car"), List.of("maker")),
                                    Operator.EQUALS, new StringLiteral("ACME")),
                            List.of(new KeywordPart("and",
                                    new Binary(new Navigation(new Identifier("Car"), List.of("color")), Operator.EQUALS,
                                            new StringLiteral("blue"))))))));
        }

        @Test
        void parsesRuleWithElseSection() {
            String input = """
                    rule AGE_CHECK "Check if customer is adult"
                    when
                        Customer.age >= 18
                    then
                        Category := "adult"
                    else
                        Category := "minor"
                    end
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.rules()).hasSize(1);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            assertThat(rule.id()).isEqualTo("AGE_CHECK");
            assertThat(rule.description()).isEqualTo("Check if customer is adult");

            // Verify when clause
            assertThat(rule.when()).hasSize(1);

            // Verify then clause
            assertThat(rule.then()).hasSize(1);
            Statement.ExpressionStatement thenStmt = (Statement.ExpressionStatement) rule.then().get(0);
            Expression.Assignment thenAssign = (Expression.Assignment) thenStmt.expression();
            assertThat(thenAssign.target()).isEqualTo(new Identifier("Category"));
            assertThat(thenAssign.value()).isEqualTo(new StringLiteral("adult"));

            // Verify else clause
            assertThat(rule.elseStatements()).hasSize(1);
            Statement.ExpressionStatement elseStmt = (Statement.ExpressionStatement) rule.elseStatements().get(0);
            Expression.Assignment elseAssign = (Expression.Assignment) elseStmt.expression();
            assertThat(elseAssign.target()).isEqualTo(new Identifier("Category"));
            assertThat(elseAssign.value()).isEqualTo(new StringLiteral("minor"));
        }

        @Test
        void parsesRuleWithMultipleElseStatements() {
            String input = """
                    rule MULTI_ELSE
                    when
                        Order.total > 100
                    then
                        Discount := 10
                    else
                        Discount := 0
                        Message := "No discount applied"
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            assertThat(rule.elseStatements()).hasSize(2);
        }

        @Test
        void parsesRuleWithoutElseSectionAsEmptyList() {
            String input = """
                    rule NO_ELSE
                    when
                        Item.available
                    then
                        Status := "ready"
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            assertThat(rule.elseStatements()).isEmpty();
        }
    }

    @Nested
    class LetStatements {
        @Test
        void parsesLetStatementWithNameAndExpression() {
            String input = """
                    rule TEST
                    when
                        let underage := person.age < 18
                        underage
                    then
                        Class := "minor"
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            assertThat(rule.when()).hasSize(2);
            assertThat(rule.when().get(0)).isInstanceOf(Statement.LetStatement.class);

            Statement.LetStatement letStmt = (Statement.LetStatement) rule.when().get(0);
            assertThat(letStmt.name()).isEqualTo("underage");
            assertThat(letStmt.expression()).isInstanceOf(Expression.Binary.class);
        }
    }

    @Nested
    class NavigationExpressions {
        @Test
        void parsesChainedNavigation() {
            String input = """
                    rule TEST
                    when
                        person.address.city = "Paris"
                    then
                        Valid := true
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.when().get(0);
            Expression.Binary binary = (Expression.Binary) stmt.expression();
            Expression.Navigation nav = (Expression.Navigation) binary.left();

            assertThat(nav.receiver()).isEqualTo(new Identifier("person"));
            assertThat(nav.names()).containsExactly("address", "city");

            assertThat(binary.right()).isEqualTo(new Expression.Literal.StringLiteral("Paris"));
        }
    }

    @Nested
    class MessageSendExpressions {
        @Test
        void parsesUnaryMessage() {
            String input = """
                    rule TEST
                    when
                        true
                    then
                        machine run
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.then().get(0);
            assertThat(stmt.expression()).isEqualTo(new UnaryMessage(new Identifier("machine"), "run"));
        }

        @Test
        void parsesKeywordMessageWithSinglePart() {
            String input = """
                    rule TEST
                    when
                        true
                    then
                        errors add: "error message"
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.then().get(0);
            KeywordMessage msg = (KeywordMessage) stmt.expression();

            assertThat(msg.receiver()).isEqualTo(new Identifier("errors"));
            assertThat(msg.parts()).hasSize(1);
            assertThat(msg.parts().get(0).keyword()).isEqualTo("add");
            assertThat(msg.parts().get(0).argument()).isEqualTo(new Expression.Literal.StringLiteral("error message"));
        }

        @Test
        void parsesKeywordMessageWithMultipleParts() {
            String input = """
                    rule TEST
                    when
                        true
                    then
                        list find: 5 cardsOfType: MAGICIAN
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            assertThat(rule.then()).singleElement()
                    .asInstanceOf(InstanceOfAssertFactories.type(ExpressionStatement.class))
                    .extracting(ExpressionStatement::expression)
                    .isEqualTo(new KeywordMessage(new Identifier("list"),
                            List.of(new KeywordPart("find", new NumberLiteral(BigDecimal.valueOf(5L))),
                                    new KeywordPart("cardsOfType", new Identifier("MAGICIAN")))));
        }
    }

    @Nested
    class BlockExpressions {
        @Test
        void parsesBlockWithParameter() {
            String input = """
                    rule TEST
                    when
                        items filter: [:item | item valid]
                    then
                        Valid := true
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.when().get(0);
            KeywordMessage msg = (KeywordMessage) stmt.expression();
            Expression.Block block = (Expression.Block) msg.parts().get(0).argument();

            assertThat(block.statements()).isNotEmpty();
        }

        @Test
        void parsesBlockWithoutParameter() {
            String input = """
                    rule TEST
                    when
                        items filter: [| valid] exists
                    then
                        Valid := true
                    end
                    """;

            RuleModule module = parseSuccessfully(input);

            assertThat(module.rules()).hasSize(1);
        }
    }

    @Nested
    class Literals {
        @Test
        void parsesListLiteral() {
            String input = """
                    rule TEST
                    when
                        items = #(1, 2, 3)
                    then
                        Valid := true
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.when().get(0);
            Expression.Binary binary = (Expression.Binary) stmt.expression();
            Expression.Literal.ListLiteral list = (Expression.Literal.ListLiteral) binary.right();

            assertThat(list.elements()).containsExactly(new Expression.Literal.NumberLiteral(new BigDecimal("1")),
                    new Expression.Literal.NumberLiteral(new BigDecimal("2")),
                    new Expression.Literal.NumberLiteral(new BigDecimal("3")));
        }

        @Test
        void parsesSetLiteral() {
            String input = """
                    rule TEST
                    when
                        items = #{1, 2, 3}
                    then
                        Valid := true
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.when().get(0);
            Expression.Binary binary = (Expression.Binary) stmt.expression();

            assertThat(binary.right()).isInstanceOf(Expression.Literal.SetLiteral.class);
        }

        @Test
        void parsesMapLiteral() {
            String input = """
                    rule TEST
                    when
                        map = #["a": 1, "b": 2]
                    then
                        Valid := true
                    end
                    """;

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.when().get(0);
            Expression.Binary binary = (Expression.Binary) stmt.expression();

            assertThat(binary.right()).isInstanceOf(Expression.Literal.MapLiteral.class);
            Expression.Literal.MapLiteral map = (Expression.Literal.MapLiteral) binary.right();
            assertThat(map.entries()).hasSize(2);
        }
    }

    @Nested
    class TemplateRules {
        @Test
        void parsesTemplateRuleWithDataTable() {
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

            RuleModule module = parseSuccessfully(input);

            assertThat(module.rules()).hasSize(1);
            assertThat(module.rules().get(0)).isInstanceOf(Rule.TemplateRule.class);

            Rule.TemplateRule templateRule = (Rule.TemplateRule) module.rules().get(0);
            assertThat(templateRule.id()).isEqualTo("BIRTHDAY");
            assertThat(templateRule.dataTable()).isNotNull();
            assertThat(templateRule.dataTable().rows()).hasSize(2);
        }
    }

    @Nested
    class ErrorHandling {
        @Test
        void reportsParseErrorWithLineAndColumn() {
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

            var diagnostic = result.getDiagnostics().get(0);
            assertThat(diagnostic.message()).contains("token recognition error");
            assertThat(diagnostic.location().line()).isEqualTo(3);
        }

        @Test
        void reportsMultipleSyntaxErrors() {
            String input = """
                    rule $$$ when @@@ then %%% end
                    """;

            ParseResult<RuleModule> result = parser.parse(input);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getDiagnostics()).hasSizeGreaterThan(1);
        }
    }

    @Nested
    class ParseOptions {
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

            work.spell.iskibal.parser.api.ParseOptions options = work.spell.iskibal.parser.api.ParseOptions.defaults()
                    .withLocale(Locale.US);
            ParseResult<RuleModule> result = parser.parse(input, options);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void supportsIskaraSourceType() {
            assertThat(parser.supportedSourceTypes()).contains(SourceType.ISKARA);
        }

        @Test
        void supportsAsciidocSourceType() {
            assertThat(parser.supportedSourceTypes()).contains(SourceType.ASCIIDOC);
        }
    }

    @Nested
    class SpecialSyntax {
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

            RuleModule module = parseSuccessfully(input);

            assertThat(module.rules()).hasSize(1);
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

            RuleModule module = parseSuccessfully(input);
            SimpleRule rule = (SimpleRule) module.rules().get(0);

            Statement.ExpressionStatement stmt = (Statement.ExpressionStatement) rule.when().get(0);
            Expression.Binary binary = (Expression.Binary) stmt.expression();

            assertThat(binary.left()).isEqualTo(new Identifier("Given Name"));
        }
    }

    private RuleModule parseSuccessfully(String input) {
        ParseResult<RuleModule> result = parser.parse(input);
        assertThat(result.isSuccess()).as("Parse should succeed but failed with: %s", result.getDiagnostics()).isTrue();
        return result.getValue().orElseThrow();
    }
}
