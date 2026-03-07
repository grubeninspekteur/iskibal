package work.spell.iskibal.mavenplugin.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.mavenplugin.tests.generated.ShippingRules;

/// Tests for the rules generated from `shipping_rules.adoc` by the
/// iskibal-maven-plugin. Exercises the AsciiDoc source path: facts and outputs
/// are declared in AsciiDoc tables, rules as annotated source blocks.
class ShippingRulesTest {

    @Test
    @DisplayName("Order >= 50 gets free shipping")
    void largeOrderGetsFreeShipping() {
        var order = new Order(new BigDecimal("75"));
        var rules = new ShippingRules(order);

        rules.evaluate();

        assertThat(rules.getFreeShipping()).isEqualTo("yes");
        assertThat(rules.getShippingCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Order < 50 pays standard shipping")
    void smallOrderPaysShipping() {
        var order = new Order(new BigDecimal("30"));
        var rules = new ShippingRules(order);

        rules.evaluate();

        assertThat(rules.getFreeShipping()).isEqualTo("no");
        assertThat(rules.getShippingCost()).isEqualByComparingTo(new BigDecimal("10"));
    }
}
