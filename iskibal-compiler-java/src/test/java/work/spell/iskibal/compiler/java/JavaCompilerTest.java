package work.spell.iskibal.compiler.java;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.compiler.java.api.CompilationResult;
import work.spell.iskibal.compiler.java.api.JavaCompiler;
import work.spell.iskibal.compiler.java.api.JavaCompilerOptions;
import work.spell.iskibal.compiler.java.internal.JavaCompilerImpl;
import work.spell.iskibal.model.DataTable;
import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Expression.Assignment;
import work.spell.iskibal.model.Expression.Binary;
import work.spell.iskibal.model.Expression.Identifier;
import work.spell.iskibal.model.Expression.Literal;
import work.spell.iskibal.model.Expression.Navigation;
import work.spell.iskibal.model.Fact;
import work.spell.iskibal.model.Global;
import work.spell.iskibal.model.Output;
import work.spell.iskibal.model.Rule;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.model.Statement;

class JavaCompilerTest {

    private JavaCompiler compiler;
    private JavaCompilerOptions options;

    @BeforeEach
    void setUp() {
        compiler = new JavaCompilerImpl();
        options = JavaCompilerOptions.of("com.example.rules", "GeneratedRules", true);
    }

    @Nested
    @DisplayName("Simple Rule Compilation")
    class SimpleRuleCompilation {

        @Test
        @DisplayName("Compiles simple rule with when/then sections")
        void compilesSimpleRule() {
            var module = new RuleModule.Default(
                    List.of(), List.of(new Fact.Definition("item", "Item",
                            "An item")),
                    List.of(),
                    List.of(new Output.Definition(
                            "discount", "BigDecimal", new Literal.NumberLiteral(BigDecimal.ZERO), "Discount")),
                    List.of(),
                    List.of(new Rule.SimpleRule("WIG1", "Wiggly doll discount",
                            List.of(new Statement.ExpressionStatement(
                                    new Binary(new Navigation(new Identifier("item"), List.of("type")),
                                            Binary.Operator.EQUALS, new Literal.StringLiteral("WigglyDoll")))),
                            List.of(new Statement.ExpressionStatement(new Assignment(new Identifier("discount"),
                                    new Literal.NumberLiteral(new BigDecimal("10"))))),
                            List.of())));

            CompilationResult result = compiler.compile(module, options);

            assertThat(result.isSuccess()).isTrue();
            String source = result.getSourceFiles().orElseThrow().get("com/example/rules/GeneratedRules.java");
            assertThat(source).contains("package com.example.rules;");
            assertThat(source).contains("public class GeneratedRules");
            assertThat(source).contains("private final Item item;");
            assertThat(source).contains("private BigDecimal discount;");
            assertThat(source).contains("private void rule_WIG1()");
            assertThat(source).contains("equalsNumericAware(item.getType(), \"WigglyDoll\")");
        }

        @Test
        @DisplayName("Compiles rule with else section")
        void compilesRuleWithElse() {
            var module = new RuleModule.Default(List.of(), List.of(new Fact.Definition("item", "Item", "")), List.of(),
                    List.of(new Output.Definition("result", "String", new Literal.StringLiteral("default"), "")),
                    List.of(),
                    List.of(new Rule.SimpleRule("R1", "Rule with else",
                            List.of(new Statement.ExpressionStatement(new Binary(new Identifier("item"),
                                    Binary.Operator.NOT_EQUALS, new Literal.NullLiteral()))),
                            List.of(new Statement.ExpressionStatement(
                                    new Assignment(new Identifier("result"), new Literal.StringLiteral("found")))),
                            List.of(new Statement.ExpressionStatement(new Assignment(new Identifier("result"),
                                    new Literal.StringLiteral("not found")))))));

            CompilationResult result = compiler.compile(module, options);

            assertThat(result.isSuccess()).isTrue();
            String source = result.getSourceFiles().orElseThrow().values().iterator().next();
            assertThat(source).contains("} else {");
            assertThat(source).contains("\"not found\"");
        }
    }

    @Nested
    @DisplayName("Expression Generation")
    class ExpressionGeneration {

        @Test
        @DisplayName("Generates BigDecimal arithmetic operations")
        void generatesBigDecimalArithmetic() {
            var module = new RuleModule.Default(List.of(), List.of(), List.of(), List
                    .of(new Output.Definition("result", "BigDecimal", new Literal.NumberLiteral(BigDecimal.ZERO), "")),
                    List.of(),
                    List.of(new Rule.SimpleRule("CALC", "Calculate", List.of(),
                            List.of(new Statement.ExpressionStatement(new Assignment(new Identifier("result"),
                                    new Binary(new Literal.NumberLiteral(BigDecimal.TEN), Binary.Operator.PLUS,
                                            new Literal.NumberLiteral(new BigDecimal("5")))))),
                            List.of())));

            CompilationResult result = compiler.compile(module, options);

            assertThat(result.isSuccess()).isTrue();
            String source = result.getSourceFiles().orElseThrow().values().iterator().next();
            assertThat(source).contains("addNumeric(");
        }

        @Test
        @DisplayName("Generates global access with this prefix")
        void generatesGlobalAccess() {
            var module = new RuleModule.Default(List.of(), List.of(),
                    List.of(new Global.Definition("clock", "Clock", "")),
                    List.of(new Output.Definition("result", "String", new Literal.StringLiteral(""), "")), List.of(),
                    List.of(new Rule.SimpleRule("R1", "",
                            List.of(new Statement.ExpressionStatement(new Binary(new Identifier("@clock"),
                                    Binary.Operator.NOT_EQUALS, new Literal.NullLiteral()))),
                            List.of(new Statement.ExpressionStatement(
                                    new Assignment(new Identifier("result"), new Literal.StringLiteral("ok")))),
                            List.of())));

            CompilationResult result = compiler.compile(module, options);

            assertThat(result.isSuccess()).isTrue();
            String source = result.getSourceFiles().orElseThrow().values().iterator().next();
            assertThat(source).contains("this.clock");
        }

        @Test
        @DisplayName("Generates navigation chain with null-safe getters")
        void generatesNavigationChain() {
            var module = new RuleModule.Default(List.of(), List.of(new Fact.Definition("order", "Order", "")),
                    List.of(),
                    List.of(new Output.Definition("amount", "BigDecimal", new Literal.NumberLiteral(BigDecimal.ZERO),
                            "")),
                    List.of(),
                    List.of(new Rule.SimpleRule("R1", "", List.of(),
                            List.of(new Statement.ExpressionStatement(new Assignment(new Identifier("amount"),
                                    new Navigation(new Identifier("order"), List.of("customer", "balance"))))),
                            List.of())));

            CompilationResult result = compiler.compile(module, options);

            assertThat(result.isSuccess()).isTrue();
            String source = result.getSourceFiles().orElseThrow().values().iterator().next();
            // With generateNullChecks=true, navigation chains use Optional for null safety
            assertThat(source).contains("java.util.Optional.ofNullable(order)");
            assertThat(source).contains(".map(v -> v.getCustomer())");
            assertThat(source).contains(".map(v -> v.getBalance())");
            assertThat(source).contains(".orElse(null)");
        }
    }

    @Nested
    @DisplayName("Template Rule Compilation")
    class TemplateRuleCompilation {

        @Test
        @DisplayName("Generates method per data table row")
        void generatesMethodPerRow() {
            var table = new DataTable.Default("rates",
                    List.of(new DataTable.Row(Map.of("category", new Literal.StringLiteral("A"), "rate",
                            new Literal.NumberLiteral(new BigDecimal("0.1")))),
                            new DataTable.Row(Map.of("category", new Literal.StringLiteral("B"), "rate",
                                    new Literal.NumberLiteral(new BigDecimal("0.2"))))));

            var module = new RuleModule.Default(List.of(), List.of(new Fact.Definition("item", "Item", "")), List.of(),
                    List.of(new Output.Definition("discount", "BigDecimal", new Literal.NumberLiteral(BigDecimal.ZERO),
                            "")),
                    List.of(table),
                    List.of(new Rule.TemplateRule("RATE", "Apply rate", table,
                            List.of(new Statement.ExpressionStatement(
                                    new Binary(new Navigation(new Identifier("item"), List.of("category")),
                                            Binary.Operator.EQUALS, new Identifier("category")))),
                            List.of(new Statement.ExpressionStatement(
                                    new Assignment(new Identifier("discount"), new Identifier("rate")))))));

            CompilationResult result = compiler.compile(module, options);

            assertThat(result.isSuccess()).isTrue();
            String source = result.getSourceFiles().orElseThrow().values().iterator().next();
            assertThat(source).contains("rule_RATE_0()");
            assertThat(source).contains("rule_RATE_1()");
            assertThat(source).contains("var category = \"A\"");
            assertThat(source).contains("var category = \"B\"");
        }
    }

    @Nested
    @DisplayName("Compilation Failures")
    class CompilationFailures {

        @Test
        @DisplayName("Fails on duplicate facts")
        void failsOnDuplicateFacts() {
            var module = new RuleModule.Default(List.of(),
                    List.of(new Fact.Definition("item", "Item", ""), new Fact.Definition("item", "Item", "")),
                    List.of(), List.of(), List.of(), List.of());

            CompilationResult result = compiler.compile(module, options);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("Duplicate"));
        }

        @Test
        @DisplayName("Fails on undefined identifier")
        void failsOnUndefinedIdentifier() {
            var module = new RuleModule.Default(List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(new Rule.SimpleRule("R1", "",
                            List.of(new Statement.ExpressionStatement(new Identifier("unknown"))), List.of(),
                            List.of())));

            CompilationResult result = compiler.compile(module, options);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("Undefined"));
        }
    }

    @Nested
    @DisplayName("Output Generation")
    class OutputGeneration {

        @Test
        @DisplayName("Generates output getters")
        void generatesOutputGetters() {
            var module = new RuleModule.Default(List.of(), List.of(), List.of(),
                    List.of(new Output.Definition("discount", "BigDecimal", new Literal.NumberLiteral(BigDecimal.ZERO),
                            ""), new Output.Definition("message", "String", new Literal.StringLiteral(""), "")),
                    List.of(), List.of());

            CompilationResult result = compiler.compile(module, options);

            assertThat(result.isSuccess()).isTrue();
            String source = result.getSourceFiles().orElseThrow().values().iterator().next();
            assertThat(source).contains("public BigDecimal getDiscount()");
            assertThat(source).contains("public String getMessage()");
        }

        @Test
        @DisplayName("Generates evaluate method calling all rules")
        void generatesEvaluateMethod() {
            var module = new RuleModule.Default(List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(new Rule.SimpleRule("R1", "", List.of(), List.of(), List.of()),
                            new Rule.SimpleRule("R2", "", List.of(), List.of(), List.of())));

            CompilationResult result = compiler.compile(module, options);

            assertThat(result.isSuccess()).isTrue();
            String source = result.getSourceFiles().orElseThrow().values().iterator().next();
            assertThat(source).contains("public void evaluate()");
            assertThat(source).contains("rule_R1();");
            assertThat(source).contains("rule_R2();");
        }
    }
}
