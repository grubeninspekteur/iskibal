package work.spell.iskibal.intellij;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;

public final class IskaraBraceMatcher implements PairedBraceMatcher {

    private static final BracePair[] PAIRS = {
        new BracePair(IskaraTokenTypes.LBRACE, IskaraTokenTypes.RBRACE, true),
        new BracePair(IskaraTokenTypes.LBRACK, IskaraTokenTypes.RBRACK, false),
        new BracePair(IskaraTokenTypes.LPAREN, IskaraTokenTypes.RPAREN, false),
        new BracePair(IskaraTokenTypes.HASH_LPAREN, IskaraTokenTypes.RPAREN, false),
        new BracePair(IskaraTokenTypes.HASH_LBRACE, IskaraTokenTypes.RBRACE, true),
        new BracePair(IskaraTokenTypes.HASH_LBRACK, IskaraTokenTypes.RBRACK, false),
    };

    @Override
    public BracePair[] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(IElementType lbraceType, IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
