package work.spell.iskibal.compiler.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.compiler.common.api.AnalysisResult;
import work.spell.iskibal.compiler.common.api.SemanticAnalyzer;
import work.spell.iskibal.compiler.common.api.SemanticDiagnostic;
import work.spell.iskibal.compiler.common.internal.SemanticAnalyzerImpl;
import work.spell.iskibal.model.DataTable;
import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Expression.Assignment;
import work.spell.iskibal.model.Expression.Binary;
import work.spell.iskibal.model.Expression.Identifier;
import work.spell.iskibal.model.Expression.Literal;
import work.spell.iskibal.model.Fact;
import work.spell.iskibal.model.Global;
import work.spell.iskibal.model.Output;
import work.spell.iskibal.model.Rule;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.model.Statement;

class SemanticAnalyzerTest {

    private SemanticAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SemanticAnalyzerImpl();
    }

    @Nested
    @DisplayName("Declaration Validation")
    class DeclarationValidation {

        @Test
        @DisplayName("Valid module with no duplicates should succeed")
        void validModuleNoDuplicates() {
            var module = new RuleModule.Default(List.of(), List.of(new Fact.Definition("item", "Item", "An item")),
                    List.of(new Global.Definition("clock", "Clock", "System clock")),
                    List.of(new Output.Definition(
                            "discount", "BigDecimal", new Literal.NumberLiteral(BigDecimal.ZERO), "Discount amount")),
                    List.of(),
                    List.of(new Rule.SimpleRule("RULE1", "Test rule",
                            List.of(new Statement.ExpressionStatement(new Identifier("item"))),
                            List.of(new Statement.ExpressionStatement(new Assignment(new Identifier("discount"),
                                    new Literal.NumberLiteral(BigDecimal.TEN)))),
                            List.of())));

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Duplicate fact names should produce error")
        void duplicateFactNames() {
            var module = new RuleModule.Default(List.of(),
                    List.of(new Fact.Definition("item", "Item", "First item"),
                            new Fact.Definition("item", "Item", "Duplicate item")),
                    List.of(), List.of(), List.of(), List.of());

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors())
                    .anyMatch(d -> d.message().contains("Duplicate fact") && d.elementName().equals("item"));
        }

        @Test
        @DisplayName("Duplicate rule IDs should produce error")
        void duplicateRuleIds() {
            var module = new RuleModule.Default(List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(new Rule.SimpleRule("RULE1", "First", List.of(), List.of(), List.of()),
                            new Rule.SimpleRule("RULE1", "Duplicate", List.of(), List.of(), List.of())));

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors())
                    .anyMatch(d -> d.message().contains("Duplicate rule ID") && d.elementName().equals("RULE1"));
        }

        @Test
        @DisplayName("Duplicate data table IDs should produce error")
        void duplicateDataTableIds() {
            var module = new RuleModule.Default(List.of(), List.of(), List.of(), List.of(),
                    List.of(new DataTable.Default("TABLE1", List.of()), new DataTable.Default("TABLE1", List.of())),
                    List.of());

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors())
                    .anyMatch(d -> d.message().contains("Duplicate data table ID") && d.elementName().equals("TABLE1"));
        }
    }

    @Nested
    @DisplayName("Reference Validation")
    class ReferenceValidation {

        @Test
        @DisplayName("Valid identifier references should succeed")
        void validIdentifierReferences() {
            var module = new RuleModule.Default(List.of(), List.of(new Fact.Definition("item", "Item", "")), List.of(),
                    List.of(), List.of(), List.of(new Rule.SimpleRule("RULE1", "Test",
                            List.of(new Statement.ExpressionStatement(new Identifier("item"))), List.of(), List.of())));

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Undefined identifier should produce error")
        void undefinedIdentifier() {
            var module = new RuleModule.Default(List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(new Rule.SimpleRule("RULE1", "Test",
                            List.of(new Statement.ExpressionStatement(new Identifier("unknown"))), List.of(),
                            List.of())));

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors())
                    .anyMatch(d -> d.message().contains("Undefined identifier") && d.elementName().equals("unknown"));
        }

        @Test
        @DisplayName("Global accessed without @ prefix should produce error")
        void globalWithoutPrefix() {
            var module = new RuleModule.Default(List.of(), List.of(),
                    List.of(new Global.Definition("clock", "Clock", "")), List.of(), List.of(),
                    List.of(new Rule.SimpleRule("RULE1", "Test",
                            List.of(new Statement.ExpressionStatement(new Identifier("clock"))), List.of(),
                            List.of())));

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors()).anyMatch(d -> d.message().contains("must be accessed with @ prefix"));
        }

        @Test
        @DisplayName("Global accessed with @ prefix should succeed")
        void globalWithPrefix() {
            var module = new RuleModule.Default(List.of(), List.of(),
                    List.of(new Global.Definition("clock", "Clock", "")), List.of(), List.of(),
                    List.of(new Rule.SimpleRule("RULE1", "Test",
                            List.of(new Statement.ExpressionStatement(new Identifier("@clock"))), List.of(),
                            List.of())));

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Let variable should be accessible after declaration")
        void letVariableAccessible() {
            var module = new RuleModule.Default(
                    List.of(), List.of(), List.of(), List.of(), List.of(), List
                            .of(new Rule.SimpleRule("RULE1", "Test",
                                    List.of(new Statement.LetStatement("x", new Literal.NumberLiteral(BigDecimal.ONE)),
                                            new Statement.ExpressionStatement(new Identifier("x"))),
                                    List.of(), List.of())));

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Assignment Validation")
    class AssignmentValidation {

        @Test
        @DisplayName("Assigning to fact should produce error")
        void assignToFact() {
            var module = new RuleModule.Default(List.of(), List.of(new Fact.Definition("item", "Item", "")), List.of(),
                    List.of(), List.of(),
                    List.of(new Rule.SimpleRule("RULE1", "Test", List.of(),
                            List.of(new Statement.ExpressionStatement(
                                    new Assignment(new Identifier("item"), new Literal.NumberLiteral(BigDecimal.ONE)))),
                            List.of())));

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors()).anyMatch(d -> d.message().contains("Cannot assign to fact"));
        }

        @Test
        @DisplayName("Assigning to output in then section should succeed")
        void assignOutputInThen() {
            var module = new RuleModule.Default(List.of(), List.of(), List.of(), List.of(
                    new Output.Definition("discount", "BigDecimal", new Literal.NumberLiteral(BigDecimal.ZERO), "")),
                    List.of(),
                    List.of(new Rule.SimpleRule("RULE1", "Test", List.of(), List.of(new Statement.ExpressionStatement(
                            new Assignment(new Identifier("discount"), new Literal.NumberLiteral(BigDecimal.TEN)))),
                            List.of())));

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Assigning to output in when section should produce error")
        void assignOutputInWhen() {
            var module = new RuleModule.Default(List.of(), List.of(), List.of(), List.of(
                    new Output.Definition("discount", "BigDecimal", new Literal.NumberLiteral(BigDecimal.ZERO), "")),
                    List.of(),
                    List.of(new Rule.SimpleRule("RULE1", "Test", List.of(new Statement.ExpressionStatement(
                            new Assignment(new Identifier("discount"), new Literal.NumberLiteral(BigDecimal.TEN)))),
                            List.of(), List.of())));

            AnalysisResult result = analyzer.analyze(module);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors())
                    .anyMatch(d -> d.message().contains("Output can only be assigned in then/else section"));
        }
    }

    @Nested
    @DisplayName("Section Validation")
    class SectionValidation {

        @Test
        @DisplayName("Empty rule should produce warning")
        void emptyRuleWarning() {
            var module = new RuleModule.Default(List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(new Rule.SimpleRule("RULE1", "Empty rule", List.of(), List.of(), List.of())));

            AnalysisResult result = analyzer.analyze(module);

            // Should succeed but with warning
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDiagnostics()).anyMatch(d -> d.severity() == SemanticDiagnostic.Severity.WARNING
                    && d.message().contains("no when or then"));
        }
    }
}
