package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import module java.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/// End-to-end tests for block expressions. Blocks are parametrized expressions
/// that translate to Java lambdas and can be passed to collection operations or
/// Java methods accepting functional interfaces.
class BlockE2ETest {

    @Nested
    @DisplayName("Blocks with Iskara Collection Operations")
    class IskaraCollectionOperations {
        ShoppingCart cart;

        @BeforeEach
        void setUp() {
            var items = List.of(
                    new CartItem("Apple", new BigDecimal("1.50"), true),
                    new CartItem("Banana", new BigDecimal("0.75"), false),
                    new CartItem("Cherry", new BigDecimal("3.00"), true));
            cart = new ShoppingCart(items);
        }

        @Test
        @DisplayName("Block with single parameter filters collection with where:")
        void blockFiltersWithWhere() throws Exception {
            String source = """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        result: java.util.List
                    }
                    rule FILTER "Filter active items"
                    when true
                    then
                        result := cart.items where: [:item | item.active]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(cart).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            List<?> filtered = rules.getOutput("result");
            assertThat(filtered).hasSize(2);
        }

        @Test
        @DisplayName("Block with comparison expression in where:")
        void blockWithComparisonInWhere() throws Exception {
            String source = """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        result: java.util.List
                    }
                    rule FILTER "Filter expensive items"
                    when true
                    then
                        result := cart.items where: [:item | item.price > 1]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(cart).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            List<?> filtered = rules.getOutput("result");
            assertThat(filtered).extracting("name").containsExactly("Apple", "Cherry");
        }

        @Test
        @DisplayName("Block checks all elements with all:")
        void blockChecksAllElements() throws Exception {
            String source = """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        allActive: boolean
                        allPriced: boolean
                    }
                    rule CHECK "Check all items"
                    when true
                    then
                        allActive := cart.items all: [:item | item.active]
                        allPriced := cart.items all: [:item | item.price > 0]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(cart).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<Boolean>getOutput("allActive")).isFalse();
            assertThat(rules.<Boolean>getOutput("allPriced")).isTrue();
        }

        @Test
        @DisplayName("Block modifies each element with each:")
        void blockModifiesEachElement() throws Exception {
            String source = """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        done: boolean
                    }
                    rule MODIFY "Deactivate all items"
                    when true
                    then
                        cart.items each: [:item | item.active := false]
                        done := true
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(cart).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(cart.getItems()).allMatch(item -> !item.isActive());
        }
    }

    @Nested
    @DisplayName("Blocks with Java Stream Methods")
    class JavaStreamMethods {
        ShoppingCart cart;

        @BeforeEach
        void setUp() {
            var items = List.of(
                    new CartItem("Apple", new BigDecimal("1.50"), true),
                    new CartItem("Banana", new BigDecimal("0.75"), false),
                    new CartItem("Cherry", new BigDecimal("3.00"), true));
            cart = new ShoppingCart(items);
        }

        @Test
        @DisplayName("Block with Java Stream map extracts values")
        void blockWithStreamMap() throws Exception {
            String source = """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        result: java.util.List
                    }
                    rule MAP "Extract names using stream map"
                    when true
                    then
                        result := cart.items stream map: [:item | item.name] toList
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(cart).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<List<String>>getOutput("result")).containsExactly("Apple", "Banana", "Cherry");
        }

        @Test
        @DisplayName("Block with Java Stream filter")
        void blockWithStreamFilter() throws Exception {
            String source = """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        result: java.util.List
                    }
                    rule FILTER "Filter using stream filter"
                    when true
                    then
                        result := cart.items stream filter: [:item | item.active] toList
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(cart).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            List<?> filtered = rules.getOutput("result");
            assertThat(filtered).hasSize(2);
        }

        @Test
        @DisplayName("Block with Java Stream anyMatch")
        void blockWithStreamAnyMatch() throws Exception {
            String source = """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        hasExpensive: boolean
                        hasFree: boolean
                    }
                    rule MATCH "Check with anyMatch"
                    when true
                    then
                        hasExpensive := cart.items stream anyMatch: [:item | item.price > 2]
                        hasFree := cart.items stream anyMatch: [:item | item.price = 0]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(cart).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<Boolean>getOutput("hasExpensive")).isTrue();
            assertThat(rules.<Boolean>getOutput("hasFree")).isFalse();
        }
    }

    @Nested
    @DisplayName("Block Syntax Variations")
    class BlockSyntaxVariations {

        @Test
        @DisplayName("List literal can be created")
        void listLiteralCanBeCreated() throws Exception {
            String source = """
                    outputs {
                        result: java.util.List
                    }
                    rule SUPPLY "Create list"
                    when true
                    then
                        result := #(1, 2, 3)
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<List<?>>getOutput("result")).hasSize(3);
        }

        @Test
        @DisplayName("Block with navigation expression")
        void blockWithNavigationExpression() throws Exception {
            var items = List.of(
                    new CartItem("Apple", new BigDecimal("1.50"), true),
                    new CartItem("Banana", new BigDecimal("0.75"), false));
            var cart = new ShoppingCart(items);

            String source = """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        result: java.util.List
                    }
                    rule NAV "Block accesses nested properties"
                    when true
                    then
                        result := cart.items where: [:item | item.name = "Apple"]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(cart).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<List<?>>getOutput("result")).hasSize(1);
        }

        @Test
        @DisplayName("Block with arithmetic in body")
        void blockWithArithmeticExpression() throws Exception {
            var items = List.of(
                    new CartItem("Apple", new BigDecimal("1.50"), true),
                    new CartItem("Banana", new BigDecimal("0.75"), false));
            var cart = new ShoppingCart(items);

            String source = """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        result: java.util.List
                    }
                    rule CALC "Filter by calculated condition"
                    when true
                    then
                        result := cart.items where: [:item | item.price * 2 > 2]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(cart).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            // Apple: 1.50 * 2 = 3.00 > 2 (true)
            // Banana: 0.75 * 2 = 1.50 > 2 (false)
            assertThat(rules.<List<?>>getOutput("result")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Shorthand Block Syntax")
    class ShorthandBlockSyntax {

        @Test
        @DisplayName("Shorthand block [| expr] uses implicit parameter")
        void shorthandBlockWithImplicitParameter() throws Exception {
            String source = """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        result: java.util.List
                    }
                    rule SHORT "Use shorthand block"
                    when true
                    then
                        result := cart.items where: [| active]
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

            assertThat(rules.<List<?>>getOutput("result")).hasSize(1);
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
