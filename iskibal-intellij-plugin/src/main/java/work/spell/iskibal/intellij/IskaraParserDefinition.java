package work.spell.iskibal.intellij;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import work.spell.iskibal.intellij.lexer.IskaraLexerAdapter;

/// Minimal parser definition for Iskara files.
///
/// Provides a flat, token-based PSI tree (no structural parsing at the PSI
/// level). Structural analysis is handled by the [work.spell.iskibal.intellij.annotator.IskaraExternalAnnotator]
/// which runs the ANTLR parser separately.
public final class IskaraParserDefinition implements ParserDefinition {

    private static final IFileElementType FILE = new IFileElementType(IskaraLanguage.INSTANCE);

    @Override
    public Lexer createLexer(Project project) {
        return new IskaraLexerAdapter();
    }

    @Override
    public PsiParser createParser(Project project) {
        // Minimal parser that wraps all tokens under the file node
        return (root, builder) -> {
            var marker = builder.mark();
            while (!builder.eof()) {
                builder.advanceLexer();
            }
            marker.done(root);
            return builder.getTreeBuilt();
        };
    }

    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public TokenSet getCommentTokens() {
        return IskaraTokenTypes.COMMENTS;
    }

    @Override
    public TokenSet getStringLiteralElements() {
        return IskaraTokenTypes.STRINGS;
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new com.intellij.psi.impl.source.tree.LeafPsiElement(node.getElementType(), node.getText());
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new IskaraFile(viewProvider);
    }
}
