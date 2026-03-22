package work.spell.iskibal.compiler.drools.api;

import module java.base;

/// Result of Drools DRL compilation, containing generated source files.
public sealed interface DroolsCompilationResult
        permits DroolsCompilationResult.Success, DroolsCompilationResult.Failure {

    /// Successful compilation result containing the generated DRL source files.
    ///
    /// Map keys are file paths (e.g. `"rules/pricing.drl"`), values are source
    /// content. May include additional `.java` files for output holder POJOs.
    record Success(Map<String, String> sourceFiles) implements DroolsCompilationResult {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Optional<Map<String, String>> getSourceFiles() {
            return Optional.of(sourceFiles);
        }

        @Override
        public List<String> getErrors() {
            return List.of();
        }

        /// Returns the primary DRL source file content.
        public String getDrlSource() {
            return sourceFiles.entrySet().stream().filter(e -> e.getKey().endsWith(".drl"))
                    .map(Map.Entry::getValue).findFirst().orElse("");
        }
    }

    /// Failed compilation result containing error messages.
    record Failure(List<String> errors) implements DroolsCompilationResult {

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public Optional<Map<String, String>> getSourceFiles() {
            return Optional.empty();
        }

        @Override
        public List<String> getErrors() {
            return errors;
        }
    }

    /// Returns true if compilation was successful.
    boolean isSuccess();

    /// Returns generated source files if successful, empty otherwise.
    Optional<Map<String, String>> getSourceFiles();

    /// Returns error messages if failed, empty list otherwise.
    List<String> getErrors();
}
