package work.spell.iskibal.intellij.highlighting;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public final class IskaraSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

    @Override
    public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
        return new IskaraSyntaxHighlighter();
    }
}
