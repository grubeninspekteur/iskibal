package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/// End-to-end tests for Smalltalk-style message sending. Exercises keyword and
/// multi-keyword messages.
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

    @Nested
    @DisplayName("Default Messages")
    class DefaultMessages {

        @Test
        @DisplayName("Default message with ! invokes get()")
        void defaultMessageInvokesApply() throws Exception {
            String source = """
                    facts {
                        supplier: java.util.function.Supplier
                    }
                    outputs {
                        result: Object
                    }
                    rule DEFAULT "Invoke default message"
                    when true
                    then
                        result := supplier!
                    end
                    """;

            java.util.function.Supplier<String> supplier = () -> "Hello from supplier";

            var result = RuleTestBuilder.forSource(source).withFact(supplier).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<Object>getOutput("result")).isEqualTo("Hello from supplier");
        }

        @Test
        @DisplayName("Default message followed by another message")
        void defaultMessageThenAnotherMessage() throws Exception {
            String source = """
                    facts {
                        supplier: java.util.function.Supplier
                    }
                    outputs {
                        result: Object
                        matches: boolean
                    }
                    rule DEFAULT "Default message then comparison"
                    when true
                    then
                        result := supplier!
                        matches := result = "Hello World"
                    end
                    """;

            java.util.function.Supplier<String> supplier = () -> "Hello World";

            var result = RuleTestBuilder.forSource(source).withFact(supplier).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<Object>getOutput("result")).isEqualTo("Hello World");
            assertThat(rules.<Boolean>getOutput("matches")).isTrue();
        }
    }

    @Nested
    @DisplayName("Boolean Messages")
    class BooleanMessages {

        @Test
        @DisplayName("ifTrue: executes block when true")
        void ifTrueExecutesBlock() throws Exception {
            String source = """
                    outputs {
                        result: String := "not set"
                    }
                    rule IFTRUE "Test ifTrue:"
                    when true
                    then
                        true ifTrue: [result := "it was true"]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("it was true");
        }

        @Test
        @DisplayName("ifFalse: executes block when false")
        void ifFalseExecutesBlock() throws Exception {
            String source = """
                    outputs {
                        result: String := "not set"
                    }
                    rule IFFALSE "Test ifFalse:"
                    when true
                    then
                        false ifFalse: [result := "it was false"]
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("it was false");
        }

        @Test
        @DisplayName("or: returns true if either is true")
        void orMessage() throws Exception {
            String source = """
                    outputs {
                        bothFalse: boolean
                        oneTrue: boolean
                        bothTrue: boolean
                    }
                    rule OR "Test or:"
                    when true
                    then
                        bothFalse := false or: false
                        oneTrue := false or: true
                        bothTrue := true or: true
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<Boolean>getOutput("bothFalse")).isFalse();
            assertThat(rules.<Boolean>getOutput("oneTrue")).isTrue();
            assertThat(rules.<Boolean>getOutput("bothTrue")).isTrue();
        }

        @Test
        @DisplayName("and: returns true only if both are true")
        void andMessage() throws Exception {
            String source = """
                    outputs {
                        bothFalse: boolean
                        oneTrue: boolean
                        bothTrue: boolean
                    }
                    rule AND "Test and:"
                    when true
                    then
                        bothFalse := false and: false
                        oneTrue := false and: true
                        bothTrue := true and: true
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<Boolean>getOutput("bothFalse")).isFalse();
            assertThat(rules.<Boolean>getOutput("oneTrue")).isFalse();
            assertThat(rules.<Boolean>getOutput("bothTrue")).isTrue();
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
