package work.spell.iskibal.intellij.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import work.spell.iskibal.intellij.IskaraTokenTypes;
import work.spell.iskibal.parser.IskaraLexer;

import java.util.ArrayList;
import java.util.List;

/// Adapts the ANTLR [IskaraLexer] for use as an IntelliJ [com.intellij.lexer.Lexer].
///
/// Overrides ANTLR's `skip()` so that whitespace and comment tokens are preserved
/// in the token stream, which IntelliJ requires for syntax highlighting.
/// Any character ranges that ANTLR's error recovery skips over are emitted as
/// `BAD_CHARACTER` tokens so that every character in the input is covered.
public final class IskaraLexerAdapter extends LexerBase {

    private record LexToken(IElementType type, int start, int end) {
    }

    private CharSequence buffer;
    private int startOffset;
    private int bufferEnd;
    private List<LexToken> tokens;
    private int tokenIndex;

    @Override
    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.startOffset = startOffset;
        this.bufferEnd = endOffset;

        var input = CharStreams.fromString(buffer.subSequence(startOffset, endOffset).toString());
        var lexer = new IskaraLexer(input) {
            @Override
            public void skip() {
                // Keep all tokens for IDE highlighting instead of discarding them
            }
        };
        lexer.removeErrorListeners();

        var antlrTokens = new ArrayList<Token>();
        Token token;
        while ((token = lexer.nextToken()).getType() != Token.EOF) {
            antlrTokens.add(token);
        }

        // Build token list, filling gaps (from error recovery) with BAD_CHARACTER
        int inputLength = endOffset - startOffset;
        tokens = new ArrayList<>(antlrTokens.size() + 4);
        int pos = 0;
        for (var t : antlrTokens) {
            int tStart = t.getStartIndex();
            if (tStart > pos) {
                tokens.add(new LexToken(IskaraTokenTypes.BAD_CHARACTER, pos + startOffset, tStart + startOffset));
            }
            int tEnd = t.getStopIndex() + 1;
            tokens.add(new LexToken(IskaraTokenTypes.map(t.getType()), tStart + startOffset, tEnd + startOffset));
            pos = tEnd;
        }
        if (pos < inputLength) {
            tokens.add(new LexToken(IskaraTokenTypes.BAD_CHARACTER, pos + startOffset, endOffset));
        }

        this.tokenIndex = 0;
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public IElementType getTokenType() {
        if (tokenIndex >= tokens.size()) {
            return null;
        }
        return tokens.get(tokenIndex).type();
    }

    @Override
    public int getTokenStart() {
        return tokens.get(tokenIndex).start();
    }

    @Override
    public int getTokenEnd() {
        return tokens.get(tokenIndex).end();
    }

    @Override
    public void advance() {
        tokenIndex++;
    }

    @Override
    public CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }
}
