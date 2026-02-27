package work.spell.iskibal.parser.diagnostic;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.List;

/// ANTLR error listener that collects syntax errors as diagnostics.
public class IskaraDiagnosticListener extends BaseErrorListener {

    private final String sourceName;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public IskaraDiagnosticListener(String sourceName) {
        this.sourceName = sourceName;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
            String msg, RecognitionException e) {
        SourceLocation location = SourceLocation.at(sourceName, line, charPositionInLine + 1);
        diagnostics.add(Diagnostic.error(msg, location));
    }

    /// Returns all collected diagnostics.
    public List<Diagnostic> getDiagnostics() {
        return List.copyOf(diagnostics);
    }

    /// Returns true if any errors were recorded.
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    /// Adds a diagnostic manually.
    public void addDiagnostic(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    /// Adds an error diagnostic at the given location.
    public void addError(String message, int line, int column) {
        diagnostics.add(Diagnostic.error(message, SourceLocation.at(sourceName, line, column)));
    }

    /// Adds a warning diagnostic at the given location.
    public void addWarning(String message, int line, int column) {
        diagnostics.add(Diagnostic.warning(message, SourceLocation.at(sourceName, line, column)));
    }
}
