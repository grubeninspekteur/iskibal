package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.testing.RuleTestBuilder;
import work.spell.iskibal.testing.RuleTestResult;

/**
 * End-to-end tests for semantic analysis errors. Tests that the compiler
 * correctly rejects invalid rules with appropriate error messages.
 */
class CompilationFailureE2ETest {

    @Nested
    @DisplayName("Duplicate Identifiers")
    class DuplicateIdentifiers {

        @Test
        @DisplayName("Duplicate rule IDs are rejected")
        void duplicateRuleIdsRejected() {
            String source = """
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule DUP1 "First rule"
                    when true
                    then
                        result := 1
                    end

                    rule DUP1 "Second rule with same ID"
                    when true
                    then
                        result := 2
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStage()).isEqualTo("analysis");
            assertThat(result.getErrors()).anyMatch(
                    error -> error.toLowerCase().contains("duplicate") || error.toLowerCase().contains("dup1"));
        }

        @Test
        @DisplayName("Duplicate fact names are rejected")
        void duplicateFactNamesRejected() {
            String source = """
                    facts {
                        item: work.spell.iskibal.e2e.Item
                        item: work.spell.iskibal.e2e.Order
                    }
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule R1 "Rule"
                    when true
                    then
                        result := 1
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStage()).isEqualTo("analysis");
            assertThat(result.getErrors()).anyMatch(
                    error -> error.toLowerCase().contains("duplicate") || error.toLowerCase().contains("item"));
        }

        @Test
        @DisplayName("Duplicate output names are rejected")
        void duplicateOutputNamesRejected() {
            String source = """
                    outputs {
                        result: BigDecimal := 0
                        result: String := ""
                    }
                    rule R1 "Rule"
                    when true
                    then
                        result := 1
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStage()).isEqualTo("analysis");
            assertThat(result.getErrors()).anyMatch(
                    error -> error.toLowerCase().contains("duplicate") || error.toLowerCase().contains("result"));
        }
    }

    @Nested
    @DisplayName("Invalid Assignments")
    class InvalidAssignments {

        @Test
        @DisplayName("Assigning to facts is rejected")
        void assigningToFactsRejected() {
            String source = """
                    facts {
                        item: work.spell.iskibal.e2e.Item
                    }
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule R1 "Rule"
                    when true
                    then
                        item := null
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStage()).isEqualTo("analysis");
            assertThat(result.getErrors()).anyMatch(error -> error.toLowerCase().contains("assign")
                    || error.toLowerCase().contains("fact") || error.toLowerCase().contains("item"));
        }

        @Test
        @DisplayName("Assigning to globals is rejected")
        void assigningToGlobalsRejected() {
            String source = """
                    globals {
                        config: work.spell.iskibal.e2e.Item
                    }
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule R1 "Rule"
                    when true
                    then
                        @config := null
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            // Currently fails at compilation stage because global fields are final
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStage()).isIn("analysis", "compilation");
        }

    }

    @Nested
    @DisplayName("Undefined Identifiers")
    class UndefinedIdentifiers {

        @Test
        @DisplayName("Undefined identifier in when clause is rejected")
        void undefinedIdentifierInWhenClauseRejected() {
            String source = """
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule R1 "Rule"
                    when
                        unknownVariable = true
                    then
                        result := 1
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStage()).isEqualTo("analysis");
            assertThat(result.getErrors()).anyMatch(error -> error.toLowerCase().contains("undefined")
                    || error.toLowerCase().contains("unknown") || error.toLowerCase().contains("unknownvariable"));
        }

        @Test
        @DisplayName("Undefined identifier in then clause is rejected")
        void undefinedIdentifierInThenClauseRejected() {
            String source = """
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule R1 "Rule"
                    when true
                    then
                        unknownOutput := 1
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStage()).isEqualTo("analysis");
            assertThat(result.getErrors()).anyMatch(error -> error.toLowerCase().contains("undefined")
                    || error.toLowerCase().contains("unknown") || error.toLowerCase().contains("unknownoutput"));
        }
    }

    @Nested
    @DisplayName("When clauses")
    class WhenClauses {
        @Test
        @DisplayName("disallow assigning to anything else but a local variable")
        void disallowAssigningToAnythingElseButALocalVariable() {
            String source = """
                    facts {
                      customer: work.spell.iskibal.e2e.MutableCustomer
                    }

                    rule R1 "Rule"
                    when
                        customer.name := "John"
                        true
                    then
                        customer.name = "Jane"
                    end
                    """;

            RuleTestResult result = RuleTestBuilder.forSource(source).build();

            assertThat(result).isInstanceOf(RuleTestResult.AnalysisFailure.class);
        }

        @Test
        @DisplayName("disallow boolean expressions at any place except the last line outside of comma")
        void disallowDisconnectedBooleanExpressionStatements() {
            String source = """
                    facts {
                      firstName: String
                      lastName: String
                    }

                    outputs {
                      result: String
                    }

                    rule R1 "Rule"
                    when
                        firstName = "John"
                        lastName = "Doe"
                    then
                        result := "42"
                    end
                    """;

            RuleTestResult result = RuleTestBuilder.forSource(source).withFacts("John", "Doe").build();

            assertThat(result).isInstanceOf(RuleTestResult.AnalysisFailure.class);
        }

        @Test
        @DisplayName("disallow assigning to outputs")
        void disallowAssigningToOutputs() {
            String source = """
                    outputs {
                        result: BigDecimal := 0
                    }
                    rule R1 "Rule"
                    when
                        result := 5
                    then
                        result := 10
                    end
                    """;

            var result = RuleTestBuilder.forSource(source).build();

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStage()).isEqualTo("analysis");
            assertThat(result.getErrors()).anyMatch(error -> error.toLowerCase().contains("assign")
                    || error.toLowerCase().contains("when") || error.toLowerCase().contains("condition"));
        }
    }

}
