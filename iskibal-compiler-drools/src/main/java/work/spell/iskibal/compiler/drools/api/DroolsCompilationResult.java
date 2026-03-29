package work.spell.iskibal.compiler.drools.api;

import module java.base;

/// Result of Drools DRL compilation, containing generated source files.
///
/// File paths in the [Success] result are [Path] objects representing relative
/// paths to the generated files. The map preserves insertion order so that
/// the primary DRL file always comes first.
public sealed interface DroolsCompilationResult
        permits DroolsCompilationResult.Success, DroolsCompilationResult.Failure {

    /// Successful compilation result containing the generated DRL source files.
    ///
    /// Map keys are relative [Path]s (e.g. `Path.of("rules/pricing.drl")`),
    /// values are source content. The map is insertion-ordered; the DRL file
    /// is always the first entry. May include additional `.java` files for
    /// output holder POJOs.
    record Success(LinkedHashMap<Path, String> sourceFiles) implements DroolsCompilationResult {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Optional<Map<Path, String>> getSourceFiles() {
            return Optional.of(sourceFiles);
        }

        @Override
        public List<String> getErrors() {
            return List.of();
        }

        /// Returns the primary DRL source file content.
        ///
        /// The DRL file is the first entry in the insertion-ordered map.
        public String getDrlSource() {
            return sourceFiles.entrySet().stream()
                    .filter(e -> e.getKey().toString().endsWith(".drl"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("");
        }
    }

    /// Failed compilation result containing error messages.
    record Failure(List<String> errors) implements DroolsCompilationResult {

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public Optional<Map<Path, String>> getSourceFiles() {
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
    Optional<Map<Path, String>> getSourceFiles();

    /// Returns error messages if failed, empty list otherwise.
    List<String> getErrors();

    /// Returns the primary DRL source file content, or empty string if not successful.
    default String getDrlSource() {
        return getSourceFiles().flatMap(
                files -> files.entrySet().stream().filter(e -> e.getKey().toString().endsWith(".drl"))
                        .map(Map.Entry::getValue).findFirst())
                .orElse("");
    }
}
