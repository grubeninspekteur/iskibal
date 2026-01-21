package work.spell.iskibal.parser.diagnostic;

/**
 * Represents a diagnostic message from parsing.
 *
 * @param severity the severity level of the diagnostic
 * @param message the diagnostic message
 * @param location the source location where the diagnostic occurred
 */
public record Diagnostic(
        Severity severity,
        String message,
        SourceLocation location
) {
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public static Diagnostic error(String message, SourceLocation location) {
        return new Diagnostic(Severity.ERROR, message, location);
    }

    public static Diagnostic warning(String message, SourceLocation location) {
        return new Diagnostic(Severity.WARNING, message, location);
    }

    public static Diagnostic info(String message, SourceLocation location) {
        return new Diagnostic(Severity.INFO, message, location);
    }

    @Override
    public String toString() {
        return "%s: %s at %s".formatted(severity, message, location);
    }
}
