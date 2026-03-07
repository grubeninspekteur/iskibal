package work.spell.iskibal.compiler.common.api;

import org.jspecify.annotations.Nullable;

/// Represents a semantic diagnostic message.
///
/// @param severity
///            the severity level of the diagnostic
/// @param message
///            the diagnostic message
/// @param elementName
///            the name of the element where the diagnostic occurred, or `null`
///            if not applicable
public record SemanticDiagnostic(Severity severity, String message, @Nullable String elementName) {
    public enum Severity {
        ERROR, WARNING
    }

    public static SemanticDiagnostic error(String message) {
        return new SemanticDiagnostic(Severity.ERROR, message, null);
    }

    public static SemanticDiagnostic error(String message, String elementName) {
        return new SemanticDiagnostic(Severity.ERROR, message, elementName);
    }

    public static SemanticDiagnostic warning(String message) {
        return new SemanticDiagnostic(Severity.WARNING, message, null);
    }

    public static SemanticDiagnostic warning(String message, String elementName) {
        return new SemanticDiagnostic(Severity.WARNING, message, elementName);
    }

    @Override
    public String toString() {
        if (elementName != null) {
            return "%s: %s (at '%s')".formatted(severity, message, elementName);
        }
        return "%s: %s".formatted(severity, message);
    }
}
