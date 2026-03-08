package work.spell.iskibal.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

/// Minimal workspace service implementation.
public final class IskaraWorkspaceService implements WorkspaceService {

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // No configuration support yet
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // No file watching support yet
    }
}
