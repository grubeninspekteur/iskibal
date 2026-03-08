package work.spell.iskibal.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/// Entry point for the Iskara Language Server.
///
/// Communicates via JSON-RPC over stdin/stdout.
public final class Main {

    public static void main(String[] args) {
        var server = new IskaraLanguageServer();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
    }
}
