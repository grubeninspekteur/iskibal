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

	// TODO this only tests template rules, not (local) data tables, and also not
	// decision tables. Needs to be expanded!

	@Nested
	@DisplayName("Template Rules")
	class TemplateRules {

		// TODO reuse rule source
		// TODO change RuleTestBuilder so you can supply facts afterwards, not have to
		// recompile for different facts, refactor other tests to make use of it

		@Test
		@DisplayName("Template rule generates one method per row")
		void templateRuleGeneratesOneMethodPerRow() throws Exception {
			String source = """
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

			var result = RuleTestBuilder.forSource(source).withFact(new Item("TypeA")).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(BigDecimal.TEN);
		}

		@Test
		@DisplayName("Second row of template applies correctly")
		void secondRowOfTemplateAppliesCorrectly() throws Exception {
			String source = """
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

			var result = RuleTestBuilder.forSource(source).withFact(new Item("TypeB")).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(new BigDecimal("20"));
		}

		@Test
		@DisplayName("Template rule with no matching row keeps default")
		void templateRuleNoMatchKeepsDefault() throws Exception {
			String source = """
					facts {
					    item: work.spell.iskibal.e2e.Item
					}
					outputs {
					    discount: BigDecimal := 100
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

			var result = RuleTestBuilder.forSource(source).withFact(new Item("TypeC")).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			// No match, should keep default
			assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(new BigDecimal("100"));
		}
	}

	// TODO this doesn't belong into the data tables category
	@Nested
	@DisplayName("Multiple Rules")
	class MultipleRules {

		@Test
		@DisplayName("Multiple rules for same fact type")
		void multipleRulesForSameFactType() throws Exception {
			String source = """
					facts {
					    customer: work.spell.iskibal.e2e.Customer
					}
					outputs {
					    discount: BigDecimal := 0
					}
					rule DISC1 "First discount"
					when
					    customer.age >= 18
					then
					    discount := 5
					end

					rule DISC2 "Second discount"
					when
					    customer.age >= 30
					then
					    discount := discount + 5
					end
					""";

			var result = RuleTestBuilder.forSource(source).withFact(new Customer("Alice", 35)).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			// Both rules fire: 5 + 5 = 10
			assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(BigDecimal.TEN);
		}
	}

	@Nested
	@DisplayName("Cascading Rules")
	class CascadingRules {

		@Test
		@DisplayName("Multiple rules can modify the same output")
		void multipleRulesCanModifySameOutput() throws Exception {
			String source = """
					facts {
					    order: work.spell.iskibal.e2e.Order
					}
					outputs {
					    totalDiscount: BigDecimal := 0
					}
					rule BASE1 "Base discount"
					when
					    order.total > 50
					then
					    totalDiscount := 5
					end

					rule BONUS1 "Bonus discount"
					when
					    order.total > 100
					then
					    totalDiscount := totalDiscount + 10
					end
					""";

			var result = RuleTestBuilder.forSource(source).withFact(new Order(new BigDecimal("150"))).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			// Both rules fire: 5 + 10 = 15
			assertThat(rules.<BigDecimal>getOutput("totalDiscount")).isEqualByComparingTo(new BigDecimal("15"));
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
