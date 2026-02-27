package work.spell.iskibal.testing;

import module java.base;

/// Result of compiling a rule source into a reusable template.
///
/// This allows tests to compile once and instantiate multiple times with
/// different arguments, improving test performance for parameterized tests.
public sealed interface CompiledRuleTemplate permits CompiledRuleTemplate.Success, CompiledRuleTemplate.Failure {

    /// Successfully compiled rule template.
    record Success(Class<?> rulesClass, String generatedSource) implements CompiledRuleTemplate {

        /// Creates a new instance of the compiled rules with the given constructor
        /// arguments.
        ///
        /// @param args
        ///            constructor arguments (facts and globals)
        /// @return a RuleTestResult representing the instantiation result
        public RuleTestResult instantiate(Object... args) {
            try {
                CompiledRules rules = CompiledRules.instantiate(rulesClass, args);
                return new RuleTestResult.Success(rules, generatedSource);
            } catch (ReflectiveOperationException e) {
                return new RuleTestResult.CompilationFailure(List.of("Failed to instantiate rules: " + e.getMessage()),
                        generatedSource);
            }
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public RuleTestResult toResult() {
            return new RuleTestResult.CompilationFailure(
                    List.of("Template compiled successfully but no facts provided for instantiation"), generatedSource);
        }
    }

    /// Failed compilation.
    record Failure(RuleTestResult failureResult) implements CompiledRuleTemplate {

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public RuleTestResult toResult() {
            return failureResult;
        }
    }

    /// Returns true if compilation succeeded.
    boolean isSuccess();

    /// Converts this template to a RuleTestResult.
    ///
    /// For Success, this returns a failure indicating no facts were provided. For
    /// Failure, this returns the underlying failure result.
    RuleTestResult toResult();
}
