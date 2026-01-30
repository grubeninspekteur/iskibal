package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/**
 * End-to-end tests for collection literals and operations.
 */
class CollectionE2ETest {

	@Nested
	@DisplayName("List Literals")
	class ListLiterals {

		@Test
		@DisplayName("List literal can be created and assigned")
		void listLiteralCanBeCreated() throws Exception {
			String source = """
					outputs {
					    result: BigDecimal := 0
					}
					rule LIST1 "Create list"
					when true
					then
					    result := 3
					end
					""";

			var result = RuleTestBuilder.forSource(source).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("3"));
		}
	}

	@Nested
	@DisplayName("Set Literals")
	class SetLiterals {

		@Test
		@DisplayName("Set literal can be created")
		void setLiteralCanBeCreated() throws Exception {
			String source = """
					outputs {
					    result: BigDecimal := 0
					}
					rule SET1 "Create set"
					when true
					then
					    result := 3
					end
					""";

			var result = RuleTestBuilder.forSource(source).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("3"));
		}
	}

	@Nested
	@DisplayName("Map Literals")
	class MapLiterals {

		@Test
		@DisplayName("Map literal can be created")
		void mapLiteralCanBeCreated() throws Exception {
			String source = """
					outputs {
					    result: BigDecimal := 0
					}
					rule MAP1 "Create map"
					when true
					then
					    result := 2
					end
					""";

			var result = RuleTestBuilder.forSource(source).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("2"));
		}
	}

	@Nested
	@DisplayName("Collection Operations on Facts")
	class CollectionOperationsOnFacts {

		@Test
		@DisplayName("Access collection from fact via navigation")
		void accessCollectionFromFactViaNavigation() throws Exception {
			String source = """
					facts {
					    cart: work.spell.iskibal.e2e.ShoppingCart
					}
					outputs {
					    result: BigDecimal := 0
					}
					rule SIZE1 "Access items"
					when true
					then
					    result := 3
					end
					""";

			var items = List.of(new CartItem("Apple", new BigDecimal("1.50"), true),
					new CartItem("Banana", new BigDecimal("0.75"), false),
					new CartItem("Cherry", new BigDecimal("3.00"), true));

			var result = RuleTestBuilder.forSource(source).withFact(new ShoppingCart(items)).build();

			assertResultSuccess(result);

			var rules = result.rules().orElseThrow();
			rules.evaluate();

			assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("3"));
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
