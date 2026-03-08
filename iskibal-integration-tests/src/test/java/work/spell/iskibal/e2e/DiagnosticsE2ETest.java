package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.runtime.RuleEvent;
import work.spell.iskibal.runtime.RuleListener;
import work.spell.iskibal.testing.RuleTestBuilder;

/// End-to-end tests verifying that a [RuleListener] is called correctly when
/// diagnostics mode is enabled.
class DiagnosticsE2ETest {

    private static final String SOURCE = """
            facts {
                customer: work.spell.iskibal.e2e.Customer
            }
            outputs {
                tier: String := "standard"
                discount: BigDecimal := 0
            }
            rule VIP "VIP customer"
            when
                customer.age >= 30
            then
                tier := "vip"
                discount := 20
            end

            rule YOUNG "Young customer"
            when
                customer.age < 18
            then
                tier := "young"
                discount := 5
            end
            """;

    @Test
    @DisplayName("RuleFired event is emitted for the matching rule only")
    void ruleFiredEventEmittedForMatchingRule() throws Exception {
        List<String> firedRules = new ArrayList<>();
        RuleListener listener = event -> {
            if (event instanceof RuleEvent.RuleFired(var id, var desc)) {
                firedRules.add(id);
            }
        };

        var customer = new Customer("Alice", 35);
        var result = RuleTestBuilder.forSource(SOURCE)
                .withFact(customer)
                .withListener(listener)
                .build();

        assertThat(result.isSuccess()).withFailMessage(() -> "Build failed: " + result.getErrors()).isTrue();
        result.rules().orElseThrow().evaluate();

        assertThat(firedRules).containsExactly("VIP");
    }

    @Test
    @DisplayName("RuleFired event carries the rule description")
    void ruleFiredEventCarriesDescription() throws Exception {
        List<String> descriptions = new ArrayList<>();
        RuleListener listener = event -> {
            if (event instanceof RuleEvent.RuleFired(var id, var desc)) {
                descriptions.add(desc);
            }
        };

        var customer = new Customer("Bob", 35);
        var result = RuleTestBuilder.forSource(SOURCE)
                .withFact(customer)
                .withListener(listener)
                .build();

        assertThat(result.isSuccess()).withFailMessage(() -> "Build failed: " + result.getErrors()).isTrue();
        result.rules().orElseThrow().evaluate();

        assertThat(descriptions).containsExactly("VIP customer");
    }

    @Test
    @DisplayName("No event is emitted when no rule matches")
    void noEventWhenNoRuleMatches() throws Exception {
        List<RuleEvent> events = new ArrayList<>();
        RuleListener listener = events::add;

        // age 20: not >= 30 (VIP) and not < 18 (YOUNG)
        var customer = new Customer("Charlie", 20);
        var result = RuleTestBuilder.forSource(SOURCE)
                .withFact(customer)
                .withListener(listener)
                .build();

        assertThat(result.isSuccess()).withFailMessage(() -> "Build failed: " + result.getErrors()).isTrue();
        result.rules().orElseThrow().evaluate();

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("One RuleFired event per matching rule when multiple rules fire")
    void oneEventPerMatchingRule() throws Exception {
        String multiRuleSource = """
                facts {
                    customer: work.spell.iskibal.e2e.Customer
                }
                outputs {
                    result: String := ""
                }
                rule R1 "Rule one"
                when true
                then
                    result := "r1"
                end

                rule R2 "Rule two"
                when true
                then
                    result := "r2"
                end
                """;

        List<String> firedRules = new ArrayList<>();
        RuleListener listener = event -> {
            if (event instanceof RuleEvent.RuleFired(var id, var desc)) {
                firedRules.add(id);
            }
        };

        var customer = new Customer("Dave", 25);
        var result = RuleTestBuilder.forSource(multiRuleSource)
                .withFact(customer)
                .withListener(listener)
                .build();

        assertThat(result.isSuccess()).withFailMessage(() -> "Build failed: " + result.getErrors()).isTrue();
        result.rules().orElseThrow().evaluate();

        assertThat(firedRules).containsExactlyInAnyOrder("R1", "R2");
    }
}
