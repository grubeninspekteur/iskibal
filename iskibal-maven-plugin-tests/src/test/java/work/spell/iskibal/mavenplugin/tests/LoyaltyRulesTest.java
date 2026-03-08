package work.spell.iskibal.mavenplugin.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.mavenplugin.tests.generated.LoyaltyRules;
import work.spell.iskibal.runtime.RuleEvent;
import work.spell.iskibal.runtime.RuleListener;

/// Tests for the rules generated from `loyalty_rules.iskara` with diagnostics
/// mode enabled. Verifies that the `RuleListener` is called when matching rules
/// fire, and only for those rules.
class LoyaltyRulesTest {

    @Test
    @DisplayName("Platinum customer: listener fires for PLATINUM rule only")
    void platinumCustomerFiresPlatinumRule() {
        List<String> firedRules = new ArrayList<>();
        RuleListener listener = event -> {
            if (event instanceof RuleEvent.RuleFired(var id, var desc)) {
                firedRules.add(id);
            }
        };

        var customer = new Customer("Alice", 2500);
        var rules = new LoyaltyRules(customer, listener);

        rules.evaluate();

        assertThat(rules.getStatus()).isEqualTo("platinum");
        assertThat(rules.getBonus()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(firedRules).containsExactly("PLATINUM");
    }

    @Test
    @DisplayName("Gold customer: listener fires for GOLD rule only")
    void goldCustomerFiresGoldRule() {
        List<String> firedRules = new ArrayList<>();
        RuleListener listener = event -> {
            if (event instanceof RuleEvent.RuleFired(var id, var desc)) {
                firedRules.add(id);
            }
        };

        var customer = new Customer("Bob", 1200);
        var rules = new LoyaltyRules(customer, listener);

        rules.evaluate();

        assertThat(rules.getStatus()).isEqualTo("gold");
        assertThat(rules.getBonus()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(firedRules).containsExactly("GOLD");
    }

    @Test
    @DisplayName("Regular customer: listener is never called")
    void regularCustomerFiresNoRule() {
        List<String> firedRules = new ArrayList<>();
        RuleListener listener = event -> {
            if (event instanceof RuleEvent.RuleFired(var id, var desc)) {
                firedRules.add(id);
            }
        };

        var customer = new Customer("Charlie", 300);
        var rules = new LoyaltyRules(customer, listener);

        rules.evaluate();

        assertThat(rules.getStatus()).isEqualTo("regular");
        assertThat(rules.getBonus()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(firedRules).isEmpty();
    }
}
