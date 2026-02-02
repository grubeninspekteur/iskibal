package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.CompiledRuleTemplate;
import work.spell.iskibal.testing.CompiledRuleTemplate.Success;
import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/**
 * End-to-end tests for simple rules. Exercises the complete pipeline: Iskara
 * source → Parser → Semantic Analysis → Java Generation → Java Compilation →
 * Execution
 */
class SimpleRuleE2ETest {

    @Nested
    @DisplayName("Basic Rule Execution")
    class BasicRuleExecution {

        private static final String WIGGLY_DOLL_SOURCE = """
                facts {
                    item: work.spell.iskibal.e2e.Item
                }
                outputs {
                    discount: BigDecimal := 100
                }
                rule WIG1 "Wiggly dolls exempt"
                when
                    item.type = "WigglyDoll"
                then
                    discount := 0
                end
                """;

        private static CompiledRuleTemplate.Success template;

        @BeforeAll
        static void compileOnce() {
            template = compileSourceSuccessfully(WIGGLY_DOLL_SOURCE);
        }

        @Test
        @DisplayName("Wiggly doll gets no discount when type matches")
        void wigglyDollGetsNoDiscount() throws Exception {
            var result = template.instantiate(new Item("WigglyDoll"));

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Non-wiggly item keeps default discount")
        void nonWigglyItemKeepsDiscount() throws Exception {
            var result = template.instantiate(new Item("RegularToy"));

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(new BigDecimal("100"));
        }
    }

    @Nested
    @DisplayName("Rule with Else Section")
    class RuleWithElseSection {

        private static final String AGE_CATEGORY_SOURCE = """
                facts {
                    customer: work.spell.iskibal.e2e.Customer
                }
                outputs {
                    category: String := "unknown"
                }
                rule AGE1 "Age category"
                when
                    customer.age >= 18
                then
                    category := "adult"
                else
                    category := "minor"
                end
                """;

        private static CompiledRuleTemplate.Success template;

        @BeforeAll
        static void compileOnce() {
            var compiled = RuleTestBuilder.forSource(AGE_CATEGORY_SOURCE).compile();
            assertThat(compiled.isSuccess()).isTrue();
            template = (CompiledRuleTemplate.Success) compiled;
        }

        @Test
        @DisplayName("Then branch executes when condition is true")
        void thenBranchExecutesWhenConditionTrue() throws Exception {
            var result = template.instantiate(new Customer("Alice", 25));

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("category")).isEqualTo("adult");
        }

        @Test
        @DisplayName("Else branch executes when condition is false")
        void elseBranchExecutesWhenConditionFalse() throws Exception {
            var result = template.instantiate(new Customer("Bob", 15));

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("category")).isEqualTo("minor");
        }
    }

    @Nested
    @DisplayName("Multiple Rules")
    class MultipleRules {

        @Test
        @DisplayName("Multiple rules execute in order")
        void multipleRulesExecuteInOrder() throws Exception {
            String source = """
                    facts {
                        order: work.spell.iskibal.e2e.Order
                    }
                    outputs {
                        discount: BigDecimal := 0
                        message: String := ""
                    }
                    rule DISC1 "High value discount"
                    when
                        order.total > 100
                    then
                        discount := 10
                    end

                    rule MSG1 "Add message for high value orders"
                    when
                        order.total > 100
                    then
                        message := "Thank you for your large order!"
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Order(new BigDecimal("150"))).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(rules.<String>getOutput("message")).isEqualTo("Thank you for your large order!");
        }

        @Test
        @DisplayName("Later rule can accumulate output from earlier rule")
        void laterRuleCanAccumulateOutput() throws Exception {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.Customer
                    }
                    outputs {
                        discount: BigDecimal := 0
                    }
                    rule DISC1 "First discount"
                    when
                        customer.age >= 18
                    then
                        discount := 5
                    end

                    rule DISC2 "Second discount"
                    when
                        customer.age >= 30
                    then
                        discount := discount + 5
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Customer("Alice", 35)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            // Both rules fire: 5 + 5 = 10
            assertThat(rules.<BigDecimal>getOutput("discount")).isEqualByComparingTo(BigDecimal.TEN);
        }
    }

    @Nested
    @DisplayName("Cascading Rules")
    class CascadingRules {

        @Test
        @DisplayName("Multiple rules can modify the same output")
        void multipleRulesCanModifySameOutput() throws Exception {
            String source = """
                    facts {
                        order: work.spell.iskibal.e2e.Order
                    }
                    outputs {
                        totalDiscount: BigDecimal := 0
                    }
                    rule BASE1 "Base discount"
                    when
                        order.total > 50
                    then
                        totalDiscount := 5
                    end

                    rule BONUS1 "Bonus discount"
                    when
                        order.total > 100
                    then
                        totalDiscount := totalDiscount + 10
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Order(new BigDecimal("150"))).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            // Both rules fire: 5 + 10 = 15
            assertThat(rules.<BigDecimal>getOutput("totalDiscount")).isEqualByComparingTo(new BigDecimal("15"));
        }
    }

    @Nested
    @DisplayName("Arithmetic Operations")
    class ArithmeticOperations {

        @Test
        @DisplayName("BigDecimal addition works correctly")
        void bigDecimalAddition() throws Exception {
            String source = """
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule CALC "Calculate sum"
                    when
                        true
                    then
                        result := 10 + 5
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("15"));
        }

        @Test
        @DisplayName("BigDecimal multiplication works correctly")
        void bigDecimalMultiplication() throws Exception {
            String source = """
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule CALC "Calculate product"
                    when
                        true
                    then
                        result := 10 * 5
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("50"));
        }
    }

    @Nested
    @DisplayName("Navigation Expressions")
    class NavigationExpressions {

        @Test
        @DisplayName("Chained navigation works correctly")
        void chainedNavigation() throws Exception {
            String source = """
                    facts {
                        order: work.spell.iskibal.e2e.Order
                    }
                    outputs {
                        customerName: String := ""
                    }
                    rule NAV1 "Get customer name"
                    when
                        true
                    then
                        customerName := order.customer.name
                    end
                    """;

            var customer = new Customer("Alice", 30);
            var order = new Order(new BigDecimal("100"), customer);

            var result = RuleTestBuilder.forSource(source).withFact(order).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("customerName")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("Chained navigation performs null checks")
        void chainedNavigationWithNullchecks() throws Exception {
            String source = """
                    facts {
                        order: work.spell.iskibal.e2e.Order
                    }
                    outputs {
                        message: String := ""
                    }
                    rule NAV1 "Congratulate customer Alice"
                    when
                        order.customer.name = "Alice"
                    then
                        message := "Congratulations, Alice!"
                    end
                    """;

            // order with intentional null cusomter
            var order = new Order(new BigDecimal("100"), null);

            var result = RuleTestBuilder.forSource(source).withFact(order).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("message")).isEqualTo("");
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

    @Nested
    @DisplayName("when clauses")
    class WhenClauses {
        @Test
        @DisplayName("with commas")
        void withCommas() throws Exception {
            String source = """
                    facts {
                        firstName: String
                        lastName: String
                    }
                    outputs {
                        result: String := ""
                    }

                    rule RULE1 "When with comma"
                    when
                        firstName = "John",
                        lastName = "Doe"
                    then
                        result := "We got him"
                    end
                    """;

            var rule = compileSourceSuccessfully(source);

            var resultWithJohn = rule.instantiate("John", "Doe");
            var resultWithJane = rule.instantiate("Jane", "Doe");

            assertResultSuccess(resultWithJohn);
            assertResultSuccess(resultWithJane);

            var rulesWithJohn = resultWithJohn.rules().orElseThrow();
            var rulesWithJane = resultWithJane.rules().orElseThrow();

            rulesWithJohn.evaluate();
            rulesWithJane.evaluate();

            assertThat(rulesWithJohn.<String>getOutput("result")).isEqualTo("We got him");
            assertThat(rulesWithJane.<String>getOutput("result")).isEmpty();
        }

        @Test
        @DisplayName("allow local variable assignment")
        void allowLocalVariableAssignment() throws Exception {
            String source = """
                    		facts {
                    		  name: String
                    		}
                    		outputs {
                    		  result: String
                    		}

                    		rule RULE1 "When with let"
                    		when
                    		    let thisWillHaveNoEffect := true
                    		    let fullName := name concat: " Doe"
                    		    fullName = "John Doe"
                    		then
                    		    result := "Got him"
                    		else
                    		   result := "Not our guy"
                    		end
                    """;

            var rule = compileSourceSuccessfully(source);

            var resultWithJohn = rule.instantiate("John");
            var resultWithJane = rule.instantiate("Jane");

            assertResultSuccess(resultWithJohn);
            assertResultSuccess(resultWithJane);

            var rulesWithJohn = resultWithJohn.rules().orElseThrow();
            var rulesWithJane = resultWithJane.rules().orElseThrow();

            rulesWithJohn.evaluate();
            rulesWithJane.evaluate();

            assertThat(rulesWithJohn.<String>getOutput("result")).isEqualTo("Got him");
            assertThat(rulesWithJane.<String>getOutput("result")).isEqualTo("Not our guy");
        }

    }

    @Nested
    @DisplayName("Strings")
    class Strings {
        @Test
        @DisplayName("can be enclosed in single and double quotes and escape these")
        void canBeEnclosedInSingleAndDoubleQuotes() throws Exception {
            String source = """
                        outputs {
                          single: String
                          doubleQuoted: String
                        }

                        rule QUOTES "quotes work"
                        when
                          true
                        then
                          single := 'Single quotes work and \\'do what they should\\''
                          doubleQuoted := "double quotes \\"also\\" work"
                        end
                """;

            var result = RuleTestBuilder.forSource(source).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("single")).isEqualTo("Single quotes work and 'do what they should'");
            assertThat(rules.<String>getOutput("doubleQuoted")).isEqualTo("double quotes \"also\" work");
        }

        @Test
        @DisplayName("can be expanded using template expressions")
        void canBeExpandedUsingTemplateExpressions() throws Exception {
            String source = """
                        outputs {
                          result: String
                        }
                
                        rule TEMPLATE "using template expressions"
                        when
                          true
                        then
                          let notATemplateExpression := "a ${bar}"
                          result := $"For ${notATemplateExpression} we calculated ${5 + 10}"
                        end
                """;

            var result = RuleTestBuilder.forSource(source).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("For a ${bar} we calculated 15");
        }
    }

    private static Success compileSourceSuccessfully(String source) {
        var compiled = RuleTestBuilder.forSource(source).compile();
        assertThat(compiled).isInstanceOf(CompiledRuleTemplate.Success.class);
        return (CompiledRuleTemplate.Success) compiled;
    }

}
