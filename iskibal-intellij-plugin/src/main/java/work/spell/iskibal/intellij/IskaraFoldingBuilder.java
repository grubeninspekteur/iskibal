package work.spell.iskibal.intellij;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

/// Token-stream-based code folding for Iskara files.
///
/// Folds `{...}` blocks, `[...]` blocks, `/* ... */` comments,
/// and `rule ... end` constructs.
public final class IskaraFoldingBuilder extends FoldingBuilderEx {

    @Override
    public FoldingDescriptor[] buildFoldRegions(PsiElement root, Document document, boolean quick) {
        var descriptors = new ArrayList<FoldingDescriptor>();
        collectFoldRegions(root, descriptors);
        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private void collectFoldRegions(PsiElement root, List<FoldingDescriptor> descriptors) {
        var braceStack = new ArrayDeque<PsiElement>();
        var bracketStack = new ArrayDeque<PsiElement>();
        var ruleStartStack = new ArrayDeque<PsiElement>();
        String ruleName = null;

        for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            var type = child.getNode().getElementType();

            if (type == IskaraTokenTypes.LBRACE || type == IskaraTokenTypes.HASH_LBRACE) {
                braceStack.push(child);
            } else if (type == IskaraTokenTypes.RBRACE && !braceStack.isEmpty()) {
                addFoldRegion(braceStack.pop(), child, "{...}", descriptors);
            } else if (type == IskaraTokenTypes.LBRACK || type == IskaraTokenTypes.HASH_LBRACK) {
                bracketStack.push(child);
            } else if (type == IskaraTokenTypes.RBRACK && !bracketStack.isEmpty()) {
                addFoldRegion(bracketStack.pop(), child, "[...]", descriptors);
            } else if (type == IskaraTokenTypes.BLOCK_COMMENT) {
                var range = child.getTextRange();
                if (range.getLength() > 4) { // more than just /**/
                    descriptors.add(new FoldingDescriptor(child.getNode(), range, null, "/*...*/"));
                }
            } else if (type == IskaraTokenTypes.KEYWORD && isRuleKeyword(child.getText())) {
                ruleStartStack.push(child);
                ruleName = getRuleName(child);
            } else if (type == IskaraTokenTypes.KEYWORD && "end".equals(child.getText()) && !ruleStartStack.isEmpty()) {
                addFoldRegion(ruleStartStack.pop(), child, "rule " + ruleName, descriptors);
                ruleName = null;
            }
        }
    }

    private static String getRuleName(PsiElement ruleKeyword) {
        var sibling = skipWhitespace(ruleKeyword);
        if (sibling == null) return "...";

        var name = new StringBuilder();
        var type = sibling.getNode().getElementType();
        if (type == IskaraTokenTypes.IDENTIFIER || type == IskaraTokenTypes.QUOTED_IDENTIFIER) {
            name.append(sibling.getText());
            sibling = skipWhitespace(sibling);
        }
        if (sibling != null && sibling.getNode().getElementType() == IskaraTokenTypes.STRING) {
            if (!name.isEmpty()) name.append(' ');
            name.append(sibling.getText());
        }
        return name.isEmpty() ? "..." : name.toString();
    }

    private static PsiElement skipWhitespace(PsiElement element) {
        var sibling = element.getNextSibling();
        while (sibling != null) {
            var type = sibling.getNode().getElementType();
            if (type != IskaraTokenTypes.WHITE_SPACE && type != IskaraTokenTypes.NEWLINE) {
                return sibling;
            }
            sibling = sibling.getNextSibling();
        }
        return null;
    }

    private boolean isRuleKeyword(String text) {
        return "rule".equals(text) || "template".equals(text) || "decision".equals(text);
    }

    private void addFoldRegion(PsiElement start, PsiElement end,
                               String placeholder, List<FoldingDescriptor> descriptors) {
        var range = new TextRange(start.getTextRange().getStartOffset(), end.getTextRange().getEndOffset());
        if (range.getLength() > 1) {
            descriptors.add(new FoldingDescriptor(start.getNode(), range, null, placeholder));
        }
    }

    @Override
    public String getPlaceholderText(ASTNode node) {
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(ASTNode node) {
        return false;
    }
}
