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
 * End-to-end tests for module-level structure including module header and
 * imports section.
 */
class ModuleStructureE2ETest {

    @Nested
    @DisplayName("Module Header")
    class ModuleHeader {

        @Test
        @DisplayName("Module with string name")
        void moduleWithStringName() throws Exception {
            String source = """
                    module "Shopping Cart Rules"

                    outputs {
                        result: String
                    }
                    rule MOD "Module test"
                    when true
                    then
                        result := "OK"
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("OK");
        }

        @Test
        @DisplayName("Module with identifier name")
        void moduleWithIdentifierName() throws Exception {
            String source = """
                    module ShoppingCartRules

                    outputs {
                        result: String
                    }
                    rule MOD "Module test"
                    when true
                    then
                        result := "OK"
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("OK");
        }
    }

    @Nested
    @DisplayName("Imports Section")
    class ImportsSection {

        @Test
        @DisplayName("Import creates type alias for facts")
        @org.junit.jupiter.api.Disabled("Requires type alias resolution from imports section")
        void importCreatesTypeAlias() throws Exception {
            String source = """
                    imports {
                        Cart := work.spell.iskibal.e2e.ShoppingCart
                    }
                    facts {
                        cart: Cart
                    }
                    outputs {
                        count: int
                    }
                    rule IMP "Use imported type"
                    when true
                    then
                        count := cart.items size
                    end
                    """;

            var items = List.of(
                    new CartItem("Apple", new BigDecimal("1.50"), true),
                    new CartItem("Banana", new BigDecimal("0.75"), false));
            var cart = new ShoppingCart(items);

            var result = RuleTestBuilder.forSource(source).withFact(cart).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<Integer>getOutput("count")).isEqualTo(2);
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
