package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/**
 * End-to-end tests for data-driven rules using data tables. Tests template
 * rules and decision tables.
 */
class DataTableE2ETest {

    @Nested
    @DisplayName("Module-level Data Tables")
    class ModuleLevelDataTables {

        @Test
        @DisplayName("Two-column table used as dictionary lookup")
        void twoColumnTableAsDictionaryLookup() throws Exception {
            String source = """
                    facts {
                        item: work.spell.iskibal.e2e.Item
                    }
                    outputs {
                        discount: BigDecimal := 0
                    }
                    data table Discounts {
                        | itemType | amount |
                        | -------- | ------ |
                        | "TypeA"  | 10     |
                        | "TypeB"  | 20     |
                    }
                    rule DISC "Apply discount from table"
                    when
                        true
                    then
                        discount := Discounts at: item.type
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Item("TypeA")).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(BigDecimal.TEN);
        }

        @Test
        @DisplayName("Two-column table lookup used in when condition via let")
        void twoColumnTableLookupInWhenCondition() throws Exception {
            String source = """
                    facts {
                        item: work.spell.iskibal.e2e.Item
                    }
                    outputs {
                        overLimit: String := "no"
                    }
                    data table Thresholds {
                        | itemType  | threshold |
                        | --------- | --------- |
                        | "TypeA"   | 5         |
                        | "TypeB"   | 50        |
                    }
                    rule CHECK "Check against threshold"
                    when
                        let limit := Thresholds at: item.type
                        limit > 20
                    then
                        overLimit := "yes"
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Item("TypeB")).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("overLimit")).isEqualTo("yes");
        }

        @Test
        @DisplayName("Multi-column table used as list of maps with where filter")
        void multiColumnTableWithWhereFilter() throws Exception {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.Customer
                    }
                    outputs {
                        found: String := "no"
                    }
                    data table Customers {
                        | name    | minAge | tier     |
                        | ------- | ------ | -------- |
                        | "Alice" | 18     | "Gold"   |
                        | "Bob"   | 21     | "Silver" |
                    }
                    rule FIND "Find matching customer"
                    when
                        (Customers where: [:p |
                            customer.name = p at: "name",
                            p at: "minAge" <= customer.age
                        ]) exists
                    then
                        found := "yes"
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Customer("Alice", 25)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("found")).isEqualTo("yes");
        }
    }

    @Nested
    @DisplayName("Template Rules")
    class TemplateRules {

        private static final String DISCOUNT_BY_TYPE_SOURCE = """
                facts {
                    item: work.spell.iskibal.e2e.Item
                }
                outputs {
                    discount: BigDecimal := 0
                }
                template rule DISC "Discount by type"
                data table {
                    | itemType  | discountAmount |
                    | --------- | -------------- |
                    | "TypeA"   | 10             |
                    | "TypeB"   | 20             |
                }
                when
                    item.type = itemType
                then
                    discount := discountAmount
                end
                """;

        @Test
        @DisplayName("Template rule generates one method per row")
        void templateRuleGeneratesOneMethodPerRow() throws Exception {
            var result = RuleTestBuilder.forSource(DISCOUNT_BY_TYPE_SOURCE).withFact(new Item("TypeA")).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(BigDecimal.TEN);
        }

        @Test
        @DisplayName("Second row of template applies correctly")
        void secondRowOfTemplateAppliesCorrectly() throws Exception {
            var result = RuleTestBuilder.forSource(DISCOUNT_BY_TYPE_SOURCE).withFact(new Item("TypeB")).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(new BigDecimal("20"));
        }

        @Test
        @DisplayName("Template rule with no matching row keeps default")
        void templateRuleNoMatchKeepsDefault() throws Exception {
            var result = RuleTestBuilder.forSource(DISCOUNT_BY_TYPE_SOURCE).withFact(new Item("TypeC")).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            // No match, should keep default
            assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Decision Tables")
    class DecisionTables {

        @Test
        @DisplayName("with parameterized alias")
        void decisionTableWithParameterizedAlias() throws Exception {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.Customer
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
                    } where greeting := [:t |
                        title := t
                    ]
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Customer("Alice", 25)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("title")).isEqualTo("Sir");
        }

        @Test
        @DisplayName("with parameterized alias - child")
        void decisionTableWithParameterizedAliasChild() throws Exception {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.Customer
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
                    } where greeting := [:t |
                        title := t
                    ]
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Customer("Bob", 10)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("title")).isEqualTo("Young");
        }

        @Test
        @DisplayName("with parameterless alias")
        void decisionTableWithParameterlessAlias() throws Exception {
            // Use curly braces { } for parameterless blocks (no implicit param)
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.Customer
                    }
                    outputs {
                        discount: BigDecimal := 0
                    }
                    decision table DISCOUNTS "Apply discounts" {
                    | ID       | WHEN       | THEN       |
                    |          | #isAdult   | #addBonus  |
                    | -------- | ---------- | ---------- |
                    | ADULT_20 | *          | *          |
                    } where isAdult := {
                        customer.age >= 18
                    },
                    addBonus := {
                        discount := 20
                    }
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Customer("Alice", 25)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(new BigDecimal("20"));
        }

        @Test
        @DisplayName("should not allow assignment to outputs in when clause")
        void decisionTableShouldNotAllowAssignmentToOutputsInWhenClause() throws Exception {
            // Use curly braces { } for parameterless blocks (no implicit param)
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.Customer
                    }
                    outputs {
                        discount: BigDecimal := 0
                    }
                    decision table DISCOUNTS "Apply discounts" {
                    | ID       | WHEN       | THEN       |
                    |          | #addBonus  | #addBonus  |
                    | -------- | ---------- | ---------- |
                    | ADULT_20 | *          | *          |
                    } where addBonus := {
                        discount := 20
                        true
                    }
                    """;

            RuleTestResult result = RuleTestBuilder.forSource(source).withFact(new Customer("Alice", 25)).build();

            assertThat(result).isInstanceOf(RuleTestResult.AnalysisFailure.class);
        }

        @Test
        @DisplayName("Decision table with multiple WHEN columns")
        void decisionTableWithMultipleWhenColumns() throws Exception {
            // VIP rule requires name = "Alice", REGULAR rule requires name = "Bob"
            // So they are mutually exclusive
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.Customer
                    }
                    outputs {
                        category: String := "unknown"
                    }
                    decision table CATEGORIZE "Categorize customers" {
                    | ID        | WHEN         | WHEN                  | THEN        |
                    |           | customer.age | customer.name         | category := |
                    | --------- | ------------ | --------------------- | ----------- |
                    | VIP_ADULT | >= 18        | = "Alice"             | "VIP"       |
                    | REGULAR   | >= 18        | = "Bob"               | "regular"   |
                    }
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Customer("Alice", 25)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("category")).isEqualTo("VIP");
        }

        @Test
        @DisplayName("Decision table with mixed direct expressions and aliases")
        void decisionTableWithMixedExpressionsAndAliases() throws Exception {
            // Use mutually exclusive age ranges: >= 65 for senior, < 65 for adult
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.Customer
                    }
                    outputs {
                        discount: BigDecimal := 0
                        category: String := ""
                    }
                    decision table OFFERS "Special offers" {
                    | ID          | WHEN         | THEN        | THEN          |
                    |             | customer.age | discount := | #setCategory  |
                    | ----------- | ------------ | ----------- | ------------- |
                    | SENIOR_DISC | >= 65        | 25          | "Senior"      |
                    | ADULT_DISC  | < 65         | 10          | "Adult"       |
                    } where setCategory := [:cat |
                        category := cat
                    ]
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Customer("Bob", 70)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(new BigDecimal("25"));
            assertThat(rules.<String>getOutput("category")).isEqualTo("Senior");
        }
    }

    private void assertResultSuccess(RuleTestResult result) {
        if (!result.isSuccess()) {
            String errorMessage = String.format("Expected success at stage '%s' but got errors: %s", result.getStage(),
                    result.getErrors());
            if (result instanceof RuleTestResult.CompilationFailure cf) {
                errorMessage += "\n\nGenerated source:\n" + cf.generatedSource();
            }
            throw new AssertionError(errorMessage);
        }
    }
}
