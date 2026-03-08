package work.spell.iskibal.intellij.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.antlr.v4.runtime.*;
import work.spell.iskibal.parser.IskaraLexer;
import work.spell.iskibal.parser.IskaraParser;

import java.util.ArrayList;
import java.util.List;

/// Runs the ANTLR parser on Iskara files in the background and publishes
/// parse errors as IntelliJ annotations (red squiggles).
public final class IskaraExternalAnnotator
        extends ExternalAnnotator<IskaraExternalAnnotator.Input, List<IskaraExternalAnnotator.Issue>> {

    record Input(String text, Document document) {
    }

    record Issue(int line, int column, int length, String message) {
    }

    @Override
    public Input collectInformation(PsiFile file) {
        var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return null;
        return new Input(document.getText(), document);
    }

    @Override
    public List<Issue> doAnnotate(Input input) {
        if (input == null) return List.of();

        var issues = new ArrayList<Issue>();
        var charStream = CharStreams.fromString(input.text());
        var lexer = new IskaraLexer(charStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new IssueCollector(issues));

        var tokenStream = new CommonTokenStream(lexer);
        var parser = new IskaraParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(new IssueCollector(issues));

        parser.ruleModule(); // Parse the full file

        return issues;
    }

    @Override
    public void apply(PsiFile file, List<Issue> issues, AnnotationHolder holder) {
        var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return;

        for (var issue : issues) {
            int line = issue.line() - 1; // ANTLR is 1-based, Document is 0-based
            if (line < 0 || line >= document.getLineCount()) continue;

            int lineStart = document.getLineStartOffset(line);
            int lineEnd = document.getLineEndOffset(line);
            int start = Math.min(lineStart + issue.column(), lineEnd);
            int end = Math.min(start + Math.max(1, issue.length()), lineEnd);

            if (start >= end) {
                // Point to the end of the line if no valid range
                start = Math.max(lineStart, lineEnd - 1);
                end = lineEnd;
            }

            holder.newAnnotation(HighlightSeverity.ERROR, issue.message())
                    .range(new TextRange(start, end))
                    .create();
        }
    }

    private static final class IssueCollector extends BaseErrorListener {

        private final List<Issue> issues;

        IssueCollector(List<Issue> issues) {
            this.issues = issues;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            int length = 1;
            if (offendingSymbol instanceof Token token && token.getType() != Token.EOF) {
                length = token.getStopIndex() - token.getStartIndex() + 1;
            }
            issues.add(new Issue(line, charPositionInLine, length, msg));
        }
    }
}
