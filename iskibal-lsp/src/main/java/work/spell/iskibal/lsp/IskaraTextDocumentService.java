package work.spell.iskibal.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.parser.api.ParseOptions;
import work.spell.iskibal.parser.api.ParseResult;
import work.spell.iskibal.parser.api.Parser;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/// Handles text document events (open, change, close) and provides
/// diagnostics and completion for Iskara files.
public final class IskaraTextDocumentService implements TextDocumentService {

    private static final List<String> KEYWORDS = List.of(
            "module", "imports", "facts", "globals", "outputs",
            "rule", "template", "decision", "table", "data",
            "when", "then", "else", "end", "where", "let",
            "true", "false", "null");

    private static final List<String> SECTION_KEYWORDS = List.of(
            "facts", "globals", "outputs", "imports", "rule",
            "template", "decision", "data");

    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, ParseResult<RuleModule>> parseResults = new ConcurrentHashMap<>();
    private final Parser parser = Parser.load();
    private LanguageClient client;

    void connect(LanguageClient client) {
        this.client = client;
    }

    // -- Document sync ---------------------------------------------------

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var uri = params.getTextDocument().getUri();
        documents.put(uri, params.getTextDocument().getText());
        validateAndPublish(uri);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var uri = params.getTextDocument().getUri();
        // Full sync: take the last content change
        var changes = params.getContentChanges();
        if (!changes.isEmpty()) {
            documents.put(uri, changes.getLast().getText());
            validateAndPublish(uri);
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        var uri = params.getTextDocument().getUri();
        documents.remove(uri);
        parseResults.remove(uri);
        // Clear diagnostics
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // Already validated on change
    }

    // -- Diagnostics -----------------------------------------------------

    private void validateAndPublish(String uri) {
        var source = documents.get(uri);
        if (source == null) return;

        var sourceName = sourceNameFromUri(uri);
        var options = ParseOptions.iskara(sourceName);
        var result = parser.parse(source, options);
        parseResults.put(uri, result);

        var diagnostics = result.getDiagnostics().stream()
                .map(IskaraTextDocumentService::toLspDiagnostic)
                .toList();

        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    private static Diagnostic toLspDiagnostic(work.spell.iskibal.parser.diagnostic.Diagnostic d) {
        var loc = d.location();
        // LSP uses 0-based lines/columns, Iskara uses 1-based
        int line = Math.max(0, loc.line() - 1);
        int col = Math.max(0, loc.column() - 1);
        int endCol = col + Math.max(1, loc.length());

        var range = new Range(new Position(line, col), new Position(line, endCol));
        var severity = switch (d.severity()) {
            case ERROR -> DiagnosticSeverity.Error;
            case WARNING -> DiagnosticSeverity.Warning;
            case INFO -> DiagnosticSeverity.Information;
        };

        var lspDiag = new Diagnostic(range, d.message());
        lspDiag.setSeverity(severity);
        lspDiag.setSource("iskara");
        return lspDiag;
    }

    // -- Completion ------------------------------------------------------

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
            CompletionParams params) {
        var items = new ArrayList<CompletionItem>();

        // Keyword completions
        for (var keyword : KEYWORDS) {
            var item = new CompletionItem(keyword);
            item.setKind(CompletionItemKind.Keyword);
            items.add(item);
        }

        // Section snippet completions
        addSnippet(items, "facts", "facts {\n\t$0\n}");
        addSnippet(items, "globals", "globals {\n\t$0\n}");
        addSnippet(items, "outputs", "outputs {\n\t$0\n}");
        addSnippet(items, "imports", "imports {\n\t$0\n}");
        addSnippet(items, "rule", "rule ${1:Name} \"${2:description}\"\nwhen\n\t$3\nthen\n\t$4\nend");

        // Identifier completions from the parsed model
        var uri = params.getTextDocument().getUri();
        var result = parseResults.get(uri);
        if (result != null) {
            result.getValue().ifPresent(module -> addModelCompletions(module, items));
        }

        return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    private void addSnippet(List<CompletionItem> items, String label, String snippet) {
        var item = new CompletionItem(label + " {...}");
        item.setKind(CompletionItemKind.Snippet);
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        item.setInsertText(snippet);
        item.setSortText("zz_" + label); // Sort snippets after keywords
        items.add(item);
    }

    private void addModelCompletions(RuleModule module, List<CompletionItem> items) {
        for (var fact : module.facts()) {
            var item = new CompletionItem(fact.name());
            item.setKind(CompletionItemKind.Variable);
            item.setDetail("fact: " + fact.type());
            items.add(item);
        }
        for (var global : module.globals()) {
            var item = new CompletionItem(global.name());
            item.setKind(CompletionItemKind.Variable);
            item.setDetail("global: " + global.type());
            items.add(item);
        }
        for (var output : module.outputs()) {
            var item = new CompletionItem(output.name());
            item.setKind(CompletionItemKind.Variable);
            item.setDetail("output: " + output.type());
            items.add(item);
        }
        for (var rule : module.rules()) {
            var item = new CompletionItem(rule.id());
            item.setKind(CompletionItemKind.Class);
            item.setDetail("rule");
            items.add(item);
        }
    }

    // -- Helpers ----------------------------------------------------------

    private static String sourceNameFromUri(String uri) {
        try {
            return Path.of(URI.create(uri)).getFileName().toString();
        } catch (Exception e) {
            return "<source>";
        }
    }
}
