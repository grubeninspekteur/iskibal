package work.spell.iskibal.testing;

import module java.base;

/// Result of in-memory Java compilation.
public sealed interface InMemoryCompilationResult
        permits InMemoryCompilationResult.Success, InMemoryCompilationResult.Failure {

    /// Successful compilation result containing the compiled class.
    record Success(Class<?> compiledClass, String className) implements InMemoryCompilationResult {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Optional<Class<?>> getCompiledClass() {
            return Optional.of(compiledClass);
        }

        @Override
        public List<String> getDiagnostics() {
            return List.of();
        }
    }

    /// Failed compilation result containing diagnostic messages.
    record Failure(List<String> diagnostics) implements InMemoryCompilationResult {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public Optional<Class<?>> getCompiledClass() {
            return Optional.empty();
        }

        @Override
        public List<String> getDiagnostics() {
            return diagnostics;
        }
    }

    /// Returns true if the compilation was successful.
    boolean isSuccess();

    /// Returns the compiled class if successful.
    Optional<Class<?>> getCompiledClass();

    /// Returns diagnostic messages (errors for failure, warnings for success).
    List<String> getDiagnostics();
}
