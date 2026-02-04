package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/**
 * End-to-end tests for general syntax features including comments and
 * special identifier quoting.
 */
class SyntaxE2ETest {

    @Nested
    @DisplayName("Comments")
    class Comments {

        @Test
        @DisplayName("Single-line comments are ignored")
        void singleLineComments() throws Exception {
            String source = """
                    // This is a comment
                    outputs {
                        result: String // inline comment
                    }
                    // Another comment
                    rule COMMENT "Test comments"
                    when true // condition comment
                    then
                        // action comment
                        result := "OK"
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("OK");
        }

        @Test
        @DisplayName("Multi-line comments are ignored")
        void multiLineComments() throws Exception {
            String source = """
                    /* This is a
                       multi-line comment */
                    outputs {
                        result: String
                    }
                    rule COMMENT "Test multi-line comments"
                    when true
                    then
                        /* another
                           multi-line */
                        result := "OK"
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("result")).isEqualTo("OK");
        }
    }

    @Nested
    @DisplayName("Backtick Quoted Identifiers")
    class BacktickQuotedIdentifiers {

        @Test
        @DisplayName("Backtick allows spaces in identifiers")
        void backtickAllowsSpaces() throws Exception {
            String source = """
                    outputs {
                        `my result`: String
                    }
                    rule BACKTICK "Test backtick identifiers"
                    when true
                    then
                        `my result` := "OK"
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("my result")).isEqualTo("OK");
        }

        @Test
        @DisplayName("Backtick allows reserved words as identifiers")
        void backtickAllowsReservedWords() throws Exception {
            String source = """
                    outputs {
                        `when`: String
                        `then`: String
                    }
                    rule BACKTICK "Reserved words as identifiers"
                    when true
                    then
                        `when` := "condition"
                        `then` := "action"
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();
            assertResultSuccess(result);

            var rules = result.rules().orElseThrow();
            rules.evaluate();

            assertThat(rules.<String>getOutput("when")).isEqualTo("condition");
            assertThat(rules.<String>getOutput("then")).isEqualTo("action");
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
