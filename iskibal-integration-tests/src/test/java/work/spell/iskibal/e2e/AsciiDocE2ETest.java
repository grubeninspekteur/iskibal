package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import module java.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.AsciiDocRuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/// End-to-end integration test that parses AsciiDoc documents with embedded Iskara
/// rules, compiles them, and validates rule behavior.
///
/// Uses a realistic e-commerce order validation scenario exercising:
/// - AsciiDoc include directives for modular rule organization
/// - Import, fact, output, and global declarations in AsciiDoc tables
/// - Data tables with block IDs
/// - Simple rules (when/then/else), template rules, and decision tables
/// - Navigation, collections, blocks, let bindings, template strings, globals
class AsciiDocE2ETest {

    private static final Path RULES_DIR = Path.of("src/test/resources/e2e/ecommerce");
    private static final Path RULES_FILE = RULES_DIR.resolve("order_rules.adoc");

    private RuleTestResult buildRules(OrderContext order, CustomerProfile customer, BigDecimal taxRate) {
        return AsciiDocRuleTestBuilder.forFile(RULES_FILE)
                .withFact(order)
                .withFact(customer)
                .withGlobal(taxRate)
                .build();
    }

    private void assertSuccess(RuleTestResult result) {
        if (!result.isSuccess()) {
            String errorMessage = String.format("Expected success at stage '%s' but got errors: %s", result.getStage(),
                    result.getErrors());
            if (result instanceof RuleTestResult.CompilationFailure cf) {
                errorMessage += "\n\nGenerated source:\n" + cf.generatedSource();
            }
            throw new AssertionError(errorMessage);
        }
    }

    @Nested
    @DisplayName("Eligibility Rules")
    class Eligibility {

        @Test
        @DisplayName("Adult with items is eligible")
        void adultWithItemsIsEligible() throws Exception {
            var product = new Product("Laptop", "electronics", new BigDecimal("999.99"), true);
            var line = new OrderLine(product, 1);
            var customer = new CustomerProfile("Alice", 30, 100, false);
            var order = new OrderContext(customer, List.of(line), null);

            var result = buildRules(order, customer, new BigDecimal("1.19"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("eligible")).isEqualTo("yes");
        }

        @Test
        @DisplayName("Minor is not eligible")
        void minorIsNotEligible() throws Exception {
            var product = new Product("Toy", "electronics", new BigDecimal("19.99"), true);
            var line = new OrderLine(product, 1);
            var customer = new CustomerProfile("Bob", 15, 0, false);
            var order = new OrderContext(customer, List.of(line), null);

            var result = buildRules(order, customer, new BigDecimal("1.19"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("eligible")).isEqualTo("no");
        }

        @Test
        @DisplayName("Adult with empty cart is not eligible")
        void adultWithEmptyCartIsNotEligible() throws Exception {
            var customer = new CustomerProfile("Carol", 25, 0, false);
            var order = new OrderContext(customer, List.of(), null);

            var result = buildRules(order, customer, new BigDecimal("1.19"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("eligible")).isEqualTo("no");
        }
    }

    @Nested
    @DisplayName("Pricing Rules")
    class Pricing {

        @Test
        @DisplayName("Calculates total price from order lines")
        void calculatesTotalPrice() throws Exception {
            var p1 = new Product("Laptop", "electronics", new BigDecimal("1000"), true);
            var p2 = new Product("Mouse", "electronics", new BigDecimal("50"), true);
            var customer = new CustomerProfile("Alice", 30, 100, false);
            var order = new OrderContext(customer, List.of(new OrderLine(p1, 1), new OrderLine(p2, 1)),
                    null);

            var result = buildRules(order, customer, new BigDecimal("1.19"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("totalPrice")).isEqualByComparingTo(new BigDecimal("1050"));
        }

        @Test
        @DisplayName("Generates template string message")
        void generatesTemplateStringMessage() throws Exception {
            var product = new Product("Book", "food", new BigDecimal("25"), true);
            var customer = new CustomerProfile("Dave", 40, 0, false);
            var order = new OrderContext(customer, List.of(new OrderLine(product, 1)), null);

            var result = buildRules(order, customer, new BigDecimal("1.0"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("message")).contains("25");
        }

        @Test
        @DisplayName("Applies electronics category discount from data table")
        void appliesCategoryDiscount() throws Exception {
            var product = new Product("Laptop", "electronics", new BigDecimal("1000"), true);
            var customer = new CustomerProfile("Eve", 30, 100, false);
            var order = new OrderContext(customer, List.of(new OrderLine(product, 1)), null);

            var result = buildRules(order, customer, new BigDecimal("1.19"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discountPercent")).isEqualByComparingTo(new BigDecimal("5"));
        }

        @Test
        @DisplayName("Calculates final price with tax and discount")
        void calculatesFinalPriceWithTax() throws Exception {
            var product = new Product("Shirt", "clothing", new BigDecimal("100"), true);
            var customer = new CustomerProfile("Frank", 30, 100, false);
            var order = new OrderContext(customer, List.of(new OrderLine(product, 1)), null);

            var result = buildRules(order, customer, new BigDecimal("1.19"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            // clothing discount = 10%, so 100 - 10 = 90, * 1.19 = 107.1
            assertThat(rules.<BigDecimal>getOutput("finalPrice")).isEqualByComparingTo(new BigDecimal("107.1"));
        }
    }

    @Nested
    @DisplayName("Customer Categorization (Decision Table)")
    class CustomerCategorization {

        @Test
        @DisplayName("Senior with high loyalty is Senior VIP")
        void seniorVip() throws Exception {
            var product = new Product("Book", "food", new BigDecimal("10"), true);
            var customer = new CustomerProfile("Grandma", 70, 1000, false);
            var order = new OrderContext(customer, List.of(new OrderLine(product, 1)), null);

            var result = buildRules(order, customer, new BigDecimal("1.0"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("customerCategory")).isEqualTo("Senior VIP");
        }

        @Test
        @DisplayName("Senior with low loyalty is Senior")
        void senior() throws Exception {
            var product = new Product("Book", "food", new BigDecimal("10"), true);
            var customer = new CustomerProfile("Grandpa", 65, 200, false);
            var order = new OrderContext(customer, List.of(new OrderLine(product, 1)), null);

            var result = buildRules(order, customer, new BigDecimal("1.0"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("customerCategory")).isEqualTo("Senior");
        }

        @Test
        @DisplayName("Young with high loyalty is Loyal")
        void loyal() throws Exception {
            var product = new Product("Book", "food", new BigDecimal("10"), true);
            var customer = new CustomerProfile("Loyalist", 35, 1500, false);
            var order = new OrderContext(customer, List.of(new OrderLine(product, 1)), null);

            var result = buildRules(order, customer, new BigDecimal("1.0"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("customerCategory")).isEqualTo("Loyal");
        }

        @Test
        @DisplayName("Young with low loyalty is Regular")
        void regular() throws Exception {
            var product = new Product("Book", "food", new BigDecimal("10"), true);
            var customer = new CustomerProfile("Newbie", 25, 50, false);
            var order = new OrderContext(customer, List.of(new OrderLine(product, 1)), null);

            var result = buildRules(order, customer, new BigDecimal("1.0"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("customerCategory")).isEqualTo("Regular");
        }
    }

    @Nested
    @DisplayName("Coupon Rules (Template Rule)")
    class Coupons {

        @Test
        @DisplayName("SAVE10 coupon applies 10% discount")
        void save10Coupon() throws Exception {
            var product = new Product("Book", "food", new BigDecimal("10"), true);
            var customer = new CustomerProfile("Alice", 30, 0, false);
            var order = new OrderContext(customer, List.of(new OrderLine(product, 1)), "SAVE10");

            var result = buildRules(order, customer, new BigDecimal("1.0"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discountPercent")).isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        @DisplayName("VIP50 coupon applies 50% discount")
        void vip50Coupon() throws Exception {
            var product = new Product("Book", "food", new BigDecimal("10"), true);
            var customer = new CustomerProfile("Alice", 30, 0, false);
            var order = new OrderContext(customer, List.of(new OrderLine(product, 1)), "VIP50");

            var result = buildRules(order, customer, new BigDecimal("1.0"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discountPercent")).isEqualByComparingTo(new BigDecimal("50"));
        }

        @Test
        @DisplayName("No coupon keeps default discount")
        void noCoupon() throws Exception {
            var product = new Product("Book", "food", new BigDecimal("10"), true);
            var customer = new CustomerProfile("Alice", 30, 0, false);
            var order = new OrderContext(customer, List.of(new OrderLine(product, 1)), "INVALID");

            var result = buildRules(order, customer, new BigDecimal("1.0"));
            assertSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            // "food" category has 0 discount and no matching coupon
            assertThat(rules.<BigDecimal>getOutput("discountPercent")).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
