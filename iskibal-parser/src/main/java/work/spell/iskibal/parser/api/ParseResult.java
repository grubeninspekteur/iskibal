package work.spell.iskibal.parser.api;

import work.spell.iskibal.parser.diagnostic.Diagnostic;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/// Result of a parse operation, either success with a value and optional
/// warnings, or failure with a list of errors.
///
/// @param <T>
///            the type of the parsed value
public sealed interface ParseResult<T> permits ParseResult.Success, ParseResult.Failure {

    /// Successful parse result containing the parsed value and any warnings.
    record Success<T>(T value, List<Diagnostic> warnings) implements ParseResult<T> {
        public Success(T value) {
            this(value, List.of());
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Optional<T> getValue() {
            return Optional.of(value);
        }

        @Override
        public List<Diagnostic> getDiagnostics() {
            return warnings;
        }
    }

    /// Failed parse result containing the list of errors.
    record Failure<T>(List<Diagnostic> errors) implements ParseResult<T> {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public Optional<T> getValue() {
            return Optional.empty();
        }

        @Override
        public List<Diagnostic> getDiagnostics() {
            return errors;
        }
    }

    /// Returns true if the parse was successful.
    boolean isSuccess();

    /// Returns the parsed value if successful, empty otherwise.
    Optional<T> getValue();

    /// Returns all diagnostics (warnings for success, errors for failure).
    List<Diagnostic> getDiagnostics();

    /// Maps the value if successful, preserving warnings.
    default <U> ParseResult<U> map(Function<T, U> mapper) {
        return switch (this) {
            case Success<T>(var value, var warnings) -> new Success<>(mapper.apply(value), warnings);
            case Failure<T>(var errors) -> new Failure<>(errors);
        };
    }

    /// Flat maps the value if successful.
    default <U> ParseResult<U> flatMap(Function<T, ParseResult<U>> mapper) {
        return switch (this) {
            case Success<T>(var value, var warnings) -> {
                ParseResult<U> result = mapper.apply(value);
                yield switch (result) {
                    case Success<U>(var v, var w) -> new Success<>(v, combineDiagnostics(warnings, w));
                    case Failure<U> f -> f;
                };
            }
            case Failure<T>(var errors) -> new Failure<>(errors);
        };
    }

    private static List<Diagnostic> combineDiagnostics(List<Diagnostic> a, List<Diagnostic> b) {
        if (a.isEmpty())
            return b;
        if (b.isEmpty())
            return a;
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
