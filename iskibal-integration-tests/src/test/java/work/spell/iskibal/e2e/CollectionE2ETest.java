package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
                        result: java.util.List
                    }
                    rule LIST1 "Create list"
                    when true
                    then
                        result := ["hello", "world", 42]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<List>getOutput("result")).containsExactly("hello", "world", BigDecimal.valueOf(42));
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
                        result: java.util.Set
                    }
                    rule SET1 "Create set"
                    when true
                    then
                        result := {"Match", "my", "style", "style", "baby!"}
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<Set>getOutput("result")).containsExactlyInAnyOrder("Match", "my", "style", "baby!");
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
                        result: java.util.Map
                    }
                    rule MAP1 "Create map"
                    when true
                    then
                        result := ["hello": "world", "number": 42]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<Map<?, ?>>getOutput("result"))
                    .isEqualTo(Map.of("hello", "world", "number", BigDecimal.valueOf(42)));
        }

        /**
         * A naiive implementation would use {@link Map#of()} with key-value pairs,
         * which is only defined for at most 10 pairs. Rather,
         * {@link Map#ofEntries(Entry[])} should be used.
         */
        @Test
        @DisplayName("Map literals with many arguments be created")
        void mapLiteralWithManyArguments() throws Exception {
            String source = """
                    outputs {
                        result: java.util.Map
                    }
                    rule MAP1 "Create map"
                    when true
                    then
                        result := [
                        1: 1,
                        2: 2,
                        3: 3,
                        4: 4,
                        5: 5,
                        6: 6,
                        7: 7,
                        8: 8,
                        9: 9,
                        10: 10,
                        11: 11,
                        12: 12
                        ]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            Map<BigDecimal, BigDecimal> expectedMap = new HashMap<>();
            for (int i = 1; i <= 12; i++) {
                expectedMap.put(BigDecimal.valueOf(i), BigDecimal.valueOf(i));
            }

            assertThat(rules.<Map<?, ?>>getOutput("result")).isEqualTo(expectedMap);
        }
    }

    @Nested
    @DisplayName("Collection Operations")
    class CollectionOperations {
        ShoppingCart cart;

        @BeforeEach
        void fillShoppingCart() {
            var items = List.of(new CartItem("Apple", new BigDecimal("1.50"), true),
                    new CartItem("Banana", new BigDecimal("0.75"), false),
                    new CartItem("Cherry", new BigDecimal("3.00"), true));
            cart = new ShoppingCart(items);
        }

        String createShoppingCartRule(String resultType, String consequence) {
            return """
                    facts {
                        cart: work.spell.iskibal.e2e.ShoppingCart
                    }
                    outputs {
                        result: %s
                    }
                    rule CART "ShoppingCart test"
                    when true
                    then
                        %s
                    end
                    """.formatted(resultType, consequence);
        }

        <T> T getResult(String source, Class<T> targetClass) throws Exception {
            var result = RuleTestBuilder.forSource(source).withFact(cart).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            return targetClass.cast(rules.getOutput("result"));
        }

        @Test
        @DisplayName("Flattens lists to values")
        void flattensItemsToValues() throws Exception {
            String source = createShoppingCartRule("java.util.List", "result := cart.items.name");
            assertThat(getResult(source, List.class)).containsExactly("Apple", "Banana", "Cherry");
        }

        @Test
        @DisplayName("Access lists by 0-indexed `at:` operation")
        void accessAtIndex() throws Exception {
            String source = createShoppingCartRule("String", "result := (cart.items at: 1).name");
            assertThat(getResult(source, String.class)).isEqualTo("Banana");
        }

        @Test
        @DisplayName("Access map by key with `at:`")
        void accessMapByKey() throws Exception {
            String source = createShoppingCartRule("boolean", """
                    result := ["foo": "bar", "gold": "silver"] at: "gold" = "silver"
                    """);
            assertThat(getResult(source, Boolean.class)).isEqualTo(true);
        }

        @Test
        @DisplayName("Get list size")
        void getSizeOfList() throws Exception {
            String source = createShoppingCartRule("int", """
                    result := cart.items size
                    """);
            assertThat(getResult(source, Integer.class)).isEqualTo(3);
        }

        @Test
        @DisplayName("Get set size")
        void getSizeOfSet() throws Exception {
            String source = createShoppingCartRule("int", """
                    result := {1, 2, 3, 4, 5} size
                    """);
            assertThat(getResult(source, Integer.class)).isEqualTo(5);
        }

        @Test
        @DisplayName("Get map size")
        void getSizeOfMap() throws Exception {
            String source = createShoppingCartRule("int", """
                    result := ["Han": "Solo"] size
                    """);
            assertThat(getResult(source, Integer.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("satisfy with all")
        void allOperation() throws Exception {
            String source = createShoppingCartRule("boolean", "result := cart.items all: [:item | item.active = true]");
            assertThat(getResult(source, Boolean.class)).isFalse();
        }

        @Test
        @DisplayName("check presence with exists and empty")
        void existsOperation() throws Exception {
            String source = createShoppingCartRule("java.util.List", """
                     let items := cart.items
                     result := [
                       items exists,
                       items notEmpty,
                       items doesNotExist,
                       items empty,
                       [] exists,
                       [] notEmpty,
                       [] doesNotExist,
                       [] empty
                     ]
                    """);
            assertThat(getResult(source, List.class))
                    .isEqualTo(List.of(true, true, false, false, false, false, true, true));
        }

        @Test
        @DisplayName("Check containment with contains")
        void containsOperation() throws Exception {
            String source = createShoppingCartRule("java.util.Map", """
                    let itemNames := cart.items.name
                    result := [
                      "hasItemsWithBanana": (itemNames contains: "Banana"),
                      "hasItemsWithGold": (itemNames contains: "Gold"),
                      "emptyListContains": ([] contains: "Foo"),
                      "mapContainsKeyExists": (["Foo": "Bar"] contains: "Foo"),
                      "mapContainsKeyDoesNotExist": (["Foo": "Bar"] contains: "Bar")
                    ]
                    """);
            assertThat(getResult(source, Map.class))
                    .isEqualTo(Map.of("hasItemsWithBanana", true, "hasItemsWithGold", false, "emptyListContains", false,
                            "mapContainsKeyExists", true, "mapContainsKeyDoesNotExist", false));
        }

        @Test
        @DisplayName("filter with where")
        void whereOperation() throws Exception {
            String source = createShoppingCartRule("java.util.List", """
                      result := cart.items where: [:item | item.active = true]
                    """);
            assertThat(getResult(source, List.class)).extracting("name").containsExactly("Apple", "Cherry");
        }

        @Test
        @DisplayName("apply with each")
        void eachOperation() throws Exception {
            String source = createShoppingCartRule("boolean", """
                    result := true
                    cart.items each: [:item | item.name := "Foo"]
                    """);
            assertThat(getResult(source, Boolean.class)).isTrue();
            assertThat(cart.getItems()).extracting(CartItem::getName).hasSize(3).containsOnly("Foo");
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
