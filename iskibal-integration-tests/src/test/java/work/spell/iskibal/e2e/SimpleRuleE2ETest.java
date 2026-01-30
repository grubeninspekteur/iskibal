package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.CompiledRuleTemplate;
import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/**
 * End-to-end tests for simple rules. Exercises the complete pipeline: Iskara
 * source → Parser → Semantic Analysis → Java Generation → Java Compilation →
 * Execution
 */
class SimpleRuleE2ETest {

	@Nested
	@DisplayName("Basic Rule Execution")
	class BasicRuleExecution {

		private static final String WIGGLY_DOLL_SOURCE = """
				facts {
				    item: work.spell.iskibal.e2e.Item
				}
				outputs {
				    discount: BigDecimal := 100
				}
				rule WIG1 "Wiggly dolls exempt"
				when
				    item.type = "WigglyDoll"
				then
				    discount := 0
				end
				""";

		private static CompiledRuleTemplate.Success template;

		@BeforeAll
		static void compileOnce() {
			var compiled = RuleTestBuilder.forSource(WIGGLY_DOLL_SOURCE).compile();
			assertThat(compiled.isSuccess()).isTrue();
			template = (CompiledRuleTemplate.Success) compiled;
		}

		@Test
		@DisplayName("Wiggly doll gets no discount when type matches")
		void wigglyDollGetsNoDiscount() throws Exception {
			var result = template.instantiate(new Item("WigglyDoll"));

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(BigDecimal.ZERO);
		}

		@Test
		@DisplayName("Non-wiggly item keeps default discount")
		void nonWigglyItemKeepsDiscount() throws Exception {
			var result = template.instantiate(new Item("RegularToy"));

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(new BigDecimal("100"));
		}
	}

	@Nested
	@DisplayName("Rule with Else Section")
	class RuleWithElseSection {

		private static final String AGE_CATEGORY_SOURCE = """
				facts {
				    customer: work.spell.iskibal.e2e.Customer
				}
				outputs {
				    category: String := "unknown"
				}
				rule AGE1 "Age category"
				when
				    customer.age >= 18
				then
				    category := "adult"
				else
				    category := "minor"
				end
				""";

		private static CompiledRuleTemplate.Success template;

		@BeforeAll
		static void compileOnce() {
			var compiled = RuleTestBuilder.forSource(AGE_CATEGORY_SOURCE).compile();
			assertThat(compiled.isSuccess()).isTrue();
			template = (CompiledRuleTemplate.Success) compiled;
		}

		@Test
		@DisplayName("Then branch executes when condition is true")
		void thenBranchExecutesWhenConditionTrue() throws Exception {
			var result = template.instantiate(new Customer("Alice", 25));

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<String>getOutput("category")).isEqualTo("adult");
		}

		@Test
		@DisplayName("Else branch executes when condition is false")
		void elseBranchExecutesWhenConditionFalse() throws Exception {
			var result = template.instantiate(new Customer("Bob", 15));

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<String>getOutput("category")).isEqualTo("minor");
		}
	}

	@Nested
	@DisplayName("Multiple Rules")
	class MultipleRules {

		@Test
		@DisplayName("Multiple rules execute in order")
		void multipleRulesExecuteInOrder() throws Exception {
			String source = """
					facts {
					    order: work.spell.iskibal.e2e.Order
					}
					outputs {
					    discount: BigDecimal := 0
					    message: String := ""
					}
					rule DISC1 "High value discount"
					when
					    order.total > 100
					then
					    discount := 10
					end

					rule MSG1 "Add message for high value orders"
					when
					    order.total > 100
					then
					    message := "Thank you for your large order!"
					end
					""";

			var result = RuleTestBuilder.forSource(source).withFact(new Order(new BigDecimal("150"))).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(BigDecimal.TEN);
			assertThat(rules.<String>getOutput("message")).isEqualTo("Thank you for your large order!");
		}
	}

	@Nested
	@DisplayName("Arithmetic Operations")
	class ArithmeticOperations {

		@Test
		@DisplayName("BigDecimal addition works correctly")
		void bigDecimalAddition() throws Exception {
			String source = """
					outputs {
					    result: BigDecimal := 0
					}
					rule CALC "Calculate sum"
					when
					    true
					then
					    result := 10 + 5
					end
					""";

			var result = RuleTestBuilder.forSource(source).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("15"));
		}

		@Test
		@DisplayName("BigDecimal multiplication works correctly")
		void bigDecimalMultiplication() throws Exception {
			String source = """
					outputs {
					    result: BigDecimal := 0
					}
					rule CALC "Calculate product"
					when
					    true
					then
					    result := 10 * 5
					end
					""";

			var result = RuleTestBuilder.forSource(source).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("50"));
		}
	}

	@Nested
	@DisplayName("Navigation Expressions")
	class NavigationExpressions {

		@Test
		@DisplayName("Chained navigation works correctly")
		void chainedNavigation() throws Exception {
			String source = """
					facts {
					    order: work.spell.iskibal.e2e.Order
					}
					outputs {
					    customerName: String := ""
					}
					rule NAV1 "Get customer name"
					when
					    true
					then
					    customerName := order.customer.name
					end
					""";

			var customer = new Customer("Alice", 30);
			var order = new Order(new BigDecimal("100"), customer);

			var result = RuleTestBuilder.forSource(source).withFact(order).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<String>getOutput("customerName")).isEqualTo("Alice");
		}

		@Test
		@DisplayName("Chained navigation performs null checks")
		void chainedNavigationWithNullchecks() throws Exception {
			String source = """
					facts {
					    order: work.spell.iskibal.e2e.Order
					}
					outputs {
					    message: String := ""
					}
					rule NAV1 "Congratulate customer Alice"
					when
					    order.customer.name = "Alice"
					then
					    message := "Congratulations, Alice!"
					end
					""";

			// order with intentional null cusomter
			var order = new Order(new BigDecimal("100"), null);

			var result = RuleTestBuilder.forSource(source).withFact(order).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<String>getOutput("message")).isEqualTo("");
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
