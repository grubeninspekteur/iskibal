package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/**
 * End-to-end tests for Smalltalk-style message sending. Exercises keyword and
 * multi-keyword messages.
 */
class MessagingE2ETest {

    @Nested
    @DisplayName("Single Keyword Messages")
    class SingleKeywordMessages {

        @Test
        @DisplayName("Unary message")
        void unaryMessage() throws Exception {
            String source = """
                    facts {
                        calculator: work.spell.iskibal.e2e.Calculator
                    }
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule ADD1 "Add to value"
                    when true
                    then
                        result := calculator negate
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Calculator(BigDecimal.TEN)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("result")).isEqualTo(BigDecimal.valueOf(-10));
        }

        @Test
        @DisplayName("Single keyword message with argument")
        void singleKeywordMessageWithArgument() throws Exception {
            String source = """
                    facts {
                        calculator: work.spell.iskibal.e2e.Calculator
                    }
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule ADD1 "Add to value"
                    when true
                    then
                        result := calculator add: 5
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Calculator(BigDecimal.TEN)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("15"));
        }

        @Test
        @DisplayName("Single keyword message with expression argument")
        void singleKeywordMessageWithExpressionArgument() throws Exception {
            String source = """
                    facts {
                        calculator: work.spell.iskibal.e2e.Calculator
                    }
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule MUL1 "Multiply value"
                    when true
                    then
                        result := calculator multiply: (2 + 3)
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).withFact(new Calculator(BigDecimal.TEN)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("50"));
        }
    }

    @Nested
    @DisplayName("Multi-Keyword Messages")
    class MultiKeywordMessages {

        @Test
        @DisplayName("Multi-keyword message combines keywords into method name")
        void multiKeywordMessageCombinesKeywords() throws Exception {
            String source = """
                    facts {
                        calculator: work.spell.iskibal.e2e.Calculator
                    }
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule SCALE1 "Scale and add"
                    when true
                    then
                        result := calculator scaleBy: 3 thenAdd: 5
                    end
                    """;

            // Calculator(10).scaleByThenAdd(3, 5) = 10 * 3 + 5 = 35
            var result = RuleTestBuilder.forSource(source).withFact(new Calculator(BigDecimal.TEN)).build();

            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<BigDecimal>getOutput("result")).isEqualByComparingTo(new BigDecimal("35"));
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
