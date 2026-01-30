package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/**
 * End-to-end tests for type conversion between int and BigDecimal. These tests
 * validate that the compiler correctly handles type coercion scenarios.
 */
class TypeConversionE2ETest {

	@Nested
	@DisplayName("Int to BigDecimal Comparison")
	class IntToBigDecimalComparison {

		@Test
		@DisplayName("Compare int property with BigDecimal literal (greater or equal)")
		void compareIntPropertyWithBigDecimalLiteralGreaterOrEqual() throws Exception {
			// Customer.age is int, 18 is BigDecimal - type coercion should handle this
			String source = """
					facts {
					    customer: work.spell.iskibal.e2e.Customer
					}
					outputs {
					    category: String := "unknown"
					}
					rule AGE1 "Adult check"
					when
					    customer.age >= 18
					then
					    category := "adult"
					end
					""";

			var result = RuleTestBuilder.forSource(source).withFact(new Customer("Alice", 25)).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<String>getOutput("category")).isEqualTo("adult");
		}

		@Test
		@DisplayName("Compare int property with BigDecimal literal (less than)")
		void compareIntPropertyWithBigDecimalLiteralLessThan() throws Exception {
			String source = """
					facts {
					    customer: work.spell.iskibal.e2e.Customer
					}
					outputs {
					    category: String := "unknown"
					}
					rule AGE2 "Minor check"
					when
					    customer.age < 18
					then
					    category := "minor"
					end
					""";

			var result = RuleTestBuilder.forSource(source).withFact(new Customer("Bob", 15)).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<String>getOutput("category")).isEqualTo("minor");
		}

		@Test
		@DisplayName("Compare int property with BigDecimal literal (equals)")
		void compareIntPropertyWithBigDecimalLiteralEquals() throws Exception {
			String source = """
					facts {
					    customer: work.spell.iskibal.e2e.Customer
					}
					outputs {
					    message: String := ""
					}
					rule AGE3 "Exactly 18"
					when
					    customer.age = 18
					then
					    message := "Just turned adult"
					end
					""";

			var result = RuleTestBuilder.forSource(source).withFact(new Customer("Charlie", 18)).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<String>getOutput("message")).isEqualTo("Just turned adult");
		}
	}

	@Nested
	@DisplayName("Int Arithmetic with BigDecimal")
	class IntArithmetic {

		@Test
		@DisplayName("Int property multiplied by BigDecimal literal")
		void intPropertyMultipliedByBigDecimalLiteral() throws Exception {
			String source = """
					facts {
					    customer: work.spell.iskibal.e2e.Customer
					}
					outputs {
					    ageInMonths: BigDecimal := 0
					}
					rule CALC1 "Calculate age in months"
					when true
					then
					    ageInMonths := customer.age * 12
					end
					""";

			// customer.age is int (25), 12 is BigDecimal
			var result = RuleTestBuilder.forSource(source).withFact(new Customer("Diana", 25)).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("ageInMonths")).isEqualByComparingTo(new BigDecimal("300"));
		}

		@Test
		@DisplayName("Int property addition with BigDecimal literal")
		void intPropertyAdditionWithBigDecimalLiteral() throws Exception {
			String source = """
					facts {
					    customer: work.spell.iskibal.e2e.Customer
					}
					outputs {
					    result: BigDecimal := 0
					}
					rule CALC2 "Add to age"
					when true
					then
					    result := customer.age + 5
					end
					""";

			var result = RuleTestBuilder.forSource(source).withFact(new Customer("Eve", 30)).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("35"));
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
