package work.spell.iskibal.mavenplugin.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.mavenplugin.tests.generated.DiscountRules;

/// Tests for the rules generated from `discount_rules.iskara` by the
/// iskibal-maven-plugin. The `DiscountRules` class is generated during the
/// `generate-sources` phase and compiled alongside the main sources.
class DiscountRulesTest {

    @Test
    @DisplayName("VIP customer (1000+ points) gets 20% discount")
    void vipCustomerGets20PercentDiscount() {
        var customer = new Customer("Alice", 1500);
        var rules = new DiscountRules(customer);

        rules.evaluate();

        assertThat(rules.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(rules.getTier()).isEqualTo("vip");
    }

    @Test
    @DisplayName("Loyal customer (500–999 points) gets 10% discount")
    void loyalCustomerGets10PercentDiscount() {
        var customer = new Customer("Bob", 750);
        var rules = new DiscountRules(customer);

        rules.evaluate();

        assertThat(rules.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(rules.getTier()).isEqualTo("loyal");
    }

    @Test
    @DisplayName("Standard customer (< 500 points) gets no discount")
    void standardCustomerGetsNoDiscount() {
        var customer = new Customer("Charlie", 100);
        var rules = new DiscountRules(customer);

        rules.evaluate();

        assertThat(rules.getDiscountPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rules.getTier()).isEqualTo("standard");
    }
}
