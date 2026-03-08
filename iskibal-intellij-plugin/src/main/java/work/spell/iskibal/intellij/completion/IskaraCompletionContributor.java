package work.spell.iskibal.intellij.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import work.spell.iskibal.intellij.IskaraLanguage;

import java.util.List;

/// Provides code completion for Iskara files.
///
/// Offers keyword completions and section snippets.
public final class IskaraCompletionContributor extends CompletionContributor {

    private static final List<String> KEYWORDS = List.of(
            "module", "imports", "facts", "globals", "outputs",
            "rule", "template", "decision", "table", "data",
            "when", "then", "else", "end", "where", "let",
            "true", "false", "null");

    public IskaraCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(IskaraLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(CompletionParameters parameters,
                                                  ProcessingContext context,
                                                  CompletionResultSet result) {

                        // Keyword completions
                        for (var keyword : KEYWORDS) {
                            result.addElement(LookupElementBuilder.create(keyword).bold());
                        }

                        // Section snippets
                        result.addElement(snippet("facts { ... }", "facts {\n\t\n}"));
                        result.addElement(snippet("globals { ... }", "globals {\n\t\n}"));
                        result.addElement(snippet("outputs { ... }", "outputs {\n\t\n}"));
                        result.addElement(snippet("imports { ... }", "imports {\n\t\n}"));
                        result.addElement(snippet("rule ... end",
                                "rule  \"\"\nwhen\n\t\nthen\n\t\nend"));
                    }
                });
    }

    private static LookupElementBuilder snippet(String presentation, String insertText) {
        return LookupElementBuilder.create(insertText)
                .withPresentableText(presentation)
                .withInsertHandler((ctx, item) -> {
                    // The insert text is already set via create()
                });
    }
}
