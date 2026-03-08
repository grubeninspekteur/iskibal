package work.spell.iskibal.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/// LSP server implementation for the Iskara rule language.
public final class IskaraLanguageServer implements LanguageServer, LanguageClientAware {

    private final IskaraTextDocumentService textDocumentService = new IskaraTextDocumentService();

    @Override
    public void connect(LanguageClient client) {
        textDocumentService.connect(client);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        var capabilities = new ServerCapabilities();

        // Full document sync — we get the entire text on every change
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

        // Completion support
        var completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(List.of("."));
        capabilities.setCompletionProvider(completionOptions);

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new IskaraWorkspaceService();
    }
}
