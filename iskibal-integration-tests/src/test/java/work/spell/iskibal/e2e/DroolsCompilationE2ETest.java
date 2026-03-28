package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import module java.base;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.asciidoc.AsciiDocParser;
import work.spell.iskibal.compiler.drools.api.DroolsCompilationResult;
import work.spell.iskibal.compiler.drools.api.DroolsCompiler;
import work.spell.iskibal.compiler.drools.api.DroolsCompilerOptions;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.parser.api.Parser;

/// End-to-end tests for Drools Rule Language (DRL) compilation.
///
/// These tests verify that Iskara rule sources (both plain `.iskara` and
/// AsciiDoc `.adoc` documents) are correctly compiled to Drools DRL text.
/// They validate the structure and content of the generated DRL without
/// requiring a Drools runtime.
///
/// Coverage:
/// - Simple rules with when/then
/// - Simple rules with when/then/else (produces a negated companion rule)
/// - Template rules (expanded to one DRL rule per row)
/// - Decision table rules (expanded to one DRL rule per row)
/// - Globals → DRL `global` declarations
/// - Outputs → `__outputs` global + companion POJO generation
/// - AsciiDoc document parsing followed by DRL compilation
@DisplayName("Drools DRL compilation")
class DroolsCompilationE2ETest {

    private static final Path RULES_DIR = Path.of("src/test/resources/e2e/drools");
    private static AsciiDocParser sharedParser;

    @BeforeAll
    static void setupParser() {
        System.setProperty("jruby.compile.mode", "JIT");
        sharedParser = new AsciiDocParser(Locale.US);
    }

    @AfterAll
    static void teardownParser() {
        if (sharedParser != null) {
            sharedParser.close();
        }
    }

    // ---- helpers ----

    private static DroolsCompilationResult compileDrl(String iskaraSource) {
        return compileDrl(iskaraSource, DroolsCompilerOptions.defaults());
    }

    private static DroolsCompilationResult compileDrl(String iskaraSource, DroolsCompilerOptions options) {
        Parser parser = Parser.load();
        var parseResult = parser.parse(iskaraSource);
        assertThat(parseResult.isSuccess()).as("parse should succeed; errors: %s", parseResult.getDiagnostics())
                .isTrue();
        RuleModule module = parseResult.getValue().orElseThrow();

        DroolsCompiler compiler = DroolsCompiler.load();
        return compiler.compile(module, options);
    }

    private static DroolsCompilationResult compileAdocFile(String fileName) {
        return compileAdocFile(fileName, DroolsCompilerOptions.defaults());
    }

    private static DroolsCompilationResult compileAdocFile(String fileName, DroolsCompilerOptions options) {
        Path file = RULES_DIR.resolve(fileName);
        AsciiDocParser.ParseResult parseResult = sharedParser.parseFile(file);
        assertThat(parseResult.isSuccess()).as("adoc parse should succeed for " + fileName).isTrue();
        RuleModule module = parseResult.module();

        DroolsCompiler compiler = DroolsCompiler.load();
        return compiler.compile(module, options);
    }

    // ---- tests ----

    @Nested
    @DisplayName("Simple rules")
    class SimpleRules {

        @Test
        @DisplayName("when/then rule generates a DRL rule block")
        void whenThenRule() {
            String source = """
                    facts {
                        label: String
                    }
                    outputs {
                        result: String := "none"
                    }
                    rule `set-label`
                    when
                        label = "hello"
                    then
                        result := "world"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();

            assertThat(drl).contains("rule \"set-label\"");
            assertThat(drl).contains("when");
            assertThat(drl).contains("then");
            assertThat(drl).contains("end");
        }

        @Test
        @DisplayName("when/then/else generates two DRL rules (positive + negated)")
        void whenThenElseGeneratesTwoRules() {
            String source = """
                    facts {
                        status: String
                    }
                    outputs {
                        result: String := "no"
                    }
                    rule `check-status`
                    when
                        status = "active"
                    then
                        result := "yes"
                    else
                        result := "no"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();

            // Positive rule
            assertThat(drl).contains("rule \"check-status\"");
            // Negated companion rule
            assertThat(drl).contains("rule \"check-status-else\"");
            // Both rules have eval
            assertThat(drl).contains("eval(");
            // Negated rule contains negated eval
            assertThat(drl).contains("eval(!(");
        }

        @Test
        @DisplayName("rule description is emitted as @Description annotation")
        void ruleDescriptionEmitted() {
            String source = """
                    facts {
                        x: String
                    }
                    outputs {
                        y: String := ""
                    }
                    rule `my-rule` "This is a description"
                    when
                        x = "a"
                    then
                        y := "b"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();
            assertThat(drl).contains("@Description(\"This is a description\")");
        }
    }

    @Nested
    @DisplayName("Fact bindings")
    class FactBindings {

        @Test
        @DisplayName("each declared fact is bound as a DRL pattern variable")
        void factsAreBoundAsPatternVariables() {
            String source = """
                    facts {
                        order: work.spell.iskibal.e2e.Order
                        customer: work.spell.iskibal.e2e.Customer
                    }
                    outputs {
                        status: String := ""
                    }
                    rule check
                    when
                        customer.age >= 18
                    then
                        status := "ok"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();

            assertThat(drl).contains("$order : work.spell.iskibal.e2e.Order()");
            assertThat(drl).contains("$customer : work.spell.iskibal.e2e.Customer()");
        }

        @Test
        @DisplayName("navigation through facts uses getter chain in eval")
        void navigationUsesGetterChainInEval() {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.CustomerProfile
                    }
                    outputs {
                        result: String := ""
                    }
                    rule `age-check`
                    when
                        customer.age >= 18
                    then
                        result := "adult"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();
            assertThat(drl).contains("$customer.getAge() >= new java.math.BigDecimal(18)");
        }
    }

    @Nested
    @DisplayName("Global declarations")
    class Globals {

        @Test
        @DisplayName("globals are emitted as DRL global declarations")
        void globalsEmittedAsDrlGlobals() {
            String source = """
                    globals {
                        taxRate: java.math.BigDecimal
                        configValue: String
                    }
                    facts {
                        x: String
                    }
                    outputs {
                        y: String := ""
                    }
                    rule `use-global`
                    when
                        x = "a"
                    then
                        y := "b"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();

            assertThat(drl).contains("global java.math.BigDecimal taxRate;");
            assertThat(drl).contains("global String configValue;");
        }
    }

    @Nested
    @DisplayName("Outputs")
    class Outputs {

        @Test
        @DisplayName("__outputs global is declared for the output holder")
        void outputsGlobalDeclared() {
            String source = """
                    facts {
                        x: String
                    }
                    outputs {
                        result: String := "none"
                        score: java.math.BigDecimal := 0
                    }
                    rule `set-output`
                    when
                        x = "go"
                    then
                        result := "done"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source,
                    DroolsCompilerOptions.of("work.spell.test", "outputs_test"));

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();

            // The __outputs global is declared with the fully qualified outputs type
            assertThat(drl).contains("global work.spell.test.OutputsTestOutputs __outputs;");
        }

        @Test
        @DisplayName("output assignments in then block call __outputs setter")
        void outputAssignmentCallsSetter() {
            String source = """
                    facts {
                        x: String
                    }
                    outputs {
                        result: String := "no"
                    }
                    rule `set-result`
                    when
                        x = "trigger"
                    then
                        result := "yes"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();
            assertThat(drl).contains("__outputs.setResult(\"yes\");");
        }

        @Test
        @DisplayName("companion outputs POJO Java file is generated")
        void outputsPojoGenerated() {
            String source = """
                    facts {
                        x: String
                    }
                    outputs {
                        eligible: String := "no"
                        discount: java.math.BigDecimal := 0
                    }
                    rule r
                    when
                        x = "a"
                    then
                        eligible := "yes"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source, DroolsCompilerOptions.of("com.example", "pojo_test"));

            assertThat(result.isSuccess()).isTrue();
            Map<String, String> files = result.getSourceFiles().orElseThrow();

            // Should have both DRL and Java POJO files
            assertThat(files).containsKey("com/example/pojo_test.drl");
            assertThat(files).containsKey("com/example/PojoTestOutputs.java");

            String pojo = files.get("com/example/PojoTestOutputs.java");
            assertThat(pojo).contains("public class PojoTestOutputs");
            assertThat(pojo).contains("private String eligible");
            assertThat(pojo).contains("private java.math.BigDecimal discount");
            assertThat(pojo).contains("public String getEligible()");
            assertThat(pojo).contains("public void setEligible(");
            assertThat(pojo).contains("public java.math.BigDecimal getDiscount()");
            assertThat(pojo).contains("public void setDiscount(");
        }

        @Test
        @DisplayName("outputs POJO contains initial values")
        void outputsPojoHasInitialValues() {
            String source = """
                    facts {
                        x: String
                    }
                    outputs {
                        status: String := "pending"
                        amount: java.math.BigDecimal := 100
                    }
                    rule r
                    when
                        x = "a"
                    then
                        status := "done"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source, DroolsCompilerOptions.of("", "init_test"));

            assertThat(result.isSuccess()).isTrue();
            Map<String, String> files = result.getSourceFiles().orElseThrow();
            String pojo = files.get("InitTestOutputs.java");
            assertThat(pojo).contains("= \"pending\"");
            assertThat(pojo).contains("= new java.math.BigDecimal(100)");
        }
    }

    @Nested
    @DisplayName("Template rules")
    class TemplateRules {

        @Test
        @DisplayName("template rule expands to one DRL rule per data table row")
        void templateRuleExpandsToOneRulePerRow() {
            String source = """
                    facts {
                        code: String
                    }
                    outputs {
                        discount: java.math.BigDecimal := 0
                    }
                    template rule COUPON "Coupon codes"
                    data table {
                        | couponCode | discountPct |
                        | ---------- | ----------- |
                        | "SAVE10"   | 10          |
                        | "SAVE20"   | 20          |
                        | "VIP50"    | 50          |
                    }
                    when
                        code = couponCode
                    then
                        discount := discountPct
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();

            // One rule per row — three rows means three rule blocks
            long ruleCount = drl.lines().filter(l -> l.startsWith("rule \"")).count();
            assertThat(ruleCount).isEqualTo(3);
            // Each rule has a different index suffix
            assertThat(drl).contains("rule \"COUPON-0\"");
            assertThat(drl).contains("rule \"COUPON-1\"");
            assertThat(drl).contains("rule \"COUPON-2\"");
        }
    }

    @Nested
    @DisplayName("Decision table rules")
    class DecisionTableRules {

        @Test
        @DisplayName("decision table expands to one DRL rule per row")
        void decisionTableExpandsToOneRulePerRow() {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.CustomerProfile
                    }
                    outputs {
                        title: String := ""
                    }
                    decision table GREETINGS "Greet customers" {
                    | ID    | WHEN         | THEN      |
                    |       | customer.age | #greeting |
                    | ----- | ------------ | --------- |
                    | ADULT | >= 18        | "Sir"     |
                    | CHILD | < 18         | "Young"   |
                    } where greeting := [:t | title := t]
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();

            assertThat(drl).contains("rule \"GREETINGS-ADULT\"");
            assertThat(drl).contains("rule \"GREETINGS-CHILD\"");
        }
    }

    @Nested
    @DisplayName("Package declaration")
    class PackageDeclaration {

        @Test
        @DisplayName("package name is emitted in DRL")
        void packageNameEmitted() {
            String source = """
                    facts {
                        x: String
                    }
                    outputs {
                        y: String := ""
                    }
                    rule r
                    when
                        x = "a"
                    then
                        y := "b"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source,
                    DroolsCompilerOptions.of("com.example.rules", "pkg_test"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDrlSource()).startsWith("package com.example.rules;");
        }

        @Test
        @DisplayName("no package declaration when package name is empty")
        void noPackageWhenEmpty() {
            String source = """
                    facts {
                        x: String
                    }
                    outputs {
                        y: String := ""
                    }
                    rule r
                    when
                        x = "a"
                    then
                        y := "b"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source, DroolsCompilerOptions.of("", "no_pkg_test"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDrlSource()).doesNotContain("package ");
        }
    }

    @Nested
    @DisplayName("Import declarations")
    class ImportDeclarations {

        @Test
        @DisplayName("imports from rule module appear in DRL")
        void importsAppearInDrl() {
            String source = """
                    imports {
                        Order := work.spell.iskibal.e2e.Order
                        Customer := work.spell.iskibal.e2e.Customer
                    }
                    facts {
                        order: work.spell.iskibal.e2e.Order
                    }
                    outputs {
                        result: String := ""
                    }
                    rule r
                    when
                        order = order
                    then
                        result := "ok"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();
            assertThat(drl).contains("import work.spell.iskibal.e2e.Order;");
            assertThat(drl).contains("import work.spell.iskibal.e2e.Customer;");
        }
    }

    @Nested
    @DisplayName("Binary expressions")
    class BinaryExpressions {

        @Test
        @DisplayName("comparison operators are emitted in eval conditions")
        void comparisonOperatorsEmitted() {
            String source = """
                    facts {
                        amount: java.math.BigDecimal
                    }
                    outputs {
                        category: String := "low"
                    }
                    rule `high-value`
                    when
                        amount > 1000
                    then
                        category := "high"
                    end
                    rule `medium-value`
                    when
                        amount >= 100
                    then
                        category := "medium"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();
            assertThat(drl).contains("> new java.math.BigDecimal(1000)");
            assertThat(drl).contains(">= new java.math.BigDecimal(100)");
            assertThat(drl).contains("rule \"high-value\"");
            assertThat(drl).contains("rule \"medium-value\"");
        }
    }

    @Nested
    @DisplayName("Unary message expressions")
    class UnaryMessageExpressions {

        @Test
        @DisplayName("notEmpty message is translated to isEmpty check")
        void notEmptyTranslated() {
            String source = """
                    facts {
                        items: java.util.List
                    }
                    outputs {
                        result: String := "no"
                    }
                    rule `has-items`
                    when
                        items notEmpty
                    then
                        result := "yes"
                    end
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();
            assertThat(drl).contains("!= null && !");
            assertThat(drl).contains(".isEmpty()");
        }
    }

    @Nested
    @DisplayName("File path generation")
    class FilePathGeneration {

        @Test
        @DisplayName("DRL file path uses rule name and package")
        void drlFilePathCorrect() {
            DroolsCompilerOptions options = DroolsCompilerOptions.of("com.example", "pricing_rules");
            assertThat(options.drlFilePath()).isEqualTo("com/example/pricing_rules.drl");
            assertThat(options.outputsFilePath()).isEqualTo("com/example/PricingRulesOutputs.java");
            assertThat(options.outputsClassName()).isEqualTo("PricingRulesOutputs");
        }

        @Test
        @DisplayName("DRL file path without package")
        void drlFilePathNoPackage() {
            DroolsCompilerOptions options = DroolsCompilerOptions.of("", "my_rules");
            assertThat(options.drlFilePath()).isEqualTo("my_rules.drl");
        }
    }

    @Nested
    @DisplayName("AsciiDoc integration")
    class AsciiDocIntegration {

        @Test
        @DisplayName("AsciiDoc document compiles to valid DRL")
        void asciiDocCompilesSuccessfully() {
            DroolsCompilationResult result = compileAdocFile("discount_rules.adoc",
                    DroolsCompilerOptions.of("work.spell.iskibal.e2e", "discount_rules"));

            assertThat(result.isSuccess())
                    .as("DRL compilation should succeed; errors: %s", result.getErrors())
                    .isTrue();
        }

        @Test
        @DisplayName("AsciiDoc DRL contains all expected rule identifiers")
        void asciiDocDrlContainsRules() {
            DroolsCompilationResult result = compileAdocFile("discount_rules.adoc",
                    DroolsCompilerOptions.of("work.spell.iskibal.e2e", "discount_rules"));

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();

            // Simple rules
            assertThat(drl).contains("rule \"ELIG_001\"");
            assertThat(drl).contains("rule \"ELIG_001-else\"");
            assertThat(drl).contains("rule \"DISC_001\"");
            assertThat(drl).contains("rule \"DISC_002\"");

            // Decision table rows
            assertThat(drl).contains("rule \"TIER_DISC-BRONZE\"");
            assertThat(drl).contains("rule \"TIER_DISC-SILVER\"");
            assertThat(drl).contains("rule \"TIER_DISC-GOLD\"");

            // Template rule rows (3 coupon rows → 3 DRL rules)
            assertThat(drl).contains("rule \"COUPON-0\"");
            assertThat(drl).contains("rule \"COUPON-1\"");
            assertThat(drl).contains("rule \"COUPON-2\"");
        }

        @Test
        @DisplayName("AsciiDoc DRL includes correct imports and globals")
        void asciiDocDrlHasImportsAndGlobals() {
            DroolsCompilationResult result = compileAdocFile("discount_rules.adoc",
                    DroolsCompilerOptions.of("work.spell.iskibal.e2e", "discount_rules"));

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();

            assertThat(drl).contains("import work.spell.iskibal.e2e.CustomerProfile;");
            assertThat(drl).contains("import work.spell.iskibal.e2e.OrderContext;");
            assertThat(drl).contains("global java.math.BigDecimal taxRate;");
        }

        @Test
        @DisplayName("AsciiDoc outputs POJO is generated with correct fields")
        void asciiDocOutputsPojoGenerated() {
            DroolsCompilationResult result = compileAdocFile("discount_rules.adoc",
                    DroolsCompilerOptions.of("work.spell.iskibal.e2e", "discount_rules"));

            assertThat(result.isSuccess()).isTrue();
            Map<String, String> files = result.getSourceFiles().orElseThrow();

            assertThat(files).containsKey("work/spell/iskibal/e2e/DiscountRulesOutputs.java");

            String pojo = files.get("work/spell/iskibal/e2e/DiscountRulesOutputs.java");
            assertThat(pojo).contains("public class DiscountRulesOutputs");
            assertThat(pojo).contains("private String eligible");
            assertThat(pojo).contains("private java.math.BigDecimal discountPercent");
            assertThat(pojo).contains("= \"no\"");
        }

        @Test
        @DisplayName("fact patterns are bound as DRL variables")
        void asciiDocFactPatternsBound() {
            DroolsCompilationResult result = compileAdocFile("discount_rules.adoc",
                    DroolsCompilerOptions.of("work.spell.iskibal.e2e", "discount_rules"));

            assertThat(result.isSuccess()).isTrue();
            String drl = result.getDrlSource();

            assertThat(drl).contains("$order : OrderContext()");
            assertThat(drl).contains("$customer : CustomerProfile()");
        }
    }

    @Nested
    @DisplayName("DRL-native decision tables")
    class DrlNativeDecisionTables {

        @Test
        @DisplayName("decision table [drools] passes WHEN/THEN cells verbatim")
        void drlNativeDecisionTablePassesCellsVerbatim() {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.CustomerProfile
                    }
                    outputs {
                        discount: java.math.BigDecimal := 0
                    }
                    decision table [drools] TIER "Loyalty tier discounts" {
                    | ID     | WHEN                              | THEN                          |
                    |        | $customer.getLoyaltyPoints() >= 0 | __outputs.setDiscount(null)   |
                    | ------ | --------------------------------- | ----------------------------- |
                    | BRONZE | $customer.getLoyaltyPoints() >= 50   | __outputs.setDiscount(new java.math.BigDecimal(5)) |
                    | SILVER | $customer.getLoyaltyPoints() >= 200  | __outputs.setDiscount(new java.math.BigDecimal(15)) |
                    | GOLD   | $customer.getLoyaltyPoints() >= 1000 | __outputs.setDiscount(new java.math.BigDecimal(25)) |
                    }
                    """;

            DroolsCompilationResult result = compileDrl(source);

            assertThat(result.isSuccess())
                    .as("compilation should succeed; errors: %s", result.getErrors())
                    .isTrue();
            String drl = result.getDrlSource();

            assertThat(drl).contains("rule \"TIER-BRONZE\"");
            assertThat(drl).contains("rule \"TIER-SILVER\"");
            assertThat(drl).contains("rule \"TIER-GOLD\"");

            // WHEN cells are passed verbatim (wrapped in eval)
            assertThat(drl).contains("$customer.getLoyaltyPoints() >= 50");
            assertThat(drl).contains("$customer.getLoyaltyPoints() >= 200");
            assertThat(drl).contains("$customer.getLoyaltyPoints() >= 1000");

            // THEN cells are passed verbatim
            assertThat(drl).contains("__outputs.setDiscount(new java.math.BigDecimal(5))");
            assertThat(drl).contains("__outputs.setDiscount(new java.math.BigDecimal(15))");
            assertThat(drl).contains("__outputs.setDiscount(new java.math.BigDecimal(25))");
        }

        @Test
        @DisplayName("AsciiDoc decision table with language=drools passes cells verbatim")
        void asciiDocDrlNativeDecisionTable() {
            DroolsCompilationResult result = compileAdocFile("discount_rules.adoc",
                    DroolsCompilerOptions.of("work.spell.iskibal.e2e", "discount_rules"));

            assertThat(result.isSuccess())
                    .as("DRL compilation should succeed; errors: %s", result.getErrors())
                    .isTrue();
            String drl = result.getDrlSource();

            assertThat(drl).contains("rule \"TIER_DISC_DRL-BRONZE\"");
            assertThat(drl).contains("rule \"TIER_DISC_DRL-SILVER\"");
            assertThat(drl).contains("rule \"TIER_DISC_DRL-GOLD\"");

            // Cells should be raw DRL/Java passed through verbatim
            assertThat(drl).contains("$customer.getLoyaltyPoints() >= 50");
            assertThat(drl).contains("$customer.getLoyaltyPoints() >= 200");
            assertThat(drl).contains("$customer.getLoyaltyPoints() >= 1000");
        }
    }
}
